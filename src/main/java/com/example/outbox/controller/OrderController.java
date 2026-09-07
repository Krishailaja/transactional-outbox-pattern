package com.example.outbox.controller;

import com.example.outbox.entity.OrderEvent;
import com.example.outbox.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderEvent> createOrder(@RequestBody CreateOrderRequest request) {
        OrderEvent order = orderService.createOrder(request.customerId(), request.amount());
        return ResponseEntity.ok(order);
    }

    public record CreateOrderRequest(String customerId, BigDecimal amount) {}
}