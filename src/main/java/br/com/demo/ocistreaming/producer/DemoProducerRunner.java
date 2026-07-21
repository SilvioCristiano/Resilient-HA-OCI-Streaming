package br.com.demo.ocistreaming.producer;

import br.com.demo.ocistreaming.config.StreamingDemoProperties;
import br.com.demo.ocistreaming.domain.OrderEvent;
import br.com.demo.ocistreaming.ha.ActiveStreamingTargetResolver;
import java.util.Scanner;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "demo.producer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoProducerRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoProducerRunner.class);

    private final FailoverAwareOrderProducer failoverAwareOrderProducer;
    private final StreamingDemoProperties demoProperties;
    private final DemoOrderEventFactory eventFactory;
    private final ActiveStreamingTargetResolver activeStreamingTargetResolver;

    public DemoProducerRunner(
            FailoverAwareOrderProducer failoverAwareOrderProducer,
            StreamingDemoProperties demoProperties,
            DemoOrderEventFactory eventFactory,
            ActiveStreamingTargetResolver activeStreamingTargetResolver) {
        this.failoverAwareOrderProducer = failoverAwareOrderProducer;
        this.demoProperties = demoProperties;
        this.eventFactory = eventFactory;
        this.activeStreamingTargetResolver = activeStreamingTargetResolver;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        StreamingDemoProperties.Producer producerProperties = demoProperties.getProducer();
        int messageCount = resolveMessageCount(producerProperties);

        if (messageCount <= 0) {
            log.info("Producer habilitado, mas nenhuma mensagem foi solicitada.");
            return;
        }

        log.info("Iniciando producer: topic={}, quantidade={}, startSequence={}, orderPoolSize={}",
                activeStreamingTargetResolver.currentOrdersTopic(),
                messageCount,
                producerProperties.getStartSequence(),
                producerProperties.getOrderPoolSize());

        for (int index = 0; index < messageCount; index++) {
            int sequence = producerProperties.getStartSequence() + index;
            OrderEvent event = eventFactory.create(sequence, producerProperties);
            String partitionKey = event.partitionKey();

            SendResult<String, OrderEvent> result = failoverAwareOrderProducer.send(event, partitionKey);
            RecordMetadata metadata = result.getRecordMetadata();

            log.info("Produzido eventId={}, key={}, status={}, partition={}, offset={}",
                    event.getEventId(),
                    partitionKey,
                    event.getStatus(),
                    metadata.partition(),
                    metadata.offset());

            if (producerProperties.getDelayMs() > 0) {
                Thread.sleep(producerProperties.getDelayMs());
            }
        }

        log.info("Producer finalizado. Consumer continua processando enquanto a aplicacao estiver ativa.");
    }

    private int resolveMessageCount(StreamingDemoProperties.Producer properties) {
        if (!properties.isInteractive()) {
            return properties.getMessageCount();
        }

        System.out.print("Quantidade de mensagens para produzir [" + properties.getMessageCount() + "]: ");
        Scanner scanner = new Scanner(System.in);
        String line = scanner.nextLine();
        if (line == null || line.trim().isEmpty()) {
            return properties.getMessageCount();
        }
        return Integer.parseInt(line.trim());
    }
}
