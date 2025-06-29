package com.struchev.invest.service.candle;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.expression.Date;
import com.struchev.invest.repository.CandleRepository;
import com.struchev.invest.service.notification.NotificationService;
import com.struchev.invest.service.processor.PurchaseService;
import com.struchev.invest.service.tinkoff.ITinkoffCommonAPI;
import com.struchev.invest.strategy.StrategySelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.Candle;
import ru.tinkoff.piapi.contract.v1.CandleInterval;
import ru.tinkoff.piapi.contract.v1.HistoricCandle;
import ru.tinkoff.piapi.contract.v1.SubscriptionInterval;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

    @Value("${invest.streamNumber:2}")
    private Integer streamNumber;

    private void startToListen(int number) {
        startToListen(number, "1min");
        startToListen(number, "5min");
        startToListen(number, "1hour");
    }

    private void startToListen(int number, String interval) {
        var figies = strategySelector.getFigiesForActiveStrategies(interval);
        if (figies.size() < 1) {
            log.info("There are no any strategy for interval {}", interval);
            return;
        }
        var candleInterval = interval.equals("1min") ? CandleInterval.CANDLE_INTERVAL_1_MIN :
                interval.equals("5min") ? CandleInterval.CANDLE_INTERVAL_5_MIN :
                CandleInterval.CANDLE_INTERVAL_HOUR;
        var minInInterval = interval.equals("1min") ? 1 :
                interval.equals("5min") ? 5 :
                60;

        var subscriptionInterval = interval.equals("1min") ? SubscriptionInterval.SUBSCRIPTION_INTERVAL_ONE_MINUTE :
                SubscriptionInterval.SUBSCRIPTION_INTERVAL_FIVE_MINUTES;
        var strategies = strategySelector.getFigiesForActiveStrategies();
        OffsetDateTime dateBefore = OffsetDateTime.now();

        log.info("Init first candle for {} strategies", strategies.size());

        if (Objects.equals(interval, "1min")) {
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
                    .forEach(c -> purchaseService.observeNewCandleNoThrow(c));
        }

        notificationService.sendMessageAndLog("Listening candle " + interval + " events... " + number);
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

                            /*
                            var now = OffsetDateTime.now();
                            var mod = (now.getMinute() % minInInterval);
                            if (mod > 0) {
                                now = now.minusMinutes(mod);
                            }
                            var curCandleMinuteExpectString = Date.formatDateTimeToMinute(now);
                            var curCandleMinuteExpectPrevString = Date.formatDateTimeToMinute(now.minusMinutes(minInInterval));
                            var curCandleMinute = Date.formatDateTimeToMinute(candleDomainEntity.getDateTime());
                            if (
                                    !(curCandleMinute.equals(curCandleMinuteExpectString)
                                    || curCandleMinuteExpectString.equals(curCandleMinuteExpectPrevString))
                            ) {
                                log.warn("Skip candle {} {}. Now {}", item.getCandle().getFigi(), curCandleMinute, curCandleMinuteExpectString);
                                candleDomainEntity = null;
                            }*/
                        }

                        if (null != candleDomainEntity) {
                            //lastCandleObservedStartMap.put(candleDomainEntity.getFigi(), candleDomainEntity);
                            CandleDomainEntity candleDomainEntity5Min = null;
                            CandleDomainEntity candleDomainEntity1Hour = null;
                            if (isNewCandle) {
                                if (interval.equals("1min")) {
                                    log.info("Need refresh 1min candles {}", candleDomainEntity.getFigi());
                                    candleHistoryService.loadCandlesHistory(candleDomainEntity.getFigi(), 1L, CandleInterval.CANDLE_INTERVAL_1_MIN, OffsetDateTime.now());
                                    log.info("Need refresh 5min candles {}", candleDomainEntity.getFigi());
                                    candleHistoryService.loadCandlesHistory(candleDomainEntity.getFigi(), 1L, CandleInterval.CANDLE_INTERVAL_5_MIN, OffsetDateTime.now());
                                    log.info("Need refresh 1hour candles {}", candleDomainEntity.getFigi());
                                    candleHistoryService.loadCandlesHistory(candleDomainEntity.getFigi(), 1L, CandleInterval.CANDLE_INTERVAL_HOUR, OffsetDateTime.now());

                                    var candle5MinList = candleHistoryService.getAllCandlesByFigiByLength(
                                            candleDomainEntity.getFigi(),
                                            candleDomainEntity.getDateTime(),
                                            1,
                                            "5min"
                                    );
                                    if (candle5MinList.size() > 0) {
                                        candleDomainEntity5Min = candle5MinList.get(0);
                                    }
                                    var candle1HourList = candleHistoryService.getAllCandlesByFigiByLength(
                                            candleDomainEntity.getFigi(),
                                            candleDomainEntity.getDateTime(),
                                            1,
                                            "1hour"
                                    );
                                    if (candle1HourList.size() > 0) {
                                        candleDomainEntity1Hour = candle1HourList.get(0);
                                    }
                                } else {
                                    log.info("Need refresh {} candles {}", interval, candleDomainEntity.getFigi());
                                    candleHistoryService.loadCandlesHistory(candleDomainEntity.getFigi(), 1L, candleInterval, OffsetDateTime.now());
                                }
                            }
                            if (interval.equals("1hour")) {
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
                            }
                            setCurrentCandle(candleDomainEntity);
                            //purchaseService.observeNewCandleNoThrow(candleDomainEntity);
                            if (candleDomainEntity5Min != null) {
                                setCurrentCandle(candleDomainEntity5Min);
                                //purchaseService.observeNewCandleNoThrow(candleDomainEntity5Min);
                            }
                            if (candleDomainEntity1Hour != null) {
                                setCurrentCandle(candleDomainEntity1Hour);
                                //purchaseService.observeNewCandleNoThrow(candleDomainEntity1Hour);
                            }
                            //lastCandleObservedEndMap.put(candleDomainEntity.getFigi(), candleDomainEntity);
                            //log.info("lastCandleObserved {} {} version {}", candleDomainEntity.getFigi(), candleDomainEntity.getDateTime(), candleDomainEntity.getVersion());
                        }
                    }, e -> {
                        log.error("An error '" + e.getMessage() + "' in candles_stream " + interval + " , listener will be restarted", e);
                        startToListen(number + 1);
                    })
                    .subscribeCandles(new ArrayList<>(figies), subscriptionInterval);
        } catch (Throwable th) {
            log.error("An error '" + th.getMessage() + "' in subscriber, listener " + interval + " will be restarted", th);
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

    private void observeNewCandle(CandleDomainEntity candle) {
        purchaseService.observeNewCandleNoThrow(candle);
    }

    private void observeCandles() {
        observeCandles("1min");
        observeCandles("5min");
        observeCandles("1hour");
    }

    private void observeCandles(String interval) {
        var figies = strategySelector.getFigiesForActiveStrategies(interval);
        if (figies.size() < 1) {
            return;
        }
        var figiesNumberInStream = figies.size() / streamNumber;
        if ((figies.size() % streamNumber) > 0) {
            figiesNumberInStream++;
        }
        log.info("Starting observe {} candles {} in parallel {} streams with {}...", interval, figies, streamNumber, figiesNumberInStream);
        List<List<String>> streams = new ArrayList<>();
        for (int i = 0; i < streamNumber; i++) {
            if (figies.size() < 1) {
                break;
            }
            streams.add(new ArrayList<>());
            for (int j = 0; j < figiesNumberInStream && figies.size() > 0; j++) {
                var figi = figies.iterator().next();
                streams.get(i).add(figi);
                figies.remove(figi);
            }
        }
        streams.parallelStream().forEach(streamFigies -> {
            notificationService.sendMessageAndLog("Starting observe " + interval + " candles " + streamFigies + " in one parallel stream...");
            while (true) {
                streamFigies.forEach(figi -> {
                    try {
                        var candle = getCurrentCandle(figi, interval);
                        if (null != candle) {
                            log.info("Observe candle {} {} version {}", candle.getFigi(), candle.getDateTime(), candle.getVersion());
                            observeNewCandle(candle);
                        }
                    } catch (Throwable th) {
                        log.error("An error '" + th.getMessage() + "' in observe", th);
                    }
                });
            }
        });
    }

    private synchronized CandleDomainEntity getCurrentCandle(String figi, String interval) {
        if (!currentCandleByFigiAndInterval.containsKey(figi)) {
            return null;
        }
        var byFigi = currentCandleByFigiAndInterval.get(figi);
        var candle = byFigi.getOrDefault(interval, null);
        byFigi.put(interval, null);
        return candle;
    }

    private synchronized void setCurrentCandle(CandleDomainEntity candle) {
        if (!currentCandleByFigiAndInterval.containsKey(candle.getFigi())) {
            currentCandleByFigiAndInterval.put(candle.getFigi(), new HashMap<>());
        }
        var byFigi = currentCandleByFigiAndInterval.get(candle.getFigi());
        var candlePrev = byFigi.getOrDefault(candle.getInterval(), null);
        if (null != candlePrev) {
            log.warn("Skip observe candle {} {} version {}. New candle {} version {}", candle.getFigi(), candlePrev.getDateTime(), candlePrev.getVersion(), candle.getDateTime(), candle.getVersion());
        }
        byFigi.put(candle.getInterval(), candle);
    }

    private Map<String, Map<String, CandleDomainEntity>> currentCandleByFigiAndInterval = new HashMap<>();

    @PostConstruct
    void init() {
        new Thread(() -> {
            startToListen(1);
        }, "event-listener").start();

        new Thread(this::observeCandles, "candle-observe").start();
    }
}
