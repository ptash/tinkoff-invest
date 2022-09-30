package com.struchev.invest.service.processor;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.entity.OrderDomainEntity;
import com.struchev.invest.service.candle.CandleHistoryService;
import com.struchev.invest.service.notification.NotificationService;
import com.struchev.invest.service.order.OrderService;
import com.struchev.invest.strategy.AStrategy;
import com.struchev.invest.strategy.StrategySelector;
import com.struchev.invest.strategy.instrument_by_fiat_cross.AInstrumentByFiatCrossStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

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
    private final CalculatorFacade calculator;
    private final CalculatorInstrumentByInstrumentService calculatorInstrumentByInstrumentService;

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
            notificationService.sendMessage(msg);
        }
    }

    public void observeNewCandle(CandleDomainEntity candleDomainEntity) {
        log.debug("Observe candle event: {}", candleDomainEntity);
        var strategies = strategySelector.suitableByFigi(candleDomainEntity.getFigi(), null);
        strategies.parallelStream().forEach(strategy -> {
            // Ищем открытый ордер
            // Для стратегии instrumentByInstrument нужен ордер по инструменту свечки (торгуется стратегия в разрезе инструмента)
            // Для стратегии instrumentByInstrument нужен ордер по любому инструменту (торгуется вся стратегия целиком)
            var figiSuitableForOrder = strategy.getType() == AStrategy.Type.instrumentByInstrument ? null : candleDomainEntity.getFigi();
            var order = orderService.findActiveByFigiAndStrategy(figiSuitableForOrder, strategy);

            if (order == null && strategy.isArchive()) {
                return;
            }

            if (strategy instanceof AInstrumentByFiatCrossStrategy) {
                strategy = ((AInstrumentByFiatCrossStrategy)strategy).getFigiStrategy(candleDomainEntity.getFigi());
            }

            // Нет активного ордера, возможно можем купить, если нет ограничений по задержке после stop loss
            if (order == null) {
                var isShouldBuy = calculator.isShouldBuy(strategy, candleDomainEntity);
                if (isShouldBuy) {
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

                    var currentPrices = (strategy.getType() == AStrategy.Type.instrumentByInstrument)
                            ? calculatorInstrumentByInstrumentService.getCurrentPrices() : null;
                    if (strategy.getType() == AStrategy.Type.instrumentCrossByFiat) {
                        currentPrices = crossInstrumentByFiatService.getCurrentPrices();
                    }
                    order = orderService.openOrder(candleDomainEntity, strategy, currentPrices);

                    notificationService.sendBuyInfo(strategy, order, candleDomainEntity);

                    //order = orderService.openLimitOrder(order, strategy);
                    notificationService.sendSellLimitInfo(strategy, order, candleDomainEntity);
                }
                return;
            }

            // Ордер есть, но пришла свечка с датой до покупки текущего ордера, так не должно быть
            if (order.getPurchaseDateTime().isAfter(candleDomainEntity.getDateTime())) {
                log.error("Was founded order before current candle date time: {}, {}", order, candleDomainEntity);
                return;
            }

            // Ордер есть, возможно можем продать
            var isShouldSell = calculator.isShouldSell(strategy, candleDomainEntity, order.getPurchasePrice());
            if (isShouldSell) {
                order = orderService.closeOrder(candleDomainEntity, strategy);
                notificationService.sendSellInfo(strategy, order, candleDomainEntity);
                return;
            } else {
                var orderId = order.getSellOrderId();
                order = orderService.openLimitOrder(order, strategy);
                if (orderId != order.getSellOrderId() || order.getSellDateTime() != null) {
                    notificationService.sendSellLimitInfo(strategy, order, candleDomainEntity);
                }
            }
        });
    }
}
