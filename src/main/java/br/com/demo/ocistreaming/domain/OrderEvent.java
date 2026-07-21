package br.com.demo.ocistreaming.domain;

import java.math.BigDecimal;
import java.time.Instant;

public class OrderEvent {

    public static final String STATUS_CREATED = "CREATED";
    public static final String STATUS_FAIL_TEMPORARY = "FAIL_TEMPORARY";
    public static final String STATUS_FAIL_PERMANENT = "FAIL_PERMANENT";

    private String eventId;
    private String orderId;
    private String customerId;
    private BigDecimal amount;
    private int sequence;
    private String status;
    private Instant createdAt;

    public OrderEvent() {
    }

    public OrderEvent(
            String eventId,
            String orderId,
            String customerId,
            BigDecimal amount,
            int sequence,
            String status,
            Instant createdAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.sequence = sequence;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String partitionKey() {
        return orderId;
    }

    public boolean isTemporaryFailureEvent() {
        return STATUS_FAIL_TEMPORARY.equals(status);
    }

    public boolean isPermanentFailureEvent() {
        return STATUS_FAIL_PERMANENT.equals(status);
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "OrderEvent{" +
                "eventId='" + eventId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", customerId='" + customerId + '\'' +
                ", amount=" + amount +
                ", sequence=" + sequence +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
