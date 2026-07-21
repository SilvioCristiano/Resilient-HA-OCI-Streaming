# Spring Boot OCI Streaming Demo

Aplicacao Spring Boot de demonstracao para producer e consumer usando a API Kafka compativel com OCI Streaming.

O fluxo principal fica no console: ao iniciar a aplicacao, o producer pergunta a quantidade de mensagens e publica eventos de pedido. O consumer processa em lote, confirma offset somente depois de sucesso, controla idempotencia em uma tabela H2 e envia falhas permanentes para uma DLQ apos retries.

Tambem existe uma camada opcional de HA/failover inspirada no exemplo `SilvioCristiano/Resilient-HA-OCI-Streaming`: se o endpoint ativo falhar, a aplicacao cria ou reutiliza streams equivalentes em outra regiao, salva OCID/endpoint/bootstrap em `stream.properties`, atualiza os clients Kafka e reinicia os consumers para consumir do novo stream.

## Como executar no Eclipse

1. Importe o projeto como `Existing Maven Project`.
2. Configure as variaveis de ambiente ou argumentos de VM/Program Arguments conforme seu ambiente Kafka/OCI.
3. Execute a classe `br.com.demo.ocistreaming.OciStreamingDemoApplication`.
4. No console, informe a quantidade de mensagens quando aparecer:

```text
Quantidade de mensagens para produzir [10]:
```

Para nao usar prompt interativo, passe em `Program arguments`:

```text
--demo.producer.interactive=false --demo.producer.message-count=20
```

## Configuracao OCI Streaming

Crie dois streams no OCI Streaming: um principal e um para DLQ. Depois configure:

```text
SPRING_PROFILES_ACTIVE=oci
OCI_STREAMING_BOOTSTRAP_SERVERS=<endpoint-do-stream-pool>:9092
OCI_STREAMING_SASL_JAAS_CONFIG=org.apache.kafka.common.security.plain.PlainLoginModule required username="<tenancy>/<user>/<stream-pool-ocid>" password="<auth-token>";
DEMO_TOPIC_ORDERS=<stream-principal>
DEMO_TOPIC_ORDERS_DLQ=<stream-dlq>
```

Para Kafka local, use os defaults `localhost:9092`, `orders-demo` e `orders-demo.DLQ`. Se quiser que o Spring tente criar os topicos em Kafka local:

```text
--demo.topics.create=true
```

Em OCI, normalmente deixe `demo.topics.create=false` e crie os streams antes.

## Failover entre regioes

Para habilitar o failover automatico, alem das variaveis do OCI Streaming, configure:

```text
DEMO_FAILOVER_ENABLED=true
DEMO_FAILOVER_COMPARTMENT_ID=<ocid-do-compartment-para-stream-secundario>
DEMO_FAILOVER_TARGET_REGION=sa-vinhedo-1
DEMO_OCI_CONFIG_PATH=/Users/<usuario>/.oci/config
DEMO_OCI_PROFILE=DEFAULT
```

Quando o producer ou o health check detecta erro compatível com queda/transiente do stream, como timeout, desconexao, `500` ou `429`, o fluxo faz:

1. Verifica se ja existe um secundario salvo em `./data/stream.properties`.
2. Se nao existir, cria o stream principal e a DLQ na regiao secundaria.
3. Deriva o bootstrap Kafka a partir do endpoint OCI, por exemplo `cell-1.streaming.sa-vinhedo-1.oci.oraclecloud.com:9092`.
4. Salva o estado ativo em `stream.properties`.
5. Atualiza `ProducerFactory` e `ConsumerFactory`.
6. Reinicia os listeners Kafka para consumir do endpoint novo.

Por padrao, os streams secundarios sao criados com os mesmos nomes dos topicos (`orders-demo` e `orders-demo.DLQ`). Isso simplifica o failover porque a aplicacao troca o endpoint, mas mantem o mesmo topico logico. Se quiser nomes diferentes:

```text
DEMO_FAILOVER_SECONDARY_STREAM_NAME=orders-demo-secondary
DEMO_FAILOVER_SECONDARY_DLQ_STREAM_NAME=orders-demo-secondary.DLQ
```

Se o stream secundario estiver em outro stream pool e exigir outro username SASL, informe tambem:

