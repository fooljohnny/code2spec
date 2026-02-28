package com.example.api;

import org.springframework.web.bind.annotation.*;

/**
 * 订单管理接口
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    /**
     * 创建订单
     * @param request 订单创建请求
     * @return 订单ID
     */
    @PostMapping
    public String createOrder(@RequestBody CreateOrderRequest request) {
        return "order-123";
    }

    /**
     * 根据ID查询订单
     */
    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable String id) {
        return new OrderResponse();
    }

    /**
     * 取消订单
     */
    @DeleteMapping("/{id}")
    public void cancelOrder(@PathVariable String id) {
    }
}
