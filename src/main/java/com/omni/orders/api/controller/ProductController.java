package com.omni.orders.api.controller;

import com.omni.orders.api.dto.response.ProductResponse;
import com.omni.orders.api.mapper.OrderApiMapper;
import com.omni.orders.domain.port.ProductRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Collection;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Browse the product catalog")
public class ProductController {

    private final ProductRepository productRepository;
    private final OrderApiMapper    orderApiMapper;

    public ProductController(ProductRepository productRepository, OrderApiMapper orderApiMapper) {
        this.productRepository = productRepository;
        this.orderApiMapper    = orderApiMapper;
    }

    @Operation(summary = "List all active products",
               description = "Returns products seeded from the DB. Use productId values when creating orders.")
    @GetMapping
    public Collection<ProductResponse> listProducts() {
        return productRepository.findAll().stream()
                .map(orderApiMapper::toProductResponse)
                .toList();
    }
}
