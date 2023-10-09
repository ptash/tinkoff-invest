package com.struchev.invest.service.order;

import com.struchev.invest.entity.OrderDomainEntity;
import com.struchev.invest.strategy.AStrategy;

import java.math.BigDecimal;

public interface IOrderService {
    OrderDomainEntity findLastByFigiAndStrategy(String figi, AStrategy strategy);
    OrderDomainEntity findActiveByFigiAndStrategy(String figi, AStrategy strategy);
    OrderDomainEntity updateDetailsCurrentPrice(OrderDomainEntity order, String key, BigDecimal price);
}
