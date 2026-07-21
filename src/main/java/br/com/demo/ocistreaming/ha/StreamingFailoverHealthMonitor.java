package br.com.demo.ocistreaming.ha;

import br.com.demo.ocistreaming.config.KafkaClientPropertiesFactory;
import br.com.demo.ocistreaming.config.StreamingDemoProperties;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StreamingFailoverHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(StreamingFailoverHealthMonitor.class);

    private final StreamingDemoProperties demoProperties;
    private final KafkaClientPropertiesFactory kafkaClientPropertiesFactory;
    private final ActiveStreamingTargetResolver activeStreamingTargetResolver;
    private final StreamingFailoverCoordinator streamingFailoverCoordinator;

    public StreamingFailoverHealthMonitor(
            StreamingDemoProperties demoProperties,
            KafkaClientPropertiesFactory kafkaClientPropertiesFactory,
            ActiveStreamingTargetResolver activeStreamingTargetResolver,
            StreamingFailoverCoordinator streamingFailoverCoordinator) {
        this.demoProperties = demoProperties;
        this.kafkaClientPropertiesFactory = kafkaClientPropertiesFactory;
        this.activeStreamingTargetResolver = activeStreamingTargetResolver;
        this.streamingFailoverCoordinator = streamingFailoverCoordinator;
    }

    @Scheduled(fixedDelayString = "${demo.failover.health-check-interval-ms:15000}")
    public void checkActiveEndpoint() {
        if (!demoProperties.getFailover().isEnabled()
                || !demoProperties.getFailover().isHealthCheckEnabled()) {
            return;
        }

        try (AdminClient adminClient = KafkaAdminClient.create(kafkaClientPropertiesFactory.adminProperties())) {
            adminClient.describeCluster()
                    .nodes()
                    .get(demoProperties.getFailover().getHealthCheckTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            if (activeStreamingTargetResolver.current().isSecondary()) {
                log.warn("Health check falhou no stream secundario ativo: {}", exception.getMessage());
                return;
            }

            if (streamingFailoverCoordinator.isFailoverCandidate(exception)) {
                streamingFailoverCoordinator.failover("health-check", exception);
            } else {
                log.warn("Health check falhou, mas erro nao foi classificado como failover: {}",
                        exception.getMessage());
            }
        }
    }
}
