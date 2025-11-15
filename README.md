Below is a **clean, well-structured Markdown (.md) documentation** in **English**, fully ready to paste into GitHub.

It explains the **goal**, **architecture**, **components**, **failover process**, and contains **code snippets** and **diagrams**.

---

# 🟦 OCI Streaming High Availability (HA) – Java Auto-Failover Architecture

This project implements a **High-Availability (HA)** and **self-healing** architecture for **Oracle Cloud Infrastructure (OCI) Streaming**, using **Java + OCI SDK**.

The system provides:

* **Primary → Secondary failover**
* **Automatic creation of a new secondary stream**
* **Exponential retry (500 / 429)**
* **Automatic consumer endpoint switching**
* **Persistence of failover metadata (OCID + Endpoint)**
* **Environment variable export for external consumers**

---

# 📌 Objective

The goal of this codebase is to build a **resilient Streaming environment** capable of:

1. **Publishing messages to a primary stream** (`OCI-PRIMARY-STREAM`, São Paulo)
2. Detecting failure conditions:

   * **HTTP 500** (server errors)
   * **HTTP 429** (throttling)
3. **Failing over automatically** to a secondary stream **in Vinhedo**
4. If the secondary stream does **not exist**, the code **creates it automatically**
5. Persisting:

   * the **secondary OCID**
   * the **secondary endpoint**
     into:
   * `stream.properties` file
   * OS environment variables
6. The consumer detects the update and automatically **switches to the new stream**.

---

# 📡 Architecture Overview

### 🟦 Normal flow

```
Producer → OCI-PRIMARY-STREAM (São Paulo)
Consumer → OCI-PRIMARY-STREAM (São Paulo)
```

### 🟥 If primary fails (500 / 429)

```
Producer detects failure
       ↓
Creates OCI-SECONDARY-STREAM (Vinhedo)
       ↓
Saves endpoint + OCID to stream.properties
       ↓
Exports env variables for external consumers
       ↓
Resumes publishing to secondary stream
```

### 🟩 Consumer auto-switch

```
Consumer → Reads properties/env
          → Switches to OCI-SECONDARY-STREAM
          → Continues consuming
```

---

# 🔧 Components

This HA streaming solution contains **four main Java components**:

---

## 1. **StreamProducer.java**

Responsible for publishing messages.

It uses `StreamManager` to send messages **with retry + failover**:

```java
manager.sendWithHA(msg);
```

---

## 2. **StreamManager.java**

The core of the HA logic:

✔ Primary delivery
✔ Exponential retry
✔ Failover detection
✔ Secondary stream creation
✔ Saving metadata
✔ Environment export

Pseudo-flow:

```text
try primary
 ├── success → done
 └── failure → retry N times
               └── still failure → create secondary
                                    switch endpoint
                                    write properties
                                    export env
                                    try again
```

---

## 3. **StreamConsumer.java**

Consumes from the stream indicated by:

* `stream.properties`, or
* Environment variables (if set), or
* Falls back to primary

It automatically switches if primary fails.

The consumer uses:

```java
cursor = consumeOnce(currentClient, currentStreamId, cursor);
```

On error, it loads:

```java
StreamUtils.loadSecondaryStreamEndpoint()
StreamUtils.loadSecondaryStreamOcid()
```

And switches instantly.

---

## 4. **StreamUtils.java**

Utility class that:

* Writes `stream.properties`
* Loads secondary metadata
* Exports OS environment variables (`setx` on Windows)

Example saved file:

```
SECONDARY_STREAM_OCID=ocid1.stream.oc1.sa-vinhedo...
SECONDARY_STREAM_ENDPOINT=https://cell-1.streaming.sa-vinhedo-1...
```

---

# 🔁 High-Availability Flow

### **1. Attempt to send to primary**

### **2. If error == 500 or 429 → retry with exponential backoff**

### **3. If retries exhausted → create secondary stream**

### **4. Update stream.properties**

### **5. Export env variables**

### **6. Switch all reads/writes to secondary**

### **7. Consumer auto-switches**

---

# 📂 Project Files

This repository contains the following main files:

```
/src/main/java/com/playbook/ai/
    StreamProducer.java
    StreamConsumer.java
    StreamManager.java
    StreamUtils.java

/src/main/resources/
    stream.properties
```

---

# 🔥 Error Handling

The system treats the following errors as **retryable / failover triggers**:

```java
return status >= 500 || status == 429;
```

These map to:

| Status  | Meaning                                     |
| ------- | ------------------------------------------- |
| **500** | Internal Server Error (service instability) |
| **429** | Too Many Requests (throttling)              |

If these occur repeatedly → **failover starts**.

---

# 🧪 Example Output (Failover Scenario)

```
Attempt 1 failed: 500. Retrying...
Attempt 2 failed: 500. Retrying...
Primary failed after retries -> starting failover...
No secondary configured. Creating secondary stream...
Secondary stream created: OCID=ocid1.stream... endpoint=https://...
Message sent to secondary stream.
```

Consumer output:

```
Primary consumer error: 500
FAILOVER: Now consuming from secondary stream:
  OCID: ocid1.stream...
  Endpoint: https://cell-1.streaming.sa-vinhedo-1.xx
```

---

# 🗂 `stream.properties`

Example automatically generated file:

```properties
#Secondary stream info (OCID + endpoint)
SECONDARY_STREAM_OCID=ocid1.stream.oc1.sa-vinhedo-1...
SECONDARY_STREAM_ENDPOINT=https://cell-1.streaming.sa-vinhedo-1.oci.oraclecloud.com
```

---

# 🚀 How to Run

### **Producer**

```bash
mvn install
java -cp target/app.jar com.playbook.ai.StreamProducer
```

### **Consumer**

```bash
java -cp target/app.jar com.playbook.ai.StreamConsumer
```

---

# 📘 Requirements

* Java 17+
* OCI Java SDK
* OCI config file (`~/.oci/config`)
* IAM permissions:

  * `STREAM_MANAGE`
  * `STREAM_READ`
  * `STREAM_CREATE` (required for auto-failover)
  * `STREAM_USE`

---

# 🔒 Security Notes

* Primary/Secondary OCIDs do **not** contain credentials.
* Authentication uses local OCI keypair.
* Failover stream creation uses your IAM policy permissions.

---

# 🎯 Summary

This Java implementation provides a **self-healing**, **auto-failover**, **HA streaming architecture** on OCI Streaming that:

* Detects primary failures
* Creates a new secondary stream dynamically
* Saves metadata locally
* Exports environment variables
* Automatically switches producer and consumer
* Guarantees message continuity even during outages

Perfect for **enterprise-grade**, **mission-critical**, and **production-ready** Streaming workloads.



