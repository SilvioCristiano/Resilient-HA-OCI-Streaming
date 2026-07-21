package br.com.demo.ocistreaming.consumer;

import br.com.demo.ocistreaming.domain.OrderEvent;

public interface ProcessedEventRepository {

    boolean claimForProcessing(OrderEvent event);

    void markProcessed(OrderEvent event);

    void markFailed(OrderEvent event, Exception exception);
}
