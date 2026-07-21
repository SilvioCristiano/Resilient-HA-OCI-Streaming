package br.com.demo.ocistreaming;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "demo.producer.enabled=false",
                "demo.consumer.enabled=false",
                "demo.consumer.lag-monitor-enabled=false",
                "spring.datasource.url=jdbc:h2:mem:context-test;DB_CLOSE_DELAY=-1"
        })
class OciStreamingApplicationContextTests {

    @Test
    void contextLoadsWithoutKafkaConnectionWhenDemoWorkersAreDisabled() {
    }
}
