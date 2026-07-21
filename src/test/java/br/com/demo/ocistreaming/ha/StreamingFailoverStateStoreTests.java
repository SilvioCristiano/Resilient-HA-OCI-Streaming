package br.com.demo.ocistreaming.ha;

import br.com.demo.ocistreaming.config.StreamingDemoProperties;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingFailoverStateStoreTests {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsSecondaryTarget() {
        StreamingDemoProperties properties = new StreamingDemoProperties();
        properties.getFailover().setStateFile(tempDir.resolve("stream.properties").toString());
        StreamingFailoverStateStore store = new StreamingFailoverStateStore(properties);

        StreamingEndpointState state = new StreamingEndpointState(
                StreamingEndpointState.Role.SECONDARY,
                "sa-vinhedo-1",
                "orders-demo",
                "orders-demo.DLQ",
                "ocid1.stream.oc1.sa-vinhedo-1.example",
                "ocid1.stream.oc1.sa-vinhedo-1.dlq",
                "https://cell-1.streaming.sa-vinhedo-1.oci.oraclecloud.com",
                "cell-1.streaming.sa-vinhedo-1.oci.oraclecloud.com:9092",
                Instant.now());

        store.saveSecondary(state);

        Optional<StreamingEndpointState> loaded = store.loadSecondary();

        assertThat(loaded).isPresent();
        assertThat(loaded.get().getRole()).isEqualTo(StreamingEndpointState.Role.SECONDARY);
        assertThat(loaded.get().getKafkaBootstrapServers())
                .isEqualTo("cell-1.streaming.sa-vinhedo-1.oci.oraclecloud.com:9092");
        assertThat(loaded.get().getStreamOcid()).isEqualTo("ocid1.stream.oc1.sa-vinhedo-1.example");
    }
}
