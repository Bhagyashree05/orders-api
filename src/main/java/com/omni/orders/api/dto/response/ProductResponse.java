package com.omni.orders.api.dto.response;
import java.math.BigDecimal;
import java.util.UUID;
public record ProductResponse(UUID id, String sku, String name, BigDecimal price) {}
