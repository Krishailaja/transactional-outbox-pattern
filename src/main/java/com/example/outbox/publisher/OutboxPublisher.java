package com.example.outbox.publisher;

import com.example.outbox.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final JdbcTemplate jdbcTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        List<Map<String, Object>> pendingEvents = jdbcTemplate.queryForList(
                "SELECT * FROM outbox_events WHERE status = 'PENDING'"
        );

        if (pendingEvents.isEmpty()) {
            return;
        }

        for (Map<String, Object> event : pendingEvents) {
            Object rawId = event.get("id");
            Object rawAggId = event.get("aggregate_id");

            String eventId = toUuidString(rawId);
            String aggregateId = toUuidString(rawAggId);

            // 1. Publish in-memory event
            eventPublisher.publishEvent(new OrderCreatedEvent(eventId, aggregateId, 250.00));

            // 2. Mark event as PUBLISHED in MySQL using the raw ID object
            int rowsUpdated = jdbcTemplate.update(
                    "UPDATE outbox_events SET status = 'PUBLISHED' WHERE id = ?",
                    rawId
            );

            System.out.println("Published event ID: " + eventId + " (Updated rows: " + rowsUpdated + ")");
        }
    }

    private String toUuidString(Object obj) {
        if (obj == null) return null;

        if (obj instanceof byte[] bytes) {
            if (bytes.length == 16) {
                ByteBuffer bb = ByteBuffer.wrap(bytes);
                long high = bb.getLong();
                long low = bb.getLong();
                return new UUID(high, low).toString();
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }

        return obj.toString();
    }
}