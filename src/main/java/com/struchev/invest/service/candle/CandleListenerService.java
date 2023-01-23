package com.struchev.invest.service.candle;

import com.struchev.invest.expression.Date;
import com.struchev.invest.service.notification.NotificationService;
import com.struchev.invest.service.processor.PurchaseService;
import com.struchev.invest.service.tinkoff.ITinkoffCommonAPI;
import com.struchev.invest.strategy.StrategySelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.CandleInterval;
import ru.tinkoff.piapi.contract.v1.HistoricCandle;
import ru.tinkoff.piapi.contract.v1.SubscriptionInterval;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.ArrayList;

/**
 * Service to observe candles
 */
@Service
@RequiredArgsConstructor
@DependsOn({"candleHistoryService"})
@ConditionalOnProperty(name = "candle.listener.enabled", havingValue = "true")
@Slf4j
public class CandleListenerService {
    private final CandleHistoryService candleHistoryService;
    private final PurchaseService purchaseService;
    private final StrategySelector strategySelector;
    private final ITinkoffCommonAPI tinkoffCommonAPI;
    private final NotificationService notificationService;

    private void startToListen(int number) {
        var figies = strategySelector.getFigiesForActiveStrategies();
        var interval = "1min";
        notificationService.sendMessageAndLog("Listening candle events..");
        try {
            tinkoffCommonAPI.getApi().getMarketDataStreamService()
                    .newStream("candles_stream", item -> {
                        log.trace("New data in streaming api: {}", item);
                        if (item.hasCandle()) {
                            var candle = HistoricCandle.newBuilder();
                            candle.setClose(item.getCandle().getClose());
                            candle.setOpen(item.getCandle().getOpen());
                            candle.setHigh(item.getCandle().getHigh());
                            candle.setLow(item.getCandle().getLow());
                            candle.setTime(item.getCandle().getTime());
                            var candleDomainEntity = candleHistoryService.addOrReplaceCandles(candle.build(), item.getCandle().getFigi(), interval);

                            var candleHour = candleHistoryService.getCandlesByFigiByLength(candleDomainEntity.getFigi(), candleDomainEntity.getDateTime(), 1, "1hour");
                            var maxCandleHourDate = Date.formatDateTimeToHour(candleHour.get(0).getDateTime());
                            if (!maxCandleHourDate.equals(Date.formatDateTimeToHour(candleDomainEntity.getDateTime()))) {
                                log.info("Need 1hour candle {} != {}", maxCandleHourDate, Date.formatDateTimeToHour(candleDomainEntity.getDateTime()));
                                candleHistoryService.loadCandlesHistory(candleDomainEntity.getFigi(), 1L, CandleInterval.CANDLE_INTERVAL_HOUR, OffsetDateTime.now());
                            }
                            purchaseService.observeNewCandleNoThrow(candleDomainEntity);
                        }
                    }, e -> {
                        log.error("An error in candles_stream " + interval + " , listener will be restarted", e);
                        startToListen(number + 1);
                    })
                    .subscribeCandles(new ArrayList<>(figies));
        } catch (Throwable th) {
            log.error("An error in subscriber, listener " + interval + " will be restarted", th);
            startToListen(number + 1);
            throw th;
        }
    }

    @PostConstruct
    void init() {
        new Thread(() -> {
            startToListen(1);
        }, "event-listener").start();
    }
}
