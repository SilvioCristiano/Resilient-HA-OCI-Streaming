package br.com.demo.ocistreaming.config;

import br.com.demo.ocistreaming.domain.OrderEvent;
import br.com.demo.ocistreaming.ha.ActiveStreamingTargetResolver;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.stereotype.Component;

@Component
public class KafkaClientPropertiesFactory {

    private final KafkaProperties kafkaProperties;
    private final StreamingDemoProperties demoProperties;
    private final ActiveStreamingTargetResolver activeStreamingTargetResolver;

    public KafkaClientPropertiesFactory(
            KafkaProperties kafkaProperties,
            StreamingDemoProperties demoProperties,
            ActiveStreamingTargetResolver activeStreamingTargetResolver) {
        this.kafkaProperties = kafkaProperties;
        this.demoProperties = demoProperties;
        this.activeStreamingTargetResolver = activeStreamingTargetResolver;
    }

    public Map<String, Object> producerProperties() {
        Map<String, Object> properties = kafkaProperties.buildProducerProperties();
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        properties.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        properties.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        properties.putIfAbsent(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        activeStreamingTargetResolver.applyActiveTarget(properties);
        return properties;
    }

    public Map<String, Object> consumerProperties() {
        Map<String, Object> properties = kafkaProperties.buildConsumerProperties();
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, demoProperties.getConsumer().getBatchSize());
        properties.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, CooperativeStickyAssignor.class.getName());
        properties.put(JsonDeserializer.TRUSTED_PACKAGES, "br.com.demo.ocistreaming.domain");
        properties.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderEvent.class);
        properties.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        activeStreamingTargetResolver.applyActiveTarget(properties);
        return properties;
    }

    public Map<String, Object> adminProperties() {
        Map<String, Object> properties = kafkaProperties.buildAdminProperties();
        activeStreamingTargetResolver.applyActiveTarget(properties);
        return properties;
    }
}
