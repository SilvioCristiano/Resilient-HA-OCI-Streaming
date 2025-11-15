package com.resilient.oci;

import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.streaming.StreamClient;
import com.oracle.bmc.streaming.StreamAdminClient;
import com.oracle.bmc.streaming.model.*;
import com.oracle.bmc.streaming.requests.*;
import com.oracle.bmc.streaming.responses.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class StreamManager {

    private final ConfigFileAuthenticationDetailsProvider provider;

    private StreamClient primaryClient;
    private StreamClient secondaryClient;

    private String primaryStreamId;
    private String primaryEndpoint;

    private String secondaryStreamId;
    private String secondaryEndpoint;

    private static final int MAX_RETRIES = 4;
    private static final int BASE_DELAY_MS = 500;
    private static final String COMPARTMENT_ID = "ocid1.compartment.oc1..aaaaaaaayyfw5s76wsgg2d6td4ywm3l5xwkuckkh6cmahmjq7mvnltyazfyq"; // ajusta
    private static final String SECONDARY_NAME = "OCI-SECONDARY-STREAM";
    private static final String TARGET_REGION = "sa-vinhedo-1";
   

    public StreamManager(ConfigFileAuthenticationDetailsProvider provider,
                         String primaryStreamId,
                         String primaryEndpoint) {
        this.provider = provider;
        this.primaryStreamId = primaryStreamId;
        this.primaryEndpoint = primaryEndpoint;

        this.primaryClient = StreamClient.builder()
                .endpoint(primaryEndpoint)
                .build(provider);

        this.secondaryEndpoint = StreamUtils.loadSecondaryStreamEndpoint();
        this.secondaryStreamId = StreamUtils.loadSecondaryStreamOcid();

        if (secondaryEndpoint != null && !secondaryEndpoint.isBlank()) {
            this.secondaryClient = StreamClient.builder()
                    .endpoint(secondaryEndpoint)
                    .build(provider);
        }
    }

    public void sendWithHA(String message) throws InterruptedException {
        boolean ok = trySendWithRetries(primaryClient, primaryStreamId, message);
        if (ok) return;

        System.err.println("Primary failed after retries -> starting failover...");

        if (secondaryEndpoint == null || secondaryEndpoint.isBlank() || secondaryStreamId == null || secondaryStreamId.isBlank()) {
            System.out.println("No secondary configured. Creating secondary stream...");
            createSecondaryStream();
        } else {
            if (secondaryClient == null) {
                secondaryClient = StreamClient.builder()
                        .endpoint(secondaryEndpoint)
                        .build(provider);
            }
        }

        boolean okSecondary = trySendWithRetries(secondaryClient, secondaryStreamId, message);
        if (!okSecondary) {
            throw new RuntimeException("Failed to send to secondary stream after retries");
        } else {
            System.out.println("Message sent to secondary stream.");
        }
    }

    private boolean trySendWithRetries(StreamClient client, String streamId, String message) throws InterruptedException {
        if (client == null || streamId == null || streamId.isBlank()) return false;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                putMessage(client, streamId, message);
                return true;
            } catch (Exception ex) {
                if (isRetryable(ex) && attempt < MAX_RETRIES) {
                    int delay = (int) (BASE_DELAY_MS * Math.pow(2, attempt));
                    System.err.printf("Attempt %d failed: %s. Retrying in %d ms%n", attempt, ex.getMessage(), delay);
                    Thread.sleep(delay);
                } else {
                    System.err.printf("Attempt %d final/irrecoverable: %s%n", attempt, ex.getMessage());
                    break;
                }
            }
        }
        return false;
    }

    private void putMessage(StreamClient client, String streamId, String message) {
        PutMessagesDetailsEntry entry = PutMessagesDetailsEntry.builder()
                .key("k".getBytes())
                .value(message.getBytes())
                .build();
        PutMessagesDetails details = PutMessagesDetails.builder().messages(List.of(entry)).build();
        PutMessagesRequest req = PutMessagesRequest.builder().streamId(streamId).putMessagesDetails(details).build();
        client.putMessages(req);
    }

    private void createSecondaryStream() {
    	
        try (StreamAdminClient admin = StreamAdminClient.builder().region(TARGET_REGION)
        		.build(provider)) {

            CreateStreamDetails details = CreateStreamDetails.builder()
                    .name(SECONDARY_NAME)
                    .compartmentId(COMPARTMENT_ID)
                    .partitions(1)
                    .build();

            CreateStreamRequest req = CreateStreamRequest.builder().createStreamDetails(details).build();
            CreateStreamResponse resp = admin.createStream(req);

            String newOcid = null;
            String newEndpoint = null;

            if (resp.getStream() != null) {
                newOcid = resp.getStream().getId();
                newEndpoint = resp.getStream().getMessagesEndpoint();
            }

            if (newEndpoint == null || newOcid == null) {
                TimeUnit.SECONDS.sleep(2);
                ListStreamsRequest listReq = ListStreamsRequest.builder()
                        .compartmentId(COMPARTMENT_ID)
                        .name(SECONDARY_NAME)
                        .build();
                ListStreamsResponse listResp = admin.listStreams(listReq);
                if (!listResp.getItems().isEmpty()) {
                    StreamSummary s = listResp.getItems().get(0);
                    newOcid = s.getId();
                    newEndpoint = s.getMessagesEndpoint();
                }
            }

            if (newOcid == null || newEndpoint == null) {
                throw new RuntimeException("Could not resolve new stream ID / endpoint after creation");
            }

            this.secondaryStreamId = newOcid;
            this.secondaryEndpoint = newEndpoint;

            this.secondaryClient = StreamClient.builder()
                    .endpoint(this.secondaryEndpoint)
                    .build(provider);

            StreamUtils.saveSecondaryStream(this.secondaryStreamId, this.secondaryEndpoint);
            StreamUtils.exportEnv("SECONDARY_STREAM_ENDPOINT", this.secondaryEndpoint);

            System.out.println("Secondary stream created: OCID=" + newOcid + " endpoint=" + newEndpoint);

        } catch (Exception ex) {
            throw new RuntimeException("Failed to create secondary stream: " + ex.getMessage(), ex);
        }
    }

    private boolean isRetryable(Exception ex) {
        if (ex instanceof BmcException) {
            int status = ((BmcException) ex).getStatusCode();
            return status >= 500 || status == 429;
        } else {
            String m = ex.getMessage();
            return m != null && (m.contains("500") || m.contains("429"));
        }
    }
}
