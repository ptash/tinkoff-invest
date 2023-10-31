package com.struchev.invest.service.processor;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.entity.OrderDetails;
import com.struchev.invest.entity.OrderDomainEntity;
import com.struchev.invest.service.candle.CandleHistoryService;
import com.struchev.invest.service.notification.NotificationForShortService;
import com.struchev.invest.service.notification.NotificationService;
import com.struchev.invest.service.order.OrderForShortService;
import com.struchev.invest.service.order.OrderService;
import com.struchev.invest.strategy.AStrategy;
import com.struchev.invest.strategy.StrategySelector;
import com.struchev.invest.strategy.instrument_by_fiat_cross.AInstrumentByFiatCrossStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервис обрабатывает поток свечей
 * Принимает решение с помощью калькулятора о покупке/продаже инструмента в рамках включенных
 * Отправляет ордеры на исполнение
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PurchaseService {
    private final OrderService orderService;
    private final StrategySelector strategySelector;
    private final NotificationService notificationService;
    private final NotificationForShortService notificationForShortService;
    private final CalculatorFacade calculator;
    private final CalculatorInstrumentByInstrumentService calculatorInstrumentByInstrumentService;
    private final FactorialInstrumentByFiatService factorialInstrumentByFiatService;

    private final CrossInstrumentByFiatService crossInstrumentByFiatService;

    private final CandleHistoryService candleHistoryService;

    /**
     * Обработчик новой свечи используя оба типа стратегий
     * Не выбрасывает исключения, логирует ошибку
     *
     * @param candleDomainEntity
     */
    public void observeNewCandleNoThrow(CandleDomainEntity candleDomainEntity) {
        try {
            this.observeNewCandle(candleDomainEntity);
        } catch (Exception e) {
            var msg = String.format("An error during observe new candle %s, %s", candleDomainEntity, e.getMessage());
            log.error(msg, e);
        }
    }

    public void observeNewCandle(CandleDomainEntity candleDomainEntity) {
        log.debug("Observe candle event: {}", candleDomainEntity);
        var strategies = strategySelector.suitableByFigi(candleDomainEntity.getFigi(), null, candleDomainEntity.getInterval());
        strategies.parallelStream().forEach(strategy -> {
            try {
                // Ищем открытый ордер
                // Для стратегии instrumentByInstrument нужен ордер по инструменту свечки (торгуется стратегия в разрезе инструмента)
                // Для стратегии instrumentByInstrument нужен ордер по любому инструменту (торгуется вся стратегия целиком)
                var figiSuitableForOrder = strategy.getType() == AStrategy.Type.instrumentByInstrument ? null : candleDomainEntity.getFigi();
                var order = orderService.findAnyActiveOrderDomainByFigiAndStrategy(figiSuitableForOrder, strategy);

                if (order == null && strategy.isArchive()) {
                    return;
                }

                if (strategy instanceof AInstrumentByFiatCrossStrategy) {
                    strategy = ((AInstrumentByFiatCrossStrategy) strategy).getFigiStrategy(candleDomainEntity.getFigi());
                }

                // Нет активного ордера, возможно можем купить, если нет ограничений по задержке после stop loss
                if (order == null) {

                    var isShouldBuy = calculator.isShouldBuy(strategy, candleDomainEntity);
                    var isShouldBuyShort = calculator.isShouldBuyShort(strategy, candleDomainEntity);
                    var isTrendBuyShort = calculator.isTrendBuyShort(strategy, candleDomainEntity);
                    if (isShouldBuy && !isShouldBuyShort && !isTrendBuyShort) {
                        OrderDomainEntity lastOrder = null;
                        var finishedOrders = orderService.findClosedByFigiAndStrategy(candleDomainEntity.getFigi(), strategy);
                        if (finishedOrders.size() > 0) {
                            lastOrder = finishedOrders.get(finishedOrders.size() - 1);
                        }
                        if (strategy.getDelayBySL() != null
                                && lastOrder != null
                                && lastOrder.getSellProfit() != null
                                && lastOrder.getSellProfit().compareTo(BigDecimal.ZERO) < 0) {
                            var length = strategy.getDelayBySL().getSeconds() / 60;
                            var candles = candleHistoryService.getCandlesByFigiAndIntervalAndBeforeDateTimeLimit(
                                    candleDomainEntity.getFigi(),
                                    candleDomainEntity.getDateTime(),
                                    Long.valueOf(length).intValue(),
                                    strategy.getInterval()
                            );
                            if (candles.size() < length) {
                                log.warn("Buy cancel by DelayBySL {} {}: getCandlesByFigiByLength return null for length {}", strategy.getName(), candleDomainEntity.getFigi(), length);
                            }
                            if (lastOrder.getSellDateTime().isAfter(candles.get(0).getDateTime())) {
                                log.info("Buy cancel by DelayBySL {} {}: {} isAfter {}",
                                        strategy.getName(),
                                        candleDomainEntity.getFigi(),
                                        lastOrder.getSellDateTime(),
                                        candles.get(0).getDateTime()
                                );
                                return;
                            }
                        }

                        if (strategy.getDelayPlusBySL() != null
                                && lastOrder != null
                                && lastOrder.getSellProfit() != null
                                && lastOrder.getSellPrice() != null
                                && lastOrder.getSellProfit().compareTo(BigDecimal.ZERO) < 0
                        ) {
                            log.info("Buy by DelayPlusBySL {} {} {} - {}",
                                    strategy.getName(),
                                    candleDomainEntity.getFigi(),
                                    candleDomainEntity.getDateTime(),
                                    strategy.getDelayPlusBySL()
                            );
                            var candles = candleHistoryService.getCandlesByFigiByLength(
                                    candleDomainEntity.getFigi(),
                                    candleDomainEntity.getDateTime(),
                                    strategy.getDelayPlusBySL(),
                                    strategy.getInterval()
                            );
                            if (candles == null) {
                                log.info("Buy cancel by DelayPlusBySL {} {}: getCandlesByFigiByLength return null", lastOrder.getSellDateTime(), candles.get(0).getDateTime());
                                return;
                            }
                            if (lastOrder.getSellDateTime().isAfter(candles.get(0).getDateTime())
                                    && candleDomainEntity.getClosingPrice().compareTo(lastOrder.getSellPrice().subtract(
                                    lastOrder.getPurchasePrice().subtract(lastOrder.getSellPrice()).multiply(BigDecimal.valueOf(strategy.getDelayPlusBySLFactor()))
                            )) >= 0
                            ) {
                                log.info("Buy cancel by DelayPlusBySL {} {} {} - {}: {} = {} isAfter {}; {} = {} >= {} ({} {} {})",
                                        strategy.getName(),
                                        candleDomainEntity.getFigi(),
                                        candleDomainEntity.getDateTime(),
                                        strategy.getDelayPlusBySL(),
                                        lastOrder.getSellDateTime().isAfter(candles.get(0).getDateTime()),
                                        lastOrder.getSellDateTime(),
                                        candles.get(0).getDateTime(),
                                        candleDomainEntity.getClosingPrice().compareTo(lastOrder.getSellPrice().subtract(
                                                lastOrder.getPurchasePrice().subtract(lastOrder.getSellPrice()).multiply(BigDecimal.valueOf(strategy.getDelayPlusBySLFactor())))),
                                        candleDomainEntity.getClosingPrice(),
                                        lastOrder.getSellPrice().subtract(
                                                lastOrder.getPurchasePrice().subtract(lastOrder.getSellPrice()).multiply(BigDecimal.valueOf(strategy.getDelayPlusBySLFactor()))),
                                        lastOrder.getSellPrice(),
                                        lastOrder.getPurchasePrice(),
                                        strategy.getDelayPlusBySLFactor()

                                );
                                return;
                            }
                        }

                        order = orderService.openOrder(candleDomainEntity, strategy, buildOrderDetails(strategy, candleDomainEntity));
                        notificationService.sendBuyInfo(strategy, order, candleDomainEntity);
                    }

                    if (isShouldBuyShort && !isShouldBuy) {
                        var isTrendBuy = calculator.isTrendBuy(strategy, candleDomainEntity);
                        if (!isTrendBuy && !isShouldBuy) {
                            order = orderService.openOrderShort(candleDomainEntity, strategy, buildOrderShortDetails(strategy, candleDomainEntity));
                            notificationForShortService.sendSellInfo(strategy, order, candleDomainEntity);
                        }
                    }
                    if (null != order) {
                        //order = orderService.openLimitOrder(order, strategy);
                        notificationService.sendSellLimitInfo(strategy, order, candleDomainEntity);
                    }
                    return;
                }

                // Ордер есть, но пришла свечка с датой до покупки текущего ордера, так не должно быть
                if (
                        (!order.isShort() && order.getPurchaseDateTime().isAfter(candleDomainEntity.getDateTime()))
                        || ((order.isShort() && order.getSellDateTime().isAfter(candleDomainEntity.getDateTime())))
                ) {
                    log.error("Was founded order before current candle date time: {}, {}", order, candleDomainEntity);
                    return;
                }

                // Ордер есть, возможно можем продать
                //var isTrendBuy = calculator.isTrendBuy(strategy, candleDomainEntity);
                //var isTrendBuyShort = calculator.isTrendBuyShort(strategy, candleDomainEntity);
                var isSell = false;
                if (!order.isShort()) {
                    var isShouldSell = calculator.isShouldSell(strategy, candleDomainEntity, order.getPurchasePrice());
                    var isShouldBuyShort = calculator.isShouldBuyShort(strategy, candleDomainEntity);
                    if (
                            isShouldSell
                            || ((((isShouldBuyShort || calculator.isTrendBuyShort(strategy, candleDomainEntity))
                                    && isOrderNeedSell(order, candleDomainEntity)
                            )
                                    || (isShouldBuyShort && calculator.isTrendSell(strategy, candleDomainEntity))
                            )
                            && !calculator.isTrendBuy(strategy, candleDomainEntity))
                    ) {
                        order = orderService.closeOrder(candleDomainEntity, strategy);
                        notificationService.sendSellInfo(strategy, order, candleDomainEntity);
                        isSell = true;
                        if (isShouldBuyShort) {
                            order = orderService.openOrderShort(candleDomainEntity, strategy, buildOrderShortDetails(strategy, candleDomainEntity));
                            notificationForShortService.sendSellInfo(strategy, order, candleDomainEntity);
                            isSell = false;
                        }
                    }
                } else if (order.isShort()) {
                    var isShouldSellShort = calculator.isShouldSellShort(strategy, candleDomainEntity, order.getSellPrice());
                    var isShouldBuy = calculator.isShouldBuy(strategy, candleDomainEntity);
                    if (
                            isShouldSellShort
                            || ((((isShouldBuy || calculator.isTrendBuy(strategy, candleDomainEntity))
                                    && calculator.isOrderNeedSell(order, candleDomainEntity)
                            )
                                    || (isShouldBuy && calculator.isTrendSellShort(strategy, candleDomainEntity))
                            )
                            && !calculator.isTrendBuyShort(strategy, candleDomainEntity))
                    ) {
                        order = orderService.closeOrderShort(candleDomainEntity, strategy);
                        notificationForShortService.sendBuyInfo(strategy, order, candleDomainEntity);
                        isSell = true;
                        if (isShouldBuy) {
                            order = orderService.openOrder(candleDomainEntity, strategy, buildOrderDetails(strategy, candleDomainEntity));
                            notificationService.sendBuyInfo(strategy, order, candleDomainEntity);
                            isSell = false;
                        }
                    }
                }
                if (!isSell) {
                    var orderId = order.getSellOrderId();
                    order = orderService.openLimitOrder(order, strategy, candleDomainEntity);
                    if (orderId != order.getSellOrderId() || order.getSellDateTime() != null) {
                        notificationService.sendSellLimitInfo(strategy, order, candleDomainEntity);
                    }
                }
            } catch (RuntimeException e) {
                log.info("error in observeNewCandle " + strategy.getName(), e);
                throw e;
            }
        });
    }

    private Boolean isOrderNeedSell(OrderDomainEntity order, CandleDomainEntity candleDomainEntity)
    {
        return calculator.isOrderNeedSell(order, candleDomainEntity);
    }

    private OrderDetails buildOrderDetails(AStrategy strategy, CandleDomainEntity candleDomainEntity)
    {
        var currentPrices = (strategy.getType() == AStrategy.Type.instrumentByInstrument)
                ? calculatorInstrumentByInstrumentService.getCurrentPrices() : null;
        if (strategy.getType() == AStrategy.Type.instrumentCrossByFiat) {
            currentPrices = crossInstrumentByFiatService.getCurrentPrices();
        }
        if (currentPrices != null) {
            AStrategy finalStrategy = strategy;
            currentPrices = currentPrices.entrySet().stream()
                    .filter(e -> finalStrategy.getFigies().containsKey(e.getKey()))
                    .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
        }
        Map<String, Boolean> booleanDataMap = null;
        if (strategy.getType() == AStrategy.Type.instrumentFactorialByFiat) {
            booleanDataMap = factorialInstrumentByFiatService.getOrderBooleanDataMap(strategy, candleDomainEntity);
            currentPrices = factorialInstrumentByFiatService.getOrderBigDecimalDataMap(strategy, candleDomainEntity);
        }
        return OrderDetails.builder()
                .currentPrices(currentPrices)
                .booleanDataMap(booleanDataMap)
                .build();
    }

    private OrderDetails buildOrderShortDetails(AStrategy strategy, CandleDomainEntity candleDomainEntity)
    {
        Map<String, BigDecimal> currentPrices = null;
        var strategyShort = calculator.getStrategyShort(strategy);
        var c = calculator.getCalculatorServiceShort(strategy);
        if (c instanceof ICalculatorPriceDetailsService) {
            currentPrices = ((ICalculatorPriceDetailsService) c).getCurrentPrices();
        }

        if (currentPrices != null) {
            AStrategy finalStrategy = strategyShort;
            currentPrices = currentPrices.entrySet().stream()
                    .filter(e -> finalStrategy.getFigies().containsKey(e.getKey()))
                    .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
        }
        Map<String, Boolean> booleanDataMap = null;
        if (c instanceof ICalculatorDetailsService) {
            booleanDataMap = ((ICalculatorDetailsService) c).getOrderBooleanDataMap(strategyShort, candleDomainEntity);
            currentPrices = ((ICalculatorDetailsService) c).getOrderBigDecimalDataMap(strategyShort, candleDomainEntity);
        }
        return OrderDetails.builder()
                .currentPrices(currentPrices)
                .booleanDataMap(booleanDataMap)
                .build();
    }
}
