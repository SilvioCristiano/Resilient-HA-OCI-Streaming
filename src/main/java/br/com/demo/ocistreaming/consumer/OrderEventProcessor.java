package br.com.demo.ocistreaming.consumer;

import br.com.demo.ocistreaming.config.StreamingDemoProperties;
import br.com.demo.ocistreaming.domain.OrderEvent;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(OrderEventProcessor.class);

    private final StreamingDemoProperties demoProperties;
    private final ConcurrentMap<String, Integer> transientAttempts = new ConcurrentHashMap<String, Integer>();

    public OrderEventProcessor(StreamingDemoProperties demoProperties) {
        this.demoProperties = demoProperties;
    }

    public void process(OrderEvent event) throws InterruptedException {
        if (event.isPermanentFailureEvent()) {
            throw new IllegalStateException("Falha permanente simulada para eventId=" + event.getEventId());
        }

        if (event.isTemporaryFailureEvent()) {
            int attempt = transientAttempts.merge(event.getEventId(), 1, Integer::sum);
            if (attempt <= demoProperties.getConsumer().getTransientFailuresBeforeSuccess()) {
                throw new IllegalStateException("Falha temporaria simulada para eventId=" +
                        event.getEventId() + ", tentativa=" + attempt);
            }
        }

        if (demoProperties.getConsumer().getSimulatedWorkMs() > 0) {
            Thread.sleep(demoProperties.getConsumer().getSimulatedWorkMs());
        }

        log.info("Processamento de negocio concluido eventId={}, orderId={}, sequence={}, status={}",
                event.getEventId(),
                event.getOrderId(),
                event.getSequence(),
                event.getStatus());
    }
}
