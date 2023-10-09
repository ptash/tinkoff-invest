package com.struchev.invest.service.order;

import com.struchev.invest.entity.OrderDomainEntity;
import com.struchev.invest.strategy.AStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class OrderForShortService implements IOrderService
{
    private final OrderService orderService;

    @Override
    public OrderDomainEntity findLastByFigiAndStrategy(String figi, AStrategy strategy) {
        //var o = orderService.findLastByFigiAndStrategy(figi, strategy);
        return null;
    }

    @Override
    public OrderDomainEntity findActiveByFigiAndStrategy(String figi, AStrategy strategy) {
        //var o = orderService.findActiveByFigiAndStrategy(figi, strategy);
        return null;
    }

    @Override
    public OrderDomainEntity updateDetailsCurrentPrice(OrderDomainEntity order, String key, BigDecimal price) {
        var o = orderService.updateDetailsCurrentPrice(order, key, price);
        return o;
    }
}
