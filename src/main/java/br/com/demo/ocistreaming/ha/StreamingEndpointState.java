package br.com.demo.ocistreaming.ha;

import java.time.Instant;

public class StreamingEndpointState {

    public enum Role {
        PRIMARY,
        SECONDARY
    }

    private final Role role;
    private final String region;
    private final String ordersTopic;
    private final String ordersDlqTopic;
    private final String streamOcid;
    private final String dlqStreamOcid;
    private final String messagesEndpoint;
    private final String kafkaBootstrapServers;
    private final Instant updatedAt;

    public StreamingEndpointState(
            Role role,
            String region,
            String ordersTopic,
            String ordersDlqTopic,
            String streamOcid,
            String dlqStreamOcid,
            String messagesEndpoint,
            String kafkaBootstrapServers,
            Instant updatedAt) {
        this.role = role;
        this.region = region;
        this.ordersTopic = ordersTopic;
        this.ordersDlqTopic = ordersDlqTopic;
        this.streamOcid = streamOcid;
        this.dlqStreamOcid = dlqStreamOcid;
        this.messagesEndpoint = messagesEndpoint;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
        this.updatedAt = updatedAt;
    }

    public boolean isSecondary() {
        return Role.SECONDARY.equals(role);
    }

    public Role getRole() {
        return role;
    }

    public String getRegion() {
        return region;
    }

    public String getOrdersTopic() {
        return ordersTopic;
    }

    public String getOrdersDlqTopic() {
        return ordersDlqTopic;
    }

    public String getStreamOcid() {
        return streamOcid;
    }

    public String getDlqStreamOcid() {
        return dlqStreamOcid;
    }

    public String getMessagesEndpoint() {
        return messagesEndpoint;
    }

    public String getKafkaBootstrapServers() {
        return kafkaBootstrapServers;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "StreamingEndpointState{" +
                "role=" + role +
                ", region='" + region + '\'' +
                ", ordersTopic='" + ordersTopic + '\'' +
                ", ordersDlqTopic='" + ordersDlqTopic + '\'' +
                ", streamOcid='" + streamOcid + '\'' +
                ", dlqStreamOcid='" + dlqStreamOcid + '\'' +
                ", messagesEndpoint='" + messagesEndpoint + '\'' +
                ", kafkaBootstrapServers='" + kafkaBootstrapServers + '\'' +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
