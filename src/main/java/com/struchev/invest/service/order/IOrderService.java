package com.struchev.invest.service.order;

import com.struchev.invest.entity.OrderDomainEntity;
import com.struchev.invest.strategy.AStrategy;

import java.math.BigDecimal;

public interface IOrderService {
    Order findLastByFigiAndStrategy(String figi, AStrategy strategy);
    Order findActiveByFigiAndStrategy(String figi, AStrategy strategy);
    OrderDomainEntity updateDetailsCurrentPrice(Order order, String key, BigDecimal price);
}
