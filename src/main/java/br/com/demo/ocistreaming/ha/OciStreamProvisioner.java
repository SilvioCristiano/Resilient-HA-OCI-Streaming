package br.com.demo.ocistreaming.ha;

import br.com.demo.ocistreaming.config.StreamingDemoProperties;
import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.streaming.StreamAdminClient;
import com.oracle.bmc.streaming.model.CreateStreamDetails;
import com.oracle.bmc.streaming.model.StreamSummary;
import com.oracle.bmc.streaming.requests.CreateStreamRequest;
import com.oracle.bmc.streaming.requests.ListStreamsRequest;
import com.oracle.bmc.streaming.responses.CreateStreamResponse;
import com.oracle.bmc.streaming.responses.ListStreamsResponse;
import java.net.URI;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OciStreamProvisioner {

    private static final Logger log = LoggerFactory.getLogger(OciStreamProvisioner.class);

    private final StreamingDemoProperties demoProperties;
    private final ActiveStreamingTargetResolver activeStreamingTargetResolver;

    public OciStreamProvisioner(
            StreamingDemoProperties demoProperties,
            ActiveStreamingTargetResolver activeStreamingTargetResolver) {
        this.demoProperties = demoProperties;
        this.activeStreamingTargetResolver = activeStreamingTargetResolver;
    }

    public StreamingEndpointState provisionSecondary() {
        StreamingDemoProperties.Failover failover = demoProperties.getFailover();
        validateFailoverConfiguration(failover);

        try {
            ConfigFileReader.ConfigFile configFile =
                    ConfigFileReader.parse(failover.getOciConfigPath(), failover.getOciProfile());
            ConfigFileAuthenticationDetailsProvider provider =
                    new ConfigFileAuthenticationDetailsProvider(configFile);

            try (StreamAdminClient adminClient = StreamAdminClient.builder()
                    .region(failover.getTargetRegion())
                    .build(provider)) {

                String ordersName = activeStreamingTargetResolver.secondaryOrdersTopicName();
                String dlqName = activeStreamingTargetResolver.secondaryOrdersDlqTopicName();
                int partitions = failover.getPartitions() > 0
                        ? failover.getPartitions()
                        : demoProperties.getTopics().getPartitions();

                StreamSummary ordersStream = findOrCreateStream(adminClient, ordersName, partitions);
                StreamSummary dlqStream = null;
                if (failover.isCreateDlq()) {
                    dlqStream = findOrCreateStream(adminClient, dlqName, partitions);
                }

                String endpoint = ordersStream.getMessagesEndpoint();
                String kafkaBootstrapServers = failover.getSecondaryBootstrapServers();
                if (isBlank(kafkaBootstrapServers)) {
                    kafkaBootstrapServers = toKafkaBootstrapServers(endpoint, failover.getKafkaBootstrapPort());
                }

                return new StreamingEndpointState(
                        StreamingEndpointState.Role.SECONDARY,
                        failover.getTargetRegion(),
                        ordersName,
                        dlqName,
                        ordersStream.getId(),
                        dlqStream == null ? "" : dlqStream.getId(),
                        endpoint,
                        kafkaBootstrapServers,
                        Instant.now());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Falha ao criar ou resolver stream secundario OCI: "
                    + exception.getMessage(), exception);
        }
    }

    private StreamSummary findOrCreateStream(
            StreamAdminClient adminClient,
            String streamName,
            int partitions) throws InterruptedException {

        StreamSummary existing = findStream(adminClient, streamName);
        if (existing != null && !isBlank(existing.getMessagesEndpoint())) {
            log.info("Stream secundario ja existe: name={}, ocid={}, endpoint={}",
                    streamName,
                    existing.getId(),
                    existing.getMessagesEndpoint());
            return existing;
        }

        if (!demoProperties.getFailover().isCreateStreams()) {
            throw new IllegalStateException("Stream secundario nao encontrado e demo.failover.create-streams=false: "
                    + streamName);
        }

        log.warn("Criando stream secundario OCI: name={}, region={}, partitions={}",
                streamName,
                demoProperties.getFailover().getTargetRegion(),
                partitions);

        CreateStreamDetails details = CreateStreamDetails.builder()
                .compartmentId(demoProperties.getFailover().getCompartmentId())
                .name(streamName)
                .partitions(partitions)
                .build();

        CreateStreamResponse response = adminClient.createStream(
                CreateStreamRequest.builder()
                        .createStreamDetails(details)
                        .build());

        if (response.getStream() != null && !isBlank(response.getStream().getMessagesEndpoint())) {
            return toSummary(response.getStream().getId(), streamName, response.getStream().getMessagesEndpoint());
        }

        return waitUntilStreamHasEndpoint(adminClient, streamName);
    }

    private StreamSummary waitUntilStreamHasEndpoint(
            StreamAdminClient adminClient,
            String streamName) throws InterruptedException {

        StreamingDemoProperties.Failover failover = demoProperties.getFailover();
        long deadline = System.currentTimeMillis() + (failover.getAdminMaxWaitSeconds() * 1000L);
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(Math.max(1, failover.getAdminPollIntervalSeconds()) * 1000L);
            StreamSummary stream = findStream(adminClient, streamName);
            if (stream != null && !isBlank(stream.getMessagesEndpoint())) {
                log.info("Stream secundario pronto: name={}, ocid={}, endpoint={}",
                        streamName,
                        stream.getId(),
                        stream.getMessagesEndpoint());
                return stream;
            }
        }

        throw new IllegalStateException("Timeout aguardando endpoint do stream secundario: " + streamName);
    }

    private StreamSummary findStream(StreamAdminClient adminClient, String streamName) {
        ListStreamsResponse response = adminClient.listStreams(
                ListStreamsRequest.builder()
                        .compartmentId(demoProperties.getFailover().getCompartmentId())
                        .name(streamName)
                        .build());

        for (StreamSummary stream : response.getItems()) {
            if (streamName.equals(stream.getName())) {
                return stream;
            }
        }
        return null;
    }

    private StreamSummary toSummary(String streamId, String name, String endpoint) {
        return StreamSummary.builder()
                .id(streamId)
                .name(name)
                .messagesEndpoint(endpoint)
                .build();
    }

    private String toKafkaBootstrapServers(String messagesEndpoint, int port) {
        if (isBlank(messagesEndpoint)) {
            throw new IllegalStateException("Endpoint OCI vazio; nao e possivel derivar bootstrap Kafka");
        }

        try {
            URI uri = URI.create(messagesEndpoint);
            String host = uri.getHost();
            if (isBlank(host)) {
                host = messagesEndpoint
                        .replace("https://", "")
                        .replace("http://", "");
                int slashIndex = host.indexOf('/');
                if (slashIndex >= 0) {
                    host = host.substring(0, slashIndex);
                }
            }
            return host + ":" + port;
        } catch (Exception exception) {
            String host = messagesEndpoint
                    .replace("https://", "")
                    .replace("http://", "");
            int slashIndex = host.indexOf('/');
            if (slashIndex >= 0) {
                host = host.substring(0, slashIndex);
            }
            return host + ":" + port;
        }
    }

    private void validateFailoverConfiguration(StreamingDemoProperties.Failover failover) {
        if (isBlank(failover.getCompartmentId())) {
            throw new IllegalStateException("Configure demo.failover.compartment-id para criar o stream secundario.");
        }
        if (isBlank(failover.getOciConfigPath())) {
            throw new IllegalStateException("Configure demo.failover.oci-config-path.");
        }
        if (isBlank(failover.getOciProfile())) {
            throw new IllegalStateException("Configure demo.failover.oci-profile.");
        }
        if (isBlank(failover.getTargetRegion())) {
            throw new IllegalStateException("Configure demo.failover.target-region.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
