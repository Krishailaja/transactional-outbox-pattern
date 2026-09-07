# Transactional Outbox Pattern & Idempotent Consumer

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)

A lightweight, enterprise-grade implementation of the **Transactional Outbox Pattern** and **Idempotent Consumer Strategy** built using **Java 21**, **Spring Boot 3**, and **MySQL**. 

This project solves the fundamental **Dual-Write Problem** in distributed systems, guaranteeing reliable asynchronous event processing without requiring heavy external infrastructure like Apache Kafka or RabbitMQ for local deployment.

---

## 💡 The Problem: The Dual-Write Anomaly

In microservice and event-driven architectures, application logic often requires updating a local database and publishing a message/event to a message broker:

```text
[ HTTP Request ] ──► [ Order Service ] ──┬──► 1. INSERT INTO orders (MySQL)
                                        └──► 2. PUBLISH OrderCreatedEvent (Kafka / Event Bus)

What happens when failures occur?
Database succeeds, Event publishing fails: The database transaction commits, but the event is lost. Downstream microservices (e.g., Billing, Inventory, Notification) never hear about the new order.

Event publishing succeeds, Database fails: The event is sent, but the database transaction rolls back. Downstream services process phantom state that does not exist in the primary system.

Dual writes across non-transactional boundaries (Database + Message Broker) break data consistency guarantees.

🛡️ The Solution: Transactional Outbox Pattern
The Transactional Outbox Pattern replaces the dual write with a single, atomic local database transaction:

Atomic Write: The business state mutation (orders) and the event record (outbox_events) are committed within the same local ACID transaction.

Asynchronous Dispatch: A background worker (OutboxPublisher) polls unhandled outbox events and publishes them.

Status Tracking: Once published, the outbox record status transitions from PENDING to PUBLISHED.

┌──────────────────────────────────────────────────┐
                                    │               ACID DB TRANSACTION                │
                                    │                                                  │
[ HTTP Request ] ──► [ OrderService ] ──► INSERT INTO orders                      │
                                    │     INSERT INTO outbox_events (status='PENDING') │
                                    └──────────────────────────────────────────────────┘
                                                             │
                                                             ▼ (MySQL DB)
                                                  ┌─────────────────────┐
                                                  │    outbox_events    │
                                                  └─────────────────────┘
                                                             │
                                                             ▼ (Poll @Scheduled)
                                                   [ OutboxPublisher ]
                                                             │
                                                             ▼
                                                [ ApplicationEventPublisher ]
                                                             │
                                                             ▼
                                                 [ OrderConsumerService ]
                                                             │
                                                             ▼ (Check & Insert)
                                                  ┌─────────────────────┐
                                                  │   processed_events  │
                                                  └─────────────────────┘
🔄 At-Least-Once Delivery & Idempotent Consumer
Because network or execution failures can cause duplicate event publishing, the system enforces At-Least-Once Delivery guarantees. To prevent duplicate side-effects (e.g., double charging a customer), the consumer service implements an Idempotent Consumer:

Before executing business processing, OrderConsumerService checks the processed_events table for the incoming event_id.

If present: The event is flagged as a duplicate (DUPLICATE DETECTED!) and safely ignored.

If missing: The consumer processes the event and atomically records the event_id and timestamp (processed_at) into processed_events.

🚀 Tech Stack & Core Libraries
Language: Java 21 (Virtual Threads, Pattern Matching)

Framework: Spring Boot 3.x (Spring EventListener, Spring Task Scheduling)

Persistence: MySQL 8.0+ / Spring Data JDBC (JdbcTemplate)

Utilities: Lombok, Byte-to-UUID Converter

🗄️ Database Schema Design
CREATE DATABASE IF NOT EXISTS outbox_db;
USE outbox_db;

-- Primary Domain Table
CREATE TABLE IF NOT EXISTS orders (
    id BINARY(16) PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Transactional Outbox Table
CREATE TABLE IF NOT EXISTS outbox_events (
    id BINARY(16) PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id BINARY(16) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Consumer Idempotency Tracking Table
CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(36) PRIMARY KEY,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

⚙️ Getting Started & Local Setup
Prerequisites
Java 21 JDK

Maven 3.8+

MySQL 8.0+

1. Database Configuration
Update src/main/resources/application.properties with your database credentials:
spring.datasource.url=jdbc:mysql://localhost:3306/outbox_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=YOUR_MYSQL_PASSWORD
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# Scheduling Configuration
spring.task.scheduling.pool.size=2

2. Build and Run
# Clone the repository
git clone [https://github.com/Krishailaja/transactional-outbox-pattern.git](https://github.com/Krishailaja/transactional-outbox-pattern.git)
cd transactional-outbox-pattern

# Build project
mvn clean package -DskipTests

# Run Application
mvn spring-boot:run

🧪 Testing the End-to-End Flow
1. Create an Order via POST Request
Use Postman or curl to issue an order creation command:
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-101",
    "amount": 250.00
  }'
2. Observe Application Logs
Within 5 seconds, the background publisher will pick up the pending row, emit the event, and the consumer will process it:
Published event ID: d90e616e-9229-4a1a-ac03-90bfb5558d2c (Updated rows: 1)
Successfully processed Event ID: d90e616e-9229-4a1a-ac03-90bfb5558d2c

3. Verify Consumer Idempotency
To simulate an at-least-once re-delivery:

Open MySQL and manually reset an outbox row status:

UPDATE outbox_events SET status = 'PENDING' WHERE status = 'PUBLISHED' LIMIT 1;

Watch the application console on the next tick:

Published event ID: d90e616e-9229-4a1a-ac03-90bfb5558d2c (Updated rows: 1)
DUPLICATE DETECTED! Event ID d90e616e-9229-4a1a-ac03-90bfb5558d2c already processed. Skipping.

🏗️ Architectural Trade-offs & Production Upgrades
While this polling outbox architecture is highly effective for low to medium-throughput systems, enterprise production environments may consider the following evolution:

Polling Overhead: Polling MySQL (SELECT ... WHERE status = 'PENDING') introduces slight database I/O overhead as the outbox_events table grows.

CDC (Change Data Capture) Upgrade: In ultra-high-throughput environments, the polling worker can be replaced with a CDC framework like Debezium. Debezium reads raw transaction logs (binlog in MySQL) directly and streams outbox updates to Apache Kafka with zero database query overhead and sub-millisecond latency.
