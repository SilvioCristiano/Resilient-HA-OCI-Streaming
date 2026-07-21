package br.com.demo.ocistreaming.producer;

import br.com.demo.ocistreaming.domain.OrderEvent;
import br.com.demo.ocistreaming.ha.ActiveStreamingTargetResolver;
import br.com.demo.ocistreaming.ha.StreamingFailoverCoordinator;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;

@Component
public class FailoverAwareOrderProducer {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private final ActiveStreamingTargetResolver activeStreamingTargetResolver;
    private final StreamingFailoverCoordinator streamingFailoverCoordinator;

    public FailoverAwareOrderProducer(
            KafkaTemplate<String, OrderEvent> kafkaTemplate,
            ActiveStreamingTargetResolver activeStreamingTargetResolver,
            StreamingFailoverCoordinator streamingFailoverCoordinator) {
        this.kafkaTemplate = kafkaTemplate;
        this.activeStreamingTargetResolver = activeStreamingTargetResolver;
        this.streamingFailoverCoordinator = streamingFailoverCoordinator;
    }

    public SendResult<String, OrderEvent> send(OrderEvent event, String partitionKey) throws Exception {
        return streamingFailoverCoordinator.executeWithProducerFailover(
                () -> sendOnce(event, partitionKey),
                event.getEventId());
    }

    private SendResult<String, OrderEvent> sendOnce(OrderEvent event, String partitionKey) throws Exception {
        String topic = activeStreamingTargetResolver.currentOrdersTopic();
        ListenableFuture<SendResult<String, OrderEvent>> future =
                kafkaTemplate.send(topic, partitionKey, event);
        return future.get(30, TimeUnit.SECONDS);
    }
}
