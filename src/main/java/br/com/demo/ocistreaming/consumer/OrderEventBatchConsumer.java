package br.com.demo.ocistreaming.consumer;

import br.com.demo.ocistreaming.domain.OrderEvent;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "demo.consumer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderEventBatchConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventBatchConsumer.class);

    private final ProcessedEventRepository processedEventRepository;
    private final OrderEventProcessor processor;

    public OrderEventBatchConsumer(
            ProcessedEventRepository processedEventRepository,
            OrderEventProcessor processor) {
        this.processedEventRepository = processedEventRepository;
        this.processor = processor;
    }

    @KafkaListener(
            topicPattern = "#{@activeStreamingTargetResolver.ordersTopicPattern()}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(List<ConsumerRecord<String, OrderEvent>> records, Acknowledgment acknowledgment) {
        log.info("Batch recebido com {} registros", records.size());

        for (int index = 0; index < records.size(); index++) {
            ConsumerRecord<String, OrderEvent> record = records.get(index);
            OrderEvent event = record.value();

            try {
                if (!processedEventRepository.claimForProcessing(event)) {
                    log.info("Evento duplicado ignorado eventId={}, key={}, partition={}, offset={}",
                            event.getEventId(),
                            record.key(),
                            record.partition(),
                            record.offset());
                    continue;
                }

                processor.process(event);
                processedEventRepository.markProcessed(event);
                log.info("Evento confirmado em storage local eventId={}, partition={}, offset={}",
                        event.getEventId(),
                        record.partition(),
                        record.offset());
            } catch (Exception exception) {
                processedEventRepository.markFailed(event, exception);
                log.warn("Falha no item do batch eventId={}, partition={}, offset={}, erro={}",
                        event.getEventId(),
                        record.partition(),
                        record.offset(),
                        exception.getMessage());
                throw new BatchListenerFailedException(
                        "Falha no indice " + index + " do batch",
                        exception,
                        index);
            }
        }

        acknowledgment.acknowledge();
        log.info("Commit manual executado apos sucesso do batch com {} registros", records.size());
    }
}
