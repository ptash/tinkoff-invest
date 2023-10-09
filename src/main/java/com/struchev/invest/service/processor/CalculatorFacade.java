package com.struchev.invest.service.processor;

import com.struchev.invest.entity.CandleDomainEntity;
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
