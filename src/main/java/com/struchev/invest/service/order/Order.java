package com.struchev.invest.service.order;

import com.struchev.invest.entity.OrderDetails;
import com.struchev.invest.entity.OrderDomainEntity;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
public class Order {
    private OrderDomainEntity orderDomainEntity;
    private OffsetDateTime purchaseDateTime;
    private BigDecimal purchasePrice;
    private OffsetDateTime sellDateTime;
    private BigDecimal sellPrice;
    private OrderDetails details;
    private BigDecimal sellProfit;

    public OrderDetails getDetails() {
        if (details == null) {
            details = OrderDetails.builder().build();
        }
        return details;
    }
}