```text
DEMO_FAILOVER_SECONDARY_SASL_JAAS_CONFIG=org.apache.kafka.common.security.plain.PlainLoginModule required username="<tenancy>/<user>/<stream-pool-secundario-ocid>" password="<auth-token>";
```

O arquivo salvo fica assim:

```properties
active.role=SECONDARY
active.region=sa-vinhedo-1
active.topic.orders=orders-demo
active.topic.orders-dlq=orders-demo.DLQ
secondary.stream.ocid=ocid1.stream...
secondary.dlq-stream.ocid=ocid1.stream...
secondary.messages.endpoint=https://cell-1.streaming.sa-vinhedo-1.oci.oraclecloud.com
secondary.kafka.bootstrap-servers=cell-1.streaming.sa-vinhedo-1.oci.oraclecloud.com:9092
```

Na proxima inicializacao, se `DEMO_FAILOVER_ENABLED=true` e o arquivo existir, a aplicacao ja inicia usando o endpoint secundario salvo.

## Testes pelo console

Produzir 50 mensagens sem prompt:

```text
--demo.producer.interactive=false --demo.producer.message-count=50
```

Testar idempotencia: execute duas vezes com os mesmos argumentos. Como `demo.producer.use-deterministic-event-ids=true`, os mesmos `eventId` serao gerados e o consumer vai ignorar duplicados ja processados.

Testar retry e DLQ a cada 5 mensagens:

```text
--demo.producer.interactive=false --demo.producer.message-count=15 --demo.producer.poison-every=5 --demo.consumer.retry-attempts=2
```

Testar falha temporaria que depois processa com sucesso:

```text
--demo.producer.interactive=false --demo.producer.message-count=10 --demo.producer.transient-every=3 --demo.consumer.transient-failures-before-success=2
```

Ajustar lote e paralelismo:

```text
--demo.consumer.batch-size=25 --demo.consumer.concurrency=3
```

## Praticas implementadas

| Pratica | Onde esta implementado |
| --- | --- |
| Partition Key Consistente | `OrderEvent.partitionKey()` e `DemoProducerRunner`, usando `orderId` como chave Kafka. |
| Consumer Idempotente | `JdbcProcessedEventRepository`, com tabela `processed_events` e `event_id` como chave primaria. |
| Commit Apos Sucesso | `OrderEventBatchConsumer` chama `acknowledgment.acknowledge()` somente apos processar todo o batch. |
| Retry + DLQ | `KafkaDemoConfig#errorHandler`, com `DefaultErrorHandler`, `FixedBackOff` e `DeadLetterPublishingRecoverer`. |
| Monitoramento de Lag | `ConsumerLagMonitor`, que consulta offsets commitados e offsets finais via `AdminClient`. |
| Cooperative Sticky Rebalance | `partition.assignment.strategy=org.apache.kafka.clients.consumer.CooperativeStickyAssignor` em `application.yml` e `KafkaDemoConfig`. |
| Batch Processing | `ConcurrentKafkaListenerContainerFactory#setBatchListener(true)` e listener recebendo `List<ConsumerRecord<...>>`. |
| HA Failover Regional | `StreamingFailoverCoordinator`, `OciStreamProvisioner`, `StreamingFailoverStateStore` e `KafkaClientSwitchService`. |

## Arquivos principais

- `src/main/resources/application.yml`: configuracao Kafka/OCI, producer, consumer, retry e lag.
- `src/main/resources/schema.sql`: tabela H2 de idempotencia.
- `src/main/java/br/com/demo/ocistreaming/producer/DemoProducerRunner.java`: producer controlavel pelo console.
- `src/main/java/br/com/demo/ocistreaming/consumer/OrderEventBatchConsumer.java`: consumer em batch com commit manual.
- `src/main/java/br/com/demo/ocistreaming/config/KafkaDemoConfig.java`: serializers, batch listener, retry, DLQ e rebalance.
- `src/main/java/br/com/demo/ocistreaming/ha`: failover regional, criacao de stream secundario, persistencia em arquivo e troca dos clients Kafka.

## Observabilidade

O lag aparece periodicamente no console. Tambem foram habilitados endpoints Actuator:

```text
/actuator/health
/actuator/metrics
/actuator/prometheus
```

O H2 grava a tabela de idempotencia em `./data/processed-events`. Para repetir uma demonstracao sem historico, pare a aplicacao e remova os arquivos desse diretorio.
