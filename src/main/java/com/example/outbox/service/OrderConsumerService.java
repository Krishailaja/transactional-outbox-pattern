package com.example.outbox.service;

import com.example.outbox.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderConsumerService {

    private final JdbcTemplate jdbcTemplate;

    @EventListener
    public void handleOrderEvent(OrderCreatedEvent event) {
        String eventId = event.eventId();

        // 1. Idempotency Check against processed_events table
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id = ?",
                Integer.class,
                eventId
        );

        if (count != null && count > 0) {
            System.out.println("DUPLICATE DETECTED! Event ID " + eventId + " already processed. Skipping.");
            return;
        }

        // 2. Process Business Logic
        System.out.println("Successfully processed Event ID: " + eventId);

        // 3. Record in processed_events table
        jdbcTemplate.update(
                "INSERT INTO processed_events (event_id, processed_at) VALUES (?, NOW())",
                eventId
        );
    }
}