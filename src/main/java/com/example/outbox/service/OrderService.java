package com.example.outbox.service;


import com.example.outbox.entity.OrderEvent;
import com.example.outbox.entity.OutboxEvent;
import com.example.outbox.repository.OrderRepository;
import com.example.outbox.repository.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;

    public OrderService(OrderRepository orderRepository, OutboxRepository outboxRepository) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public OrderEvent createOrder(String customerId, BigDecimal amount) {
        // 1. Save business entity
        OrderEvent order = new OrderEvent(customerId, amount);
        orderRepository.save(order);

        // 2. Prepare event payload (JSON representation)
        String payload = String.format("{\"orderId\":\"%s\",\"customerId\":\"%s\",\"amount\":%s}",
                order.getId(), customerId, amount);

        // 3. Save Outbox event in the SAME database transaction
        OutboxEvent outboxEvent = new OutboxEvent("ORDER", order.getId().toString(), "ORDER_CREATED", payload);
        outboxRepository.save(outboxEvent);

        return order;
    }
}