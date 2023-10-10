package com.struchev.invest.service.processor;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.entity.OrderDomainEntity;
import com.struchev.invest.service.candle.CandleHistoryReverseForShortService;
import com.struchev.invest.service.candle.CandleHistoryService;
import com.struchev.invest.service.notification.NotificationForShortService;
import com.struchev.invest.service.notification.NotificationService;
import com.struchev.invest.service.order.OrderForShortService;
import com.struchev.invest.service.order.OrderService;
import com.struchev.invest.strategy.AStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервис - фасад для калькуляторов по разным стратегиям
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CalculatorFacade {
    private final List<ICalculatorService> calculateServices;
    private Map<AStrategy.Type, ICalculatorService> calculateServiceByType;
    private Map<AStrategy.Type, ICalculatorService> calculateServiceByTypeShort;

    private final CandleHistoryService candleHistoryService;
    private final NotificationService notificationService;
    private final OrderService orderService;

    private final CandleHistoryReverseForShortService candleHistoryReverseForShortService;
    private final NotificationForShortService notificationForShortService;
    private final OrderForShortService orderForShortService;

    public <T extends AStrategy> boolean isShouldBuy(T strategy, CandleDomainEntity candle) {
        try {
            return calculateServiceByType.get(strategy.getType()).isShouldBuy(strategy, candle);
        } catch (RuntimeException e) {
            log.info("error in strategy " + strategy.getName(), e);
            throw e;
        }
    }

    public <T extends AStrategy> boolean isTrendBuy(T strategy, CandleDomainEntity candle) {
        try {
            var s = calculateServiceByType.get(strategy.getType());
            if (s instanceof ICalculatorTrendService) {
                return ((ICalculatorTrendService<T>) s).isTrendBuy(strategy, candle);
            }
            return false;
        } catch (RuntimeException e) {
            log.info("error in strategy " + strategy.getName(), e);
            throw e;
        }
    }

    public <T extends AStrategy> boolean isShouldSell(T strategy, CandleDomainEntity candle, BigDecimal purchaseRate) {
        try {
            return calculateServiceByType.get(strategy.getType()).isShouldSell(strategy, candle, purchaseRate);
        } catch (RuntimeException e) {
            log.info("error in strategy " + strategy.getName(), e);
            throw e;
        }
    }

    public <T extends AStrategy> boolean isShouldBuyShort(T strategy, CandleDomainEntity candle) {
        try {
            var s = calculateServiceByTypeShort.get(strategy.getType());
            var c = candleHistoryReverseForShortService.prepareCandleForShort(candle.clone());
            if (s instanceof ICalculatorShortService) {
                return s.isShouldBuy(strategy, c);
            }
            return false;
        } catch (RuntimeException e) {
            log.info("error in strategy " + strategy.getName(), e);
            throw e;
        }
    }

    public <T extends AStrategy> boolean isTrendBuyShort(T strategy, CandleDomainEntity candle) {
        try {
            var s = calculateServiceByTypeShort.get(strategy.getType());
            var c = candleHistoryReverseForShortService.prepareCandleForShort(candle.clone());
            if (s instanceof ICalculatorTrendService) {
                return ((ICalculatorTrendService<T>) s).isTrendBuy(strategy, c);
            }
            return false;
        } catch (RuntimeException e) {
            log.info("error in strategy " + strategy.getName(), e);
            throw e;
        }
    }

    public <T extends AStrategy> boolean isShouldSellShort(T strategy, CandleDomainEntity candle, BigDecimal purchaseRate) {
        try {
            var s = calculateServiceByTypeShort.get(strategy.getType());
            var c = candleHistoryReverseForShortService.prepareCandleForShort(candle.clone());
            if (s instanceof ICalculatorShortService) {
                return s.isShouldSell(strategy, c, candleHistoryReverseForShortService.preparePrice(purchaseRate));
            }
            return false;
        } catch (RuntimeException e) {
            log.info("error in strategy " + strategy.getName(), e);
            throw e;
        }
    }

    public Boolean isOrderNeedSell(OrderDomainEntity order, CandleDomainEntity candleDomainEntity)
    {
        var stopLossPrice = order.getDetails().getCurrentPrices().getOrDefault("stopLossPrice", BigDecimal.ZERO);
        if (stopLossPrice.equals(BigDecimal.ZERO)) {
            return true;
        }
        var orderPrice = order.getPurchasePrice();
        if (order.isShort()) {
            orderPrice = candleHistoryReverseForShortService.preparePrice(order.getSellPrice());
            stopLossPrice = candleHistoryReverseForShortService.preparePrice(stopLossPrice);
            candleDomainEntity = candleHistoryReverseForShortService.prepareCandleForShort(candleDomainEntity.clone());
        }
        if (orderPrice.compareTo(candleDomainEntity.getClosingPrice()) > 0) {
            var annotation = "orderPrice > closingPrice: " + orderPrice + " > " + candleDomainEntity.getClosingPrice() + " ";
            // в минусе и мижняя граница либо высоко, либо очень низко
            if (stopLossPrice.compareTo(orderPrice) > 0) {
                order.getDetails().getAnnotations().put("needSell", annotation + "stopLossPrice > orderPrice: " + stopLossPrice + " > " + orderPrice);
                return true;
            }
            var middle = stopLossPrice.add(orderPrice.subtract(stopLossPrice).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP));
            var middleNear = middle.add(orderPrice.subtract(middle).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP));
            if (
                    candleDomainEntity.getClosingPrice().compareTo(middle) > 0
                    && candleDomainEntity.getClosingPrice().compareTo(middleNear) < 0
            ) {
                order.getDetails().getAnnotations().put("needSell", annotation + "closingPrice > middle: " + candleDomainEntity.getClosingPrice() + " > " + middle
                        + " closingPrice < middleNear: " + candleDomainEntity.getClosingPrice() + " > " + middleNear);
                return true;
            }
        } else {
            var annotation = "orderPrice <= closingPrice: " + orderPrice + " <= " + candleDomainEntity.getClosingPrice() + " ";
            // в плюсе, но не большом
            if (orderPrice.compareTo(stopLossPrice) < 0) {
                order.getDetails().getAnnotations().put("needSell", annotation + "orderPrice < stopLossPrice: " + orderPrice + " < " + stopLossPrice);
                return true;
            }
        }
        return false;
    }

    @PostConstruct
    private void init() {
        calculateServiceByType = calculateServices.stream().collect(Collectors.toMap(c -> c.getStrategyType(), c -> c));

        calculateServiceByTypeShort = calculateServices.stream().collect(Collectors.toMap(c -> c.getStrategyType(), c -> {
            if (c instanceof ICalculatorShortService) {
                try {
                    var cShort = ((ICalculatorShortService) c).cloneService(orderForShortService);
                    if (cShort instanceof ICalculatorShortService) {
                        cShort.setCandleHistoryService(candleHistoryReverseForShortService);
                        cShort.setNotificationService(notificationForShortService);

                        ((ICalculatorShortService) c).setCandleHistoryService(candleHistoryService);
                        ((ICalculatorShortService) c).setNotificationService(notificationService);
                        ((ICalculatorShortService) c).setOrderService(orderService);
                    }
                    return (ICalculatorService) cShort;
                } catch (CloneNotSupportedException e) {
                    throw new RuntimeException(e);
                }
            }
            return c;
        }));
    }
}
