package com.omni.orders.api.mapper;

import com.omni.orders.api.dto.request.OrderItemRequest;
import com.omni.orders.api.dto.request.PaymentMode;
import com.omni.orders.api.dto.request.ProcessPaymentRequest;
import com.omni.orders.api.dto.response.OrderItemResponse;
import com.omni.orders.api.dto.response.OrderResponse;
import com.omni.orders.api.dto.response.PaymentResponse;
import com.omni.orders.api.dto.response.ProductResponse;
import com.omni.orders.domain.model.CustomerOrder;
import com.omni.orders.domain.model.OrderItem;
import com.omni.orders.domain.model.Payment;
import com.omni.orders.domain.model.Product;
import com.omni.orders.domain.service.OrderItemCommand;
import com.omni.orders.domain.service.ProcessPaymentCommand;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Translates between API DTOs and domain model / service command objects.
 * Pure structural translation — no business logic.
 */
@Component
public class OrderApiMapper {

    public OrderResponse toResponse(CustomerOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getIdempotencyKey(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getItems().stream().map(this::toItemResponse).toList(),
                order.getPayment() != null ? toPaymentResponse(order.getPayment()) : null,
                order.getVersion(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getCancelReason()
        );
    }

    public List<OrderItemCommand> toOrderItemCommands(List<OrderItemRequest> items) {
        return items.stream()
                .map(i -> new OrderItemCommand(i.productId(), i.quantity()))
                .toList();
    }

    public ProcessPaymentCommand toPaymentCommand(ProcessPaymentRequest request) {
        return new ProcessPaymentCommand(
                request.paymentMode().name(),
                request.transactionId(),
                request.amount(),
                request.currency(),
                request.paidAt()
        );
    }

    public ProductResponse toProductResponse(Product product) {
        return new ProductResponse(
                product.getId(), product.getSku(),
                product.getName(), product.getPrice());
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getProductId(), item.getProductName(),
                item.getQuantity(), item.getUnitPrice(), item.getItemTotal());
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return new PaymentResponse(
                PaymentMode.valueOf(payment.getMode()),
                payment.getTransactionId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaidAt());
    }
}
