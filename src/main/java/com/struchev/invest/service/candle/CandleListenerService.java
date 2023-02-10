package com.struchev.invest.service.candle;

import com.struchev.invest.expression.Date;
import com.struchev.invest.service.notification.NotificationService;
import com.struchev.invest.service.processor.FactorialInstrumentByFiatService;
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
import java.util.LinkedHashMap;
import java.util.Map;

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
        notificationService.sendMessageAndLog("Listening candle events... " + number);
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

                            var candleHour = candleHistoryService.getCandlesByFigiByLength(candleDomainEntity.getFigi(), candleDomainEntity.getDateTime(), 2,
                                    "1hour");
                            var maxCandleHourDate = Date.formatDateTimeToHour(candleHour.get(1).getDateTime());
                            var curCandleHourExpect = Date.formatDateTimeToHour(candleDomainEntity.getDateTime());
                            if (!maxCandleHourDate.equals(curCandleHourExpect) || !candleHour.get(0).getIsComplete()) {
                                String key = candleDomainEntity.getFigi() + curCandleHourExpect;
                                Integer keyValue = 0;
                                synchronized (loadCandlesHistory) {
                                    if (!loadCandlesHistory.containsValue(key)) {
                                        keyValue = 1;
                                    } else {
                                        keyValue = loadCandlesHistory.get(key) + 1;
                                    }
                                    loadCandlesHistory.put(key, keyValue);
                                }
                                if (keyValue < 10) {
                                    log.info("Need 1hour candle {} != {}: {} = {}", maxCandleHourDate, curCandleHourExpect, key, keyValue);
                                    candleHistoryService.loadCandlesHistory(candleDomainEntity.getFigi(), 1L, CandleInterval.CANDLE_INTERVAL_HOUR, OffsetDateTime.now());
                                } else {
                                    log.info("No Need 1hour candle {} != {}: {} = {}", maxCandleHourDate, curCandleHourExpect, key, keyValue);
                                }
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

    private LinkedHashMap<String, Integer> loadCandlesHistory = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 1000;
        }
    };

    @PostConstruct
    void init() {
        new Thread(() -> {
            startToListen(1);
        }, "event-listener").start();
    }
}
