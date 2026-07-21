package br.com.demo.ocistreaming.ha;

import br.com.demo.ocistreaming.config.StreamingDemoProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StreamingFailoverStateStore {

    private static final Logger log = LoggerFactory.getLogger(StreamingFailoverStateStore.class);

    private static final String ACTIVE_ROLE = "active.role";
    private static final String ACTIVE_REGION = "active.region";
    private static final String ORDERS_TOPIC = "active.topic.orders";
    private static final String ORDERS_DLQ_TOPIC = "active.topic.orders-dlq";
    private static final String SECONDARY_STREAM_OCID = "secondary.stream.ocid";
    private static final String SECONDARY_DLQ_STREAM_OCID = "secondary.dlq-stream.ocid";
    private static final String SECONDARY_MESSAGES_ENDPOINT = "secondary.messages.endpoint";
    private static final String SECONDARY_KAFKA_BOOTSTRAP_SERVERS = "secondary.kafka.bootstrap-servers";
    private static final String UPDATED_AT = "updated.at";

    private final StreamingDemoProperties demoProperties;

    public StreamingFailoverStateStore(StreamingDemoProperties demoProperties) {
        this.demoProperties = demoProperties;
    }

    public Optional<StreamingEndpointState> loadSecondary() {
        Path path = stateFilePath();
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        } catch (IOException exception) {
            log.warn("Nao foi possivel carregar estado de failover em {}: {}",
                    path,
                    exception.getMessage());
            return Optional.empty();
        }

        String activeRole = properties.getProperty(ACTIVE_ROLE);
        String bootstrapServers = properties.getProperty(SECONDARY_KAFKA_BOOTSTRAP_SERVERS);
        String endpoint = properties.getProperty(SECONDARY_MESSAGES_ENDPOINT);
        String streamOcid = properties.getProperty(SECONDARY_STREAM_OCID);

        if (!"SECONDARY".equalsIgnoreCase(trim(activeRole)) || isBlank(bootstrapServers) || isBlank(streamOcid)) {
            return Optional.empty();
        }

        StreamingEndpointState state = new StreamingEndpointState(
                StreamingEndpointState.Role.SECONDARY,
                trim(properties.getProperty(ACTIVE_REGION)),
                trim(properties.getProperty(ORDERS_TOPIC)),
                trim(properties.getProperty(ORDERS_DLQ_TOPIC)),
                trim(streamOcid),
                trim(properties.getProperty(SECONDARY_DLQ_STREAM_OCID)),
                trim(endpoint),
                trim(bootstrapServers),
                parseInstant(properties.getProperty(UPDATED_AT)));
        return Optional.of(state);
    }

    public synchronized void saveSecondary(StreamingEndpointState state) {
        Path path = stateFilePath();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Properties properties = new Properties();
            properties.setProperty(ACTIVE_ROLE, state.getRole().name());
            properties.setProperty(ACTIVE_REGION, emptyIfNull(state.getRegion()));
            properties.setProperty(ORDERS_TOPIC, emptyIfNull(state.getOrdersTopic()));
            properties.setProperty(ORDERS_DLQ_TOPIC, emptyIfNull(state.getOrdersDlqTopic()));
            properties.setProperty(SECONDARY_STREAM_OCID, emptyIfNull(state.getStreamOcid()));
            properties.setProperty(SECONDARY_DLQ_STREAM_OCID, emptyIfNull(state.getDlqStreamOcid()));
            properties.setProperty(SECONDARY_MESSAGES_ENDPOINT, emptyIfNull(state.getMessagesEndpoint()));
            properties.setProperty(SECONDARY_KAFKA_BOOTSTRAP_SERVERS, emptyIfNull(state.getKafkaBootstrapServers()));
            properties.setProperty(UPDATED_AT, Instant.now().toString());

            try (OutputStream outputStream = Files.newOutputStream(path)) {
                properties.store(outputStream, "Active OCI Streaming failover target");
            }

            log.info("Estado de failover salvo em {}", path);
        } catch (IOException exception) {
            throw new IllegalStateException("Nao foi possivel salvar estado de failover em " + path, exception);
        }
    }

    public Path stateFilePath() {
        String configured = demoProperties.getFailover().getStateFile();
        if (isBlank(configured)) {
            configured = "./data/stream.properties";
        }
        return Paths.get(configured).toAbsolutePath().normalize();
    }

    private Instant parseInstant(String value) {
        if (isBlank(value)) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(value.trim());
        } catch (Exception ignored) {
            return Instant.EPOCH;
        }
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
