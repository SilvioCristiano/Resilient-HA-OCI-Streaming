package br.com.demo.ocistreaming.ha;

import br.com.demo.ocistreaming.config.KafkaClientPropertiesFactory;
import br.com.demo.ocistreaming.domain.OrderEvent;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
public class KafkaClientSwitchService {

    private static final Logger log = LoggerFactory.getLogger(KafkaClientSwitchService.class);

    private final DefaultKafkaProducerFactory<String, OrderEvent> producerFactory;
    private final DefaultKafkaConsumerFactory<String, OrderEvent> consumerFactory;
    private final KafkaClientPropertiesFactory kafkaClientPropertiesFactory;
    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    public KafkaClientSwitchService(
            DefaultKafkaProducerFactory<String, OrderEvent> producerFactory,
            DefaultKafkaConsumerFactory<String, OrderEvent> consumerFactory,
            KafkaClientPropertiesFactory kafkaClientPropertiesFactory,
            KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry) {
        this.producerFactory = producerFactory;
        this.consumerFactory = consumerFactory;
        this.kafkaClientPropertiesFactory = kafkaClientPropertiesFactory;
        this.kafkaListenerEndpointRegistry = kafkaListenerEndpointRegistry;
    }

    public synchronized void switchClientsTo(StreamingEndpointState target) {
        log.warn("Reconfigurando clients Kafka para bootstrap={}, topic={}",
                target.getKafkaBootstrapServers(),
                target.getOrdersTopic());

        Collection<MessageListenerContainer> containers =
                kafkaListenerEndpointRegistry.getListenerContainers();

        for (MessageListenerContainer container : containers) {
            if (container.isRunning()) {
                log.info("Parando listener Kafka {}", container);
                container.stop();
            }
        }

        producerFactory.updateConfigs(kafkaClientPropertiesFactory.producerProperties());
        producerFactory.reset();
        consumerFactory.updateConfigs(kafkaClientPropertiesFactory.consumerProperties());

        for (MessageListenerContainer container : containers) {
            log.info("Iniciando listener Kafka {}", container);
            container.start();
        }
    }
}
