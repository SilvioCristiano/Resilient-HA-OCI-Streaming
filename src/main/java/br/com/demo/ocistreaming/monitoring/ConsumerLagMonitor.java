package br.com.demo.ocistreaming.monitoring;

import br.com.demo.ocistreaming.config.StreamingDemoProperties;
import br.com.demo.ocistreaming.config.KafkaClientPropertiesFactory;
import br.com.demo.ocistreaming.ha.ActiveStreamingTargetResolver;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ConsumerLagMonitor {

    private static final Logger log = LoggerFactory.getLogger(ConsumerLagMonitor.class);

    private final StreamingDemoProperties demoProperties;
    private final org.springframework.boot.autoconfigure.kafka.KafkaProperties kafkaProperties;
    private final KafkaClientPropertiesFactory kafkaClientPropertiesFactory;
    private final ActiveStreamingTargetResolver activeStreamingTargetResolver;
    private final AtomicLong lastTotalLag = new AtomicLong();

    public ConsumerLagMonitor(
            StreamingDemoProperties demoProperties,
            org.springframework.boot.autoconfigure.kafka.KafkaProperties kafkaProperties,
            KafkaClientPropertiesFactory kafkaClientPropertiesFactory,
            ActiveStreamingTargetResolver activeStreamingTargetResolver) {
        this.demoProperties = demoProperties;
        this.kafkaProperties = kafkaProperties;
        this.kafkaClientPropertiesFactory = kafkaClientPropertiesFactory;
        this.activeStreamingTargetResolver = activeStreamingTargetResolver;
    }

    @Scheduled(fixedDelayString = "${demo.consumer.lag-monitor-interval-ms:15000}")
    public void logLag() {
        if (!demoProperties.getConsumer().isLagMonitorEnabled() || !demoProperties.getConsumer().isEnabled()) {
            return;
        }

        String groupId = kafkaProperties.getConsumer().getGroupId();
        if (groupId == null || groupId.trim().isEmpty()) {
            log.warn("Monitoramento de lag ignorado: spring.kafka.consumer.group-id nao configurado.");
            return;
        }

        Map<String, Object> adminProperties = kafkaClientPropertiesFactory.adminProperties();
        try (AdminClient adminClient = KafkaAdminClient.create(adminProperties)) {
            Map<TopicPartition, OffsetAndMetadata> committedOffsets = adminClient
                    .listConsumerGroupOffsets(groupId)
                    .partitionsToOffsetAndMetadata()
                    .get(10, TimeUnit.SECONDS);

            Map<TopicPartition, OffsetAndMetadata> topicOffsets = filterTopicOffsets(committedOffsets);
            if (topicOffsets.isEmpty()) {
                log.info("Lag monitor: nenhum offset commitado ainda para groupId={} topic={}",
                        groupId,
                        activeStreamingTargetResolver.currentOrdersTopic());
                return;
            }

            Map<TopicPartition, OffsetSpec> latestRequests = new HashMap<TopicPartition, OffsetSpec>();
            for (TopicPartition partition : topicOffsets.keySet()) {
                latestRequests.put(partition, OffsetSpec.latest());
            }

            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latestOffsets =
                    adminClient.listOffsets(latestRequests).all().get(10, TimeUnit.SECONDS);

            long totalLag = 0;
            for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : topicOffsets.entrySet()) {
                TopicPartition partition = entry.getKey();
                long committed = entry.getValue().offset();
                long latest = latestOffsets.get(partition).offset();
                long lag = Math.max(0, latest - committed);
                totalLag += lag;
                log.info("Lag partition={} committed={} latest={} lag={}",
                        partition,
                        committed,
                        latest,
                        lag);
            }

            lastTotalLag.set(totalLag);
            log.info("Lag total groupId={} topic={} lag={}",
                    groupId,
                    activeStreamingTargetResolver.currentOrdersTopic(),
                    totalLag);
        } catch (Exception exception) {
            log.warn("Nao foi possivel calcular lag agora: {}", exception.getMessage());
        }
    }

    public long getLastTotalLag() {
        return lastTotalLag.get();
    }

    private Map<TopicPartition, OffsetAndMetadata> filterTopicOffsets(
            Map<TopicPartition, OffsetAndMetadata> committedOffsets) {

        if (committedOffsets == null || committedOffsets.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<TopicPartition, OffsetAndMetadata> result = new HashMap<TopicPartition, OffsetAndMetadata>();
        for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : committedOffsets.entrySet()) {
            if (activeStreamingTargetResolver.currentOrdersTopic().equals(entry.getKey().topic())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
}
