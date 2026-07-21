package br.com.demo.ocistreaming.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo")
public class StreamingDemoProperties {

    private final Topics topics = new Topics();
    private final Producer producer = new Producer();
    private final Consumer consumer = new Consumer();
    private final Failover failover = new Failover();

    public Topics getTopics() {
        return topics;
    }

    public Producer getProducer() {
        return producer;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public Failover getFailover() {
        return failover;
    }

    public static class Topics {
        private String orders = "orders-demo";
        private String ordersDlq = "orders-demo.DLQ";
        private boolean create = false;
        private int partitions = 3;
        private short replicationFactor = 1;

        public String getOrders() {
            return orders;
        }

        public void setOrders(String orders) {
            this.orders = orders;
        }

        public String getOrdersDlq() {
            return ordersDlq;
        }

        public void setOrdersDlq(String ordersDlq) {
            this.ordersDlq = ordersDlq;
        }

        public boolean isCreate() {
            return create;
        }

        public void setCreate(boolean create) {
            this.create = create;
        }

        public int getPartitions() {
            return partitions;
        }

        public void setPartitions(int partitions) {
            this.partitions = partitions;
        }

        public short getReplicationFactor() {
            return replicationFactor;
        }

        public void setReplicationFactor(short replicationFactor) {
            this.replicationFactor = replicationFactor;
        }
    }

    public static class Producer {
        private boolean enabled = true;
        private boolean interactive = true;
        private int messageCount = 10;
        private long delayMs = 150;
        private int startSequence = 1;
        private int orderPoolSize = 5;
        private int poisonEvery = 0;
        private int transientEvery = 0;
        private boolean useDeterministicEventIds = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isInteractive() {
            return interactive;
        }

        public void setInteractive(boolean interactive) {
            this.interactive = interactive;
        }

        public int getMessageCount() {
            return messageCount;
        }

        public void setMessageCount(int messageCount) {
            this.messageCount = messageCount;
        }

        public long getDelayMs() {
            return delayMs;
        }

        public void setDelayMs(long delayMs) {
            this.delayMs = delayMs;
        }

        public int getStartSequence() {
            return startSequence;
        }

        public void setStartSequence(int startSequence) {
            this.startSequence = startSequence;
        }

        public int getOrderPoolSize() {
            return orderPoolSize;
        }

        public void setOrderPoolSize(int orderPoolSize) {
            this.orderPoolSize = orderPoolSize;
        }

        public int getPoisonEvery() {
            return poisonEvery;
        }

        public void setPoisonEvery(int poisonEvery) {
            this.poisonEvery = poisonEvery;
        }

        public int getTransientEvery() {
            return transientEvery;
        }

        public void setTransientEvery(int transientEvery) {
            this.transientEvery = transientEvery;
        }

        public boolean isUseDeterministicEventIds() {
            return useDeterministicEventIds;
        }

        public void setUseDeterministicEventIds(boolean useDeterministicEventIds) {
            this.useDeterministicEventIds = useDeterministicEventIds;
        }
    }

    public static class Consumer {
        private boolean enabled = true;
        private int concurrency = 1;
        private int batchSize = 10;
        private long simulatedWorkMs = 100;
        private long retryBackoffMs = 1000;
        private long retryAttempts = 3;
        private int transientFailuresBeforeSuccess = 2;
        private boolean lagMonitorEnabled = true;
        private long lagMonitorIntervalMs = 15000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int concurrency) {
            this.concurrency = concurrency;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public long getSimulatedWorkMs() {
            return simulatedWorkMs;
        }

        public void setSimulatedWorkMs(long simulatedWorkMs) {
            this.simulatedWorkMs = simulatedWorkMs;
        }

        public long getRetryBackoffMs() {
            return retryBackoffMs;
        }

        public void setRetryBackoffMs(long retryBackoffMs) {
            this.retryBackoffMs = retryBackoffMs;
        }

        public long getRetryAttempts() {
            return retryAttempts;
        }

        public void setRetryAttempts(long retryAttempts) {
            this.retryAttempts = retryAttempts;
        }

        public int getTransientFailuresBeforeSuccess() {
            return transientFailuresBeforeSuccess;
        }

        public void setTransientFailuresBeforeSuccess(int transientFailuresBeforeSuccess) {
            this.transientFailuresBeforeSuccess = transientFailuresBeforeSuccess;
        }

        public boolean isLagMonitorEnabled() {
            return lagMonitorEnabled;
        }

        public void setLagMonitorEnabled(boolean lagMonitorEnabled) {
            this.lagMonitorEnabled = lagMonitorEnabled;
        }

        public long getLagMonitorIntervalMs() {
            return lagMonitorIntervalMs;
        }

        public void setLagMonitorIntervalMs(long lagMonitorIntervalMs) {
            this.lagMonitorIntervalMs = lagMonitorIntervalMs;
        }
    }

    public static class Failover {
        private boolean enabled = false;
        private boolean healthCheckEnabled = true;
        private boolean activatePersistedSecondaryOnStartup = true;
        private boolean createStreams = true;
        private boolean createDlq = true;
        private String stateFile = "./data/stream.properties";
        private String ociConfigPath = System.getProperty("user.home") + "/.oci/config";
        private String ociProfile = "DEFAULT";
        private String compartmentId = "";
        private String targetRegion = "sa-vinhedo-1";
        private String secondaryStreamName = "";
        private String secondaryDlqStreamName = "";
        private String secondaryBootstrapServers = "";
        private String secondarySecurityProtocol = "";
        private String secondarySaslMechanism = "";
        private String secondarySaslJaasConfig = "";
        private int partitions = 0;
        private int kafkaBootstrapPort = 9092;
        private int adminMaxWaitSeconds = 120;
        private int adminPollIntervalSeconds = 5;
        private long healthCheckIntervalMs = 15000;
        private long healthCheckTimeoutMs = 5000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isHealthCheckEnabled() {
            return healthCheckEnabled;
        }

        public void setHealthCheckEnabled(boolean healthCheckEnabled) {
            this.healthCheckEnabled = healthCheckEnabled;
        }

        public boolean isActivatePersistedSecondaryOnStartup() {
            return activatePersistedSecondaryOnStartup;
        }

        public void setActivatePersistedSecondaryOnStartup(boolean activatePersistedSecondaryOnStartup) {
            this.activatePersistedSecondaryOnStartup = activatePersistedSecondaryOnStartup;
        }

        public boolean isCreateStreams() {
            return createStreams;
        }

        public void setCreateStreams(boolean createStreams) {
            this.createStreams = createStreams;
        }

        public boolean isCreateDlq() {
            return createDlq;
        }

        public void setCreateDlq(boolean createDlq) {
            this.createDlq = createDlq;
        }

        public String getStateFile() {
            return stateFile;
        }

        public void setStateFile(String stateFile) {
            this.stateFile = stateFile;
        }

        public String getOciConfigPath() {
            return ociConfigPath;
        }

        public void setOciConfigPath(String ociConfigPath) {
            this.ociConfigPath = ociConfigPath;
        }

        public String getOciProfile() {
            return ociProfile;
        }

        public void setOciProfile(String ociProfile) {
            this.ociProfile = ociProfile;
        }

        public String getCompartmentId() {
            return compartmentId;
        }

        public void setCompartmentId(String compartmentId) {
            this.compartmentId = compartmentId;
        }

        public String getTargetRegion() {
            return targetRegion;
        }

        public void setTargetRegion(String targetRegion) {
            this.targetRegion = targetRegion;
        }

        public String getSecondaryStreamName() {
            return secondaryStreamName;
        }

        public void setSecondaryStreamName(String secondaryStreamName) {
            this.secondaryStreamName = secondaryStreamName;
        }

        public String getSecondaryDlqStreamName() {
            return secondaryDlqStreamName;
        }

        public void setSecondaryDlqStreamName(String secondaryDlqStreamName) {
            this.secondaryDlqStreamName = secondaryDlqStreamName;
        }

        public String getSecondaryBootstrapServers() {
            return secondaryBootstrapServers;
        }

        public void setSecondaryBootstrapServers(String secondaryBootstrapServers) {
            this.secondaryBootstrapServers = secondaryBootstrapServers;
        }

        public String getSecondarySecurityProtocol() {
            return secondarySecurityProtocol;
        }

        public void setSecondarySecurityProtocol(String secondarySecurityProtocol) {
            this.secondarySecurityProtocol = secondarySecurityProtocol;
        }

        public String getSecondarySaslMechanism() {
            return secondarySaslMechanism;
        }

        public void setSecondarySaslMechanism(String secondarySaslMechanism) {
            this.secondarySaslMechanism = secondarySaslMechanism;
        }

        public String getSecondarySaslJaasConfig() {
            return secondarySaslJaasConfig;
        }

        public void setSecondarySaslJaasConfig(String secondarySaslJaasConfig) {
            this.secondarySaslJaasConfig = secondarySaslJaasConfig;
        }

        public int getPartitions() {
            return partitions;
        }

        public void setPartitions(int partitions) {
            this.partitions = partitions;
        }

        public int getKafkaBootstrapPort() {
            return kafkaBootstrapPort;
        }

        public void setKafkaBootstrapPort(int kafkaBootstrapPort) {
            this.kafkaBootstrapPort = kafkaBootstrapPort;
        }

        public int getAdminMaxWaitSeconds() {
            return adminMaxWaitSeconds;
        }

        public void setAdminMaxWaitSeconds(int adminMaxWaitSeconds) {
            this.adminMaxWaitSeconds = adminMaxWaitSeconds;
        }

        public int getAdminPollIntervalSeconds() {
            return adminPollIntervalSeconds;
        }

        public void setAdminPollIntervalSeconds(int adminPollIntervalSeconds) {
            this.adminPollIntervalSeconds = adminPollIntervalSeconds;
        }

        public long getHealthCheckIntervalMs() {
            return healthCheckIntervalMs;
        }

        public void setHealthCheckIntervalMs(long healthCheckIntervalMs) {
            this.healthCheckIntervalMs = healthCheckIntervalMs;
        }

        public long getHealthCheckTimeoutMs() {
            return healthCheckTimeoutMs;
        }

        public void setHealthCheckTimeoutMs(long healthCheckTimeoutMs) {
            this.healthCheckTimeoutMs = healthCheckTimeoutMs;
        }
    }
}
