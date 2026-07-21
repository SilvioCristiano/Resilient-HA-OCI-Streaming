package br.com.demo.ocistreaming.ha;

import br.com.demo.ocistreaming.config.StreamingDemoProperties;
import com.oracle.bmc.model.BmcException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.RetriableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StreamingFailoverCoordinator {

    private static final Logger log = LoggerFactory.getLogger(StreamingFailoverCoordinator.class);

    private final StreamingDemoProperties demoProperties;
    private final ActiveStreamingTargetResolver activeStreamingTargetResolver;
    private final StreamingFailoverStateStore stateStore;
    private final OciStreamProvisioner ociStreamProvisioner;
    private final KafkaClientSwitchService kafkaClientSwitchService;
    private final AtomicBoolean failoverInProgress = new AtomicBoolean(false);

    public StreamingFailoverCoordinator(
            StreamingDemoProperties demoProperties,
            ActiveStreamingTargetResolver activeStreamingTargetResolver,
            StreamingFailoverStateStore stateStore,
            OciStreamProvisioner ociStreamProvisioner,
            KafkaClientSwitchService kafkaClientSwitchService) {
        this.demoProperties = demoProperties;
        this.activeStreamingTargetResolver = activeStreamingTargetResolver;
        this.stateStore = stateStore;
        this.ociStreamProvisioner = ociStreamProvisioner;
        this.kafkaClientSwitchService = kafkaClientSwitchService;
    }

    public <T> T executeWithProducerFailover(Callable<T> operation, String description) throws Exception {
        try {
            return operation.call();
        } catch (Exception exception) {
            if (!demoProperties.getFailover().isEnabled() || !isFailoverCandidate(exception)) {
                throw exception;
            }

            StreamingEndpointState target = failover("producer:" + description, exception);
            log.warn("Reexecutando envio apos failover para {}", target.getKafkaBootstrapServers());
            return operation.call();
        }
    }

    public StreamingEndpointState failover(String trigger, Throwable cause) {
        if (!demoProperties.getFailover().isEnabled()) {
            throw new IllegalStateException("Failover desabilitado. Trigger=" + trigger, cause);
        }

        if (activeStreamingTargetResolver.current().isSecondary()) {
            log.warn("Failover solicitado por {}, mas a aplicacao ja esta no stream secundario.", trigger);
            return activeStreamingTargetResolver.current();
        }

        if (!failoverInProgress.compareAndSet(false, true)) {
            waitForConcurrentFailover();
            return activeStreamingTargetResolver.current();
        }

        try {
            log.warn("Iniciando failover OCI Streaming. trigger={}, causa={}",
                    trigger,
                    cause == null ? "n/a" : cause.getMessage());

            Optional<StreamingEndpointState> savedSecondary = stateStore.loadSecondary();
            StreamingEndpointState target = savedSecondary.orElseGet(ociStreamProvisioner::provisionSecondary);

            stateStore.saveSecondary(target);
            activeStreamingTargetResolver.markSecondaryActive(target);
            kafkaClientSwitchService.switchClientsTo(target);
            return target;
        } finally {
            failoverInProgress.set(false);
        }
    }

    public boolean isFailoverCandidate(Throwable throwable) {
        Throwable current = unwrap(throwable);

        if (current instanceof BmcException) {
            int statusCode = ((BmcException) current).getStatusCode();
            return statusCode == 429 || statusCode >= 500;
        }

        if (current instanceof TimeoutException || current instanceof RetriableException) {
            return true;
        }

        if (current instanceof KafkaException) {
            String message = current.getMessage();
            return containsFailoverSignal(message);
        }

        return containsFailoverSignal(current == null ? null : current.getMessage());
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof ExecutionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private boolean containsFailoverSignal(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("500")
                || normalized.contains("429")
                || normalized.contains("timeout")
                || normalized.contains("timed out")
                || normalized.contains("disconnect")
                || normalized.contains("connection")
                || normalized.contains("failed to update metadata")
                || normalized.contains("not present in metadata");
    }

    private void waitForConcurrentFailover() {
        while (failoverInProgress.get()) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrompido aguardando failover concorrente", exception);
            }
        }
    }
}
