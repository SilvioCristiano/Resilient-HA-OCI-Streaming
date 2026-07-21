package br.com.demo.ocistreaming;

import br.com.demo.ocistreaming.config.StreamingDemoProperties;
import br.com.demo.ocistreaming.domain.OrderEvent;
import br.com.demo.ocistreaming.producer.DemoOrderEventFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OciStreamingDemoApplicationTests {

    @Test
    void factoryUsesOrderIdAsConsistentPartitionKey() {
        StreamingDemoProperties.Producer properties = new StreamingDemoProperties.Producer();
        properties.setOrderPoolSize(2);

        DemoOrderEventFactory factory = new DemoOrderEventFactory();

        OrderEvent first = factory.create(1, properties);
        OrderEvent third = factory.create(3, properties);

        assertThat(first.partitionKey()).isEqualTo("ORDER-001");
        assertThat(third.partitionKey()).isEqualTo("ORDER-001");
    }

    @Test
    void factoryCanCreatePermanentFailureEventsForDlqDemo() {
        StreamingDemoProperties.Producer properties = new StreamingDemoProperties.Producer();
        properties.setPoisonEvery(5);

        DemoOrderEventFactory factory = new DemoOrderEventFactory();

        OrderEvent event = factory.create(5, properties);

        assertThat(event.isPermanentFailureEvent()).isTrue();
    }
}
