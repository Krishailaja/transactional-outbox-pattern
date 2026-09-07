package com.example.outbox.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class OrderEvent {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String status; // "CREATED", "CANCELLED"

    @Column(nullable = false)
    private Instant createdAt;

    public OrderEvent() {}

    public OrderEvent(String customerId, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.amount = amount;
        this.status = "CREATED";
        this.createdAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}