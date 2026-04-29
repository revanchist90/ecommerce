package com.publicnext.orders.dto;

import java.math.BigDecimal;
import java.util.UUID;

public interface OrderLineInput {

    UUID productId();

    Integer quantity();

    BigDecimal unitPrice();
}
