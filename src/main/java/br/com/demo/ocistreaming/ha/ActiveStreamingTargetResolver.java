package br.com.demo.ocistreaming.ha;

import br.com.demo.ocistreaming.config.StreamingDemoProperties;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.stereotype.Component;

@Component
public class ActiveStreamingTargetResolver {

    private static final Logger log = LoggerFactory.getLogger(ActiveStreamingTargetResolver.class);

    private final StreamingDemoProperties demoProperties;
    private final KafkaProperties kafkaProperties;
    private final StreamingFailoverStateStore stateStore;
    private volatile StreamingEndpointState activeState;

    public ActiveStreamingTargetResolver(
            StreamingDemoProperties demoProperties,
            KafkaProperties kafkaProperties,
            StreamingFailoverStateStore stateStore) {
        this.demoProperties = demoProperties;
        this.kafkaProperties = kafkaProperties;
        this.stateStore = stateStore;
        this.activeState = resolveInitialState();
    }

    public StreamingEndpointState current() {
        return activeState;
    }

    public synchronized void markSecondaryActive(StreamingEndpointState state) {
        this.activeState = state;
        log.warn("Alvo ativo do OCI Streaming alterado para role={}, region={}, bootstrap={}, topic={}",
                state.getRole(),
                state.getRegion(),
                state.getKafkaBootstrapServers(),
                state.getOrdersTopic());
    }

    public String currentOrdersTopic() {
        return current().getOrdersTopic();
    }

    public String currentOrdersDlqTopic() {
        return current().getOrdersDlqTopic();
    }

    public String ordersTopicPattern() {
        String primary = demoProperties.getTopics().getOrders();
        String secondary = secondaryOrdersTopicName();
        if (primary.equals(secondary)) {
            return Pattern.quote(primary);
        }
        return Pattern.quote(primary) + "|" + Pattern.quote(secondary);
    }

    public void applyActiveTarget(Map<String, Object> properties) {
        StreamingEndpointState state = current();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, state.getKafkaBootstrapServers());

        if (state.isSecondary()) {
            StreamingDemoProperties.Failover failover = demoProperties.getFailover();
            putIfPresent(properties, CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, failover.getSecondarySecurityProtocol());
            putIfPresent(properties, SaslConfigs.SASL_MECHANISM, failover.getSecondarySaslMechanism());
            putIfPresent(properties, SaslConfigs.SASL_JAAS_CONFIG, failover.getSecondarySaslJaasConfig());
        }
    }

    public String secondaryOrdersTopicName() {
        String configured = demoProperties.getFailover().getSecondaryStreamName();
        return isBlank(configured) ? demoProperties.getTopics().getOrders() : configured.trim();
    }

    public String secondaryOrdersDlqTopicName() {
        String configured = demoProperties.getFailover().getSecondaryDlqStreamName();
        return isBlank(configured) ? demoProperties.getTopics().getOrdersDlq() : configured.trim();
    }

    private StreamingEndpointState resolveInitialState() {
        if (demoProperties.getFailover().isEnabled()
                && demoProperties.getFailover().isActivatePersistedSecondaryOnStartup()) {
            Optional<StreamingEndpointState> persisted = stateStore.loadSecondary();
            if (persisted.isPresent()) {
                StreamingEndpointState state = persisted.get();
                log.warn("Estado secundario encontrado em {}. Aplicacao iniciara consumindo/publicando em {}",
                        stateStore.stateFilePath(),
                        state.getKafkaBootstrapServers());
                return state;
            }
        }

        return primaryState();
    }

    private StreamingEndpointState primaryState() {
        return new StreamingEndpointState(
                StreamingEndpointState.Role.PRIMARY,
                "",
                demoProperties.getTopics().getOrders(),
                demoProperties.getTopics().getOrdersDlq(),
                "",
                "",
                "",
                String.join(",", kafkaProperties.getBootstrapServers()),
                Instant.now());
    }

    private void putIfPresent(Map<String, Object> properties, String key, String value) {
        if (!isBlank(value)) {
            properties.put(key, value.trim());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
