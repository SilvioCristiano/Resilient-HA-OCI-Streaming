package br.com.demo.ocistreaming.producer;

import br.com.demo.ocistreaming.config.StreamingDemoProperties;
import br.com.demo.ocistreaming.domain.OrderEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DemoOrderEventFactory {

    public OrderEvent create(int sequence, StreamingDemoProperties.Producer properties) {
        int orderPoolSize = Math.max(1, properties.getOrderPoolSize());
        String orderId = String.format("ORDER-%03d", ((sequence - 1) % orderPoolSize) + 1);
        String customerId = String.format("CUSTOMER-%02d", ((sequence - 1) % 10) + 1);
        BigDecimal amount = BigDecimal.valueOf(1000 + sequence).movePointLeft(2);
        String status = resolveStatus(sequence, properties);
        String eventId = resolveEventId(sequence, properties);
        return new OrderEvent(eventId, orderId, customerId, amount, sequence, status, Instant.now());
    }

    private String resolveStatus(int sequence, StreamingDemoProperties.Producer properties) {
        if (properties.getPoisonEvery() > 0 && sequence % properties.getPoisonEvery() == 0) {
            return OrderEvent.STATUS_FAIL_PERMANENT;
        }
        if (properties.getTransientEvery() > 0 && sequence % properties.getTransientEvery() == 0) {
            return OrderEvent.STATUS_FAIL_TEMPORARY;
        }
        return OrderEvent.STATUS_CREATED;
    }

    private String resolveEventId(int sequence, StreamingDemoProperties.Producer properties) {
        if (properties.isUseDeterministicEventIds()) {
            return String.format("evt-%06d", sequence);
        }
        return UUID.randomUUID().toString();
    }
}
