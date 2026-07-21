package br.com.demo.ocistreaming;

import br.com.demo.ocistreaming.config.StreamingDemoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(StreamingDemoProperties.class)
public class OciStreamingDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(OciStreamingDemoApplication.class, args);
    }
}
