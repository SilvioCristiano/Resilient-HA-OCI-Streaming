package br.com.demo.ocistreaming.config;

import br.com.demo.ocistreaming.domain.OrderEvent;
import br.com.demo.ocistreaming.ha.ActiveStreamingTargetResolver;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaDemoConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaDemoConfig.class);

    @Bean
    public DefaultKafkaProducerFactory<String, OrderEvent> producerFactory(
            KafkaClientPropertiesFactory kafkaClientPropertiesFactory) {
        return new DefaultKafkaProducerFactory<String, OrderEvent>(
                kafkaClientPropertiesFactory.producerProperties());
    }

    @Bean
    public KafkaTemplate<String, OrderEvent> kafkaTemplate(ProducerFactory<String, OrderEvent> producerFactory) {
        return new KafkaTemplate<String, OrderEvent>(producerFactory);
    }

    @Bean
    public DefaultKafkaConsumerFactory<String, OrderEvent> consumerFactory(
            KafkaClientPropertiesFactory kafkaClientPropertiesFactory) {
        return new DefaultKafkaConsumerFactory<String, OrderEvent>(
                kafkaClientPropertiesFactory.consumerProperties());
    }

    @Bean
    public DefaultErrorHandler errorHandler(
            KafkaTemplate<String, OrderEvent> kafkaTemplate,
            StreamingDemoProperties demoProperties,
            ActiveStreamingTargetResolver activeStreamingTargetResolver) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        activeStreamingTargetResolver.currentOrdersDlqTopic(),
                        record.partition()));

        FixedBackOff backOff = new FixedBackOff(
                demoProperties.getConsumer().getRetryBackoffMs(),
                demoProperties.getConsumer().getRetryAttempts());

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.setCommitRecovered(true);
        errorHandler.setRetryListeners((record, exception, deliveryAttempt) ->
                log.warn("Retry {} para topic={}, partition={}, offset={}, key={}, causa={}",
                        deliveryAttempt,
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        record.key(),
                        exception.getMessage()));
        return errorHandler;
    }

    @Bean
    public ConsumerRebalanceListener rebalanceLogger() {
        return new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(java.util.Collection<TopicPartition> partitions) {
                log.info("Rebalance cooperativo: partitions revogadas {}", partitions);
            }

            @Override
            public void onPartitionsAssigned(java.util.Collection<TopicPartition> partitions) {
                log.info("Rebalance cooperativo: partitions atribuidas {}", partitions);
            }
        };
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, OrderEvent> consumerFactory,
            DefaultErrorHandler errorHandler,
            ConsumerRebalanceListener rebalanceLogger,
            StreamingDemoProperties demoProperties) {

        ConcurrentKafkaListenerContainerFactory<String, OrderEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<String, OrderEvent>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        factory.setConcurrency(demoProperties.getConsumer().getConcurrency());
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setSyncCommits(true);
        factory.getContainerProperties().setConsumerRebalanceListener(rebalanceLogger);
        return factory;
    }

    @Bean
    @ConditionalOnProperty(prefix = "demo.topics", name = "create", havingValue = "true")
    public NewTopic ordersTopic(StreamingDemoProperties demoProperties) {
        return TopicBuilder.name(demoProperties.getTopics().getOrders())
                .partitions(demoProperties.getTopics().getPartitions())
                .replicas(demoProperties.getTopics().getReplicationFactor())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "demo.topics", name = "create", havingValue = "true")
    public NewTopic ordersDlqTopic(StreamingDemoProperties demoProperties) {
        return TopicBuilder.name(demoProperties.getTopics().getOrdersDlq())
                .partitions(demoProperties.getTopics().getPartitions())
                .replicas(demoProperties.getTopics().getReplicationFactor())
                .build();
    }
}
