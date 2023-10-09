package com.struchev.invest.service.order;

import com.struchev.invest.entity.OrderDomainEntity;
import com.struchev.invest.service.candle.CandleHistoryReverseForShortService;
import com.struchev.invest.strategy.AStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class OrderForShortService implements IOrderService
{
    private final OrderService orderService;
    private final CandleHistoryReverseForShortService candleHistoryReverseForShortService;

    @Override
    public Order findLastByFigiAndStrategy(String figi, AStrategy strategy) {
        var order = orderService.findLastShortOrderDomainByFigiAndStrategy(figi, strategy);
        if (order == null) {
            return null;
        }
        return Order.builder()
                .orderDomainEntity(order)
                .purchasePrice(candleHistoryReverseForShortService.preparePrice(order.getSellPrice()))
                .purchaseDateTime(order.getSellDateTime())
                .sellPrice(candleHistoryReverseForShortService.preparePrice(order.getPurchasePrice()))
                .sellDateTime(order.getPurchaseDateTime())
                .details(order.getDetails())
                .sellProfit(order.getSellProfit())
                .build();
    }

    @Override
    public Order findActiveByFigiAndStrategy(String figi, AStrategy strategy) {
        var order = orderService.findActiveOrderDomainShortByFigiAndStrategy(figi, strategy);
        if (order == null) {
            return null;
        }
        return Order.builder()
                .orderDomainEntity(order)
                .purchasePrice(candleHistoryReverseForShortService.preparePrice(order.getSellPrice()))
                .purchaseDateTime(order.getSellDateTime())
                .details(order.getDetails())
                .build();
    }

    @Override
    public OrderDomainEntity updateDetailsCurrentPrice(Order order, String key, BigDecimal price) {
        var o = orderService.updateDetailsCurrentPrice(order, key, price);
        return o;
    }
}
