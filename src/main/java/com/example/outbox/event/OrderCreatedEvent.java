package com.example.outbox.event;

public record OrderCreatedEvent(String eventId, String customerId, Double amount) {}