package com.struchev.invest.service.processor;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.service.notification.NotificationService;
import com.struchev.invest.service.order.OrderService;
import com.struchev.invest.strategy.AStrategy;
import com.struchev.invest.strategy.StrategySelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

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

            // Нет активного ордера, возможно можем купить, если нет ограничений по задержке после stop loss
            if (order == null) {
                var isShouldBuy = calculator.isShouldBuy(strategy, candleDomainEntity);
                if (isShouldBuy) {
                    if (strategy.getDelayBySL() != null) {
                        var finishedOrders = orderService.findClosedByFigiAndStrategy(candleDomainEntity.getFigi(), strategy);
                        if (finishedOrders.size() > 0) {
                            var lastOrder = finishedOrders.get(finishedOrders.size() - 1);
                            if (lastOrder.getSellProfit().compareTo(BigDecimal.ZERO) < 0 && ChronoUnit.SECONDS.between(lastOrder.getSellDateTime(), candleDomainEntity.getDateTime()) < strategy.getDelayBySL().getSeconds()) {
                                return;
                            }
                        }
                    }

                    if (strategy.getDelayPlusBySL() != null) {
                        var finishedOrders = orderService.findClosedByFigiAndStrategy(candleDomainEntity.getFigi(), strategy);
                        if (finishedOrders.size() > 0) {
                            var lastOrder = finishedOrders.get(finishedOrders.size() - 1);
                            if (lastOrder.getSellProfit().compareTo(BigDecimal.ZERO) < 0
                                    && ChronoUnit.SECONDS.between(lastOrder.getSellDateTime(), candleDomainEntity.getDateTime()) < strategy.getDelayPlusBySL().getSeconds()
                                    && candleDomainEntity.getClosingPrice().compareTo(lastOrder.getSellPrice()) >= 0
                            ) {
                                return;
                            }
                        }
                    }

                    var currentPrices = strategy.getType() == AStrategy.Type.instrumentByInstrument
                            ? calculatorInstrumentByInstrumentService.getCurrentPrices() : null;
                    order = orderService.openOrder(candleDomainEntity, strategy, currentPrices);

                    notificationService.sendBuyInfo(strategy, order, candleDomainEntity);
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
            }
        });
    }
}
