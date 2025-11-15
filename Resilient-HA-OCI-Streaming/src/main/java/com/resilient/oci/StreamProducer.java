package com.resilient.oci;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;

public class StreamProducer {

    public static void main(String[] args) throws Exception {
        final String configPath = "C:\\Users\\Silvio\\.oci\\config";
        final String profile = "DEFAULT";

        var cfg = ConfigFileReader.parse(configPath, profile);
        var provider = new ConfigFileAuthenticationDetailsProvider(cfg);

        final String PRIMARY_STREAM_OCID = "ocid1.stream.oc1.sa-saopaulo-1.amaaaaaa7acctnqaexpq5jownt6ig47d5sjgn3fdoanjrvfeixauujk3wmaq"; // ajusta
        final String PRIMARY_ENDPOINT = "https://cell-1.streaming.sa-saopaulo-1.oci.oraclecloud.com"; // ajusta

        StreamManager manager = new StreamManager(provider, PRIMARY_STREAM_OCID, PRIMARY_ENDPOINT);

        for (int i = 1; i <= 2000; i++) {
            String msg = "Mensagem-" + i;
            try {
                manager.sendWithHA(msg);
            } catch (Exception ex) {
                System.err.println("Failed to deliver message after HA attempts: " + ex.getMessage());
            }
            Thread.sleep(200);
        }
    }
}
