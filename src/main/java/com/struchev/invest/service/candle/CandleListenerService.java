package com.struchev.invest.service.candle;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.expression.Date;
import com.struchev.invest.repository.CandleRepository;
import com.struchev.invest.service.notification.NotificationService;
import com.struchev.invest.service.processor.FactorialInstrumentByFiatService;
import com.struchev.invest.service.processor.PurchaseService;
import com.struchev.invest.service.tinkoff.ITinkoffCommonAPI;
import com.struchev.invest.strategy.StrategySelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.CandleInterval;
import ru.tinkoff.piapi.contract.v1.HistoricCandle;
import ru.tinkoff.piapi.contract.v1.SubscriptionInterval;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final CandleRepository candleRepository;

    private void startToListen(int number) {
        var figies = strategySelector.getFigiesForActiveStrategies();
        var interval = "1min";
        var strategies = strategySelector.getFigiesForActiveStrategies();
        OffsetDateTime dateBefore = OffsetDateTime.now();

        log.info("Init first candle for {} strategies", strategies.size());

        strategies.stream()
                .flatMap(figi -> {
                    var candles = candleRepository.findByFigiAndIntervalAndBeforeDateTimeLimit(figi,
                            interval, dateBefore, PageRequest.of(0, 1));
                    if (candles == null || candles.size() == 0) {
                        log.info("Init first candle cancel for {}: getCandlesByFigiByLength return {}", figi, candles);
                        return new ArrayList<CandleDomainEntity>().stream();
                    }
                    log.info("Init first candle starting for {}: getCandlesByFigiByLength return {} ({})", figi, candles.get(0).getDateTime(), candles.size());
                    return candles.stream();
                })
                .sorted(Comparator.comparing(CandleDomainEntity::getDateTime))
                .forEach(c -> purchaseService.observeNewCandle(c));

        notificationService.sendMessageAndLog("Listening candle events... " + number);
        try {
            tinkoffCommonAPI.getApi().getMarketDataStreamService()
                    .newStream("candles_stream", item -> {
                        log.trace("New data in streaming api: {}", item);
                        CandleDomainEntity candleDomainEntity = null;
                        var isNewCandle = false;
                        if (item.hasCandle()) {
                            var candle = HistoricCandle.newBuilder();
                            candle.setClose(item.getCandle().getClose());
                            candle.setOpen(item.getCandle().getOpen());
                            candle.setHigh(item.getCandle().getHigh());
                            candle.setLow(item.getCandle().getLow());
                            candle.setTime(item.getCandle().getTime());
                            candle.setVolume(item.getCandle().getVolume());
                            candleDomainEntity = candleHistoryService.replaceCandles(candle.build(), item.getCandle().getFigi(), interval);
                            if (candleDomainEntity == null) {
                                candleDomainEntity = candleHistoryService.addCandles(candle.build(), item.getCandle().getFigi(), interval);
                                isNewCandle = true;
                            }

                            var now = OffsetDateTime.now();
                            var curCandleMinuteExpect = Date.formatDateTimeToMinute(now);
                            var curCandleMinuteExpect2 = Date.formatDateTimeToMinute(now.minusMinutes(1));
                            var curCandleMinute = Date.formatDateTimeToMinute(candleDomainEntity.getDateTime());
                            if (!(curCandleMinute.equals(curCandleMinuteExpect) || curCandleMinuteExpect.equals(curCandleMinuteExpect2))) {
                                log.trace("Skip candle {}. Now {}", curCandleMinute, curCandleMinuteExpect);
                                candleDomainEntity = null;
                            }
                        }

                        if (null != candleDomainEntity) {
                            if (isNewCandle) {
                                log.info("Need refresh 1min candles {}", candleDomainEntity.getFigi());
                                candleHistoryService.loadCandlesHistory(candleDomainEntity.getFigi(), 1L, CandleInterval.CANDLE_INTERVAL_1_MIN, OffsetDateTime.now());
                            }
                            var candleHour = candleHistoryService.getAllCandlesByFigiByLength(
                                    candleDomainEntity.getFigi(),
                                    candleDomainEntity.getDateTime(),
                                    2,
                                    "1hour"
                            );
                            var maxCandleHourDate = Date.formatDateTimeToHour(candleHour.get(1).getDateTime());
                            var curCandleHourExpect = Date.formatDateTimeToHour(candleDomainEntity.getDateTime());
                            if (!maxCandleHourDate.equals(curCandleHourExpect) || !candleHour.get(0).getIsComplete()) {
                                String key = candleDomainEntity.getFigi() + curCandleHourExpect;
                                Integer keyValue = 0;
                                synchronized (loadCandlesHistory) {
                                    keyValue = loadCandlesHistory.getOrDefault(key, 0) + 1;
                                    loadCandlesHistory.put(key, keyValue);
                                }
                                if (keyValue < 100) {
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
