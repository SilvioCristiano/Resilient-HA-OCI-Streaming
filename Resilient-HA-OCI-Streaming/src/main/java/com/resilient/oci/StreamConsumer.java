package com.resilient.oci;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.streaming.StreamClient;
import com.oracle.bmc.streaming.model.CreateCursorDetails;
import com.oracle.bmc.streaming.requests.CreateCursorRequest;
import com.oracle.bmc.streaming.requests.GetMessagesRequest;
import com.oracle.bmc.streaming.responses.CreateCursorResponse;
import com.oracle.bmc.streaming.responses.GetMessagesResponse;

public class StreamConsumer {

    public static void main(String[] args) throws Exception {

        final String configPath = "C:\\Users\\Silvio\\.oci\\config";
        final String profile = "DEFAULT";

        var cfg = ConfigFileReader.parse(configPath, profile);
        var provider = new ConfigFileAuthenticationDetailsProvider(cfg);

        final String PRIMARY_STREAM_OCID =
                "ocid1.stream.oc1.sa-saopaulo-1.amaaaaaa7acctnqaexpq5jownt6ig47d5sjgn3fdoanjrvfeixauujk3wmaq";

        final String PRIMARY_ENDPOINT =
                "https://cell-1.streaming.sa-saopaulo-1.oci.oraclecloud.com";

        StreamClient primaryClient = StreamClient.builder()
                .endpoint(PRIMARY_ENDPOINT)
                .build(provider);

        StreamClient secondaryClient = null;

        String currentStreamId = PRIMARY_STREAM_OCID;
        StreamClient currentClient = primaryClient;

        String cursor = createCursor(currentClient, currentStreamId);

        while (true) {
            try {
                cursor = consumeOnce(currentClient, currentStreamId, cursor);
            } catch (Exception ex) {
                System.err.println("Primary consumer error: " + ex.getMessage());

                String secEndpoint = StreamUtils.loadSecondaryStreamEndpoint();
                String secOcid = StreamUtils.loadSecondaryStreamOcid();

                if (secEndpoint == null || secOcid == null) {
                    System.err.println("No secondary detected yet. Retrying primary...");
                    Thread.sleep(10000);
                    continue;
                }

                // garante endpoint sem escapes
                secEndpoint = secEndpoint.replace("\\:", ":").replace("\\/", "/");

                if (secondaryClient == null) {
                    secondaryClient = StreamClient.builder()
                            .endpoint(secEndpoint)
                            .build(provider);
                } else {
                    secondaryClient.setEndpoint(secEndpoint);
                }

                currentClient = secondaryClient;
                currentStreamId = secOcid;

                System.out.println("FAILOVER: Now consuming from secondary stream:");
                System.out.println("  OCID:     " + secOcid);
                System.out.println("  Endpoint: " + secEndpoint);

                // novo cursor para o stream secundário
                cursor = createCursor(currentClient, currentStreamId);
            }


            Thread.sleep(10000);
        }
    }

    private static String createCursor(StreamClient client, String streamId) {
        CreateCursorDetails cursorDetails = CreateCursorDetails.builder()
                .type(CreateCursorDetails.Type.TrimHorizon)
                .partition("0")
                .build();

        CreateCursorRequest req = CreateCursorRequest.builder()
                .streamId(streamId)
                .createCursorDetails(cursorDetails)
                .build();

        CreateCursorResponse resp = client.createCursor(req);

        return resp.getCursor().getValue();
    }

    private static String consumeOnce(StreamClient client, String streamId, String cursor) {

        GetMessagesRequest req = GetMessagesRequest.builder()
                .streamId(streamId)
                .cursor(cursor)
                .limit(20)
                .build();

        GetMessagesResponse resp = client.getMessages(req);

        if (resp.getItems().isEmpty()) {
            System.out.println("No messages available.");
        }

        resp.getItems().forEach(
                m -> System.out.println("Consumed: " + new String(m.getValue()))
        );

        // O novo cursor vem no header da resposta
        return resp.getOpcNextCursor();
    }
}
