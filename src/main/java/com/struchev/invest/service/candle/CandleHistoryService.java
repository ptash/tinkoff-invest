package com.struchev.invest.service.candle;

import com.google.protobuf.Internal;
import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.repository.CandleRepository;
import com.struchev.invest.service.tinkoff.ITinkoffCommonAPI;
import com.struchev.invest.strategy.AStrategy;
import com.struchev.invest.strategy.StrategySelector;
import com.struchev.invest.strategy.instrument_by_fiat.AInstrumentByFiatStrategy;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tinkoff.piapi.contract.v1.CandleInterval;
import ru.tinkoff.piapi.contract.v1.HistoricCandle;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * Service to load and store actual history of candles
 */
@Service
@Slf4j
@DependsOn({"instrumentService"})
@RequiredArgsConstructor
public class CandleHistoryService {
    private final ITinkoffCommonAPI tinkoffCommonAPI;
    private final StrategySelector strategySelector;
    private final CandleRepository candleRepository;
    private final EntityManager entityManager;

    private Map<String, OffsetDateTime> firstCandleDateTimeByFigi;
    private Map<String, List<CandleDomainEntity>> candlesLocalCacheMinute;
    private Map<String, List<CandleDomainEntity>> candlesLocalCacheDay;

    @Value("${candle.history.duration}")
    private Duration historyDuration;
    @Value("${candle.listener.enabled}")
    Boolean isCandleListenerEnabled;

    @Transactional
    public CandleDomainEntity addOrReplaceCandles(HistoricCandle newCandle, String figi, String interval) {
        try {
            var dateTime = ConvertorUtils.toOffsetDateTime(newCandle.getTime().getSeconds());
            var closingProse = ConvertorUtils.toBigDecimal(newCandle.getClose(), null);
            var openPrice = ConvertorUtils.toBigDecimal(newCandle.getOpen(), null);
            var lowestPrice = ConvertorUtils.toBigDecimal(newCandle.getLow(), null);
            var highestPrice = ConvertorUtils.toBigDecimal(newCandle.getHigh(), null);

            log.trace("Candle for {} by {} with openPrice {}, closingProse {}", figi, dateTime, openPrice, closingProse);
            var candleDomainEntity = candleRepository.findByFigiAndIntervalAndDateTime(figi, interval, dateTime);
            if (candleDomainEntity != null) {
                if (closingProse.compareTo(candleDomainEntity.getClosingPrice()) != 0
                        || highestPrice.compareTo(candleDomainEntity.getHighestPrice()) != 0
                        || openPrice.compareTo(candleDomainEntity.getOpenPrice()) != 0
                        || lowestPrice.compareTo(candleDomainEntity.getLowestPrice()) != 0) {
                    log.trace("Replaced candle {} to candle {}:", candleDomainEntity, newCandle);
                    candleDomainEntity.setClosingPrice(closingProse);
                    candleDomainEntity.setHighestPrice(highestPrice);
                    candleDomainEntity.setLowestPrice(lowestPrice);
                    candleDomainEntity.setOpenPrice(openPrice);
                    candleDomainEntity = candleRepository.save(candleDomainEntity);
                }
                return candleDomainEntity;
            }
            candleDomainEntity = CandleDomainEntity.builder()
                    .figi(figi)
                    .closingPrice(closingProse)
                    .highestPrice(highestPrice)
                    .lowestPrice(lowestPrice)
                    .openPrice(openPrice)
                    .interval(interval)
                    .dateTime(dateTime)
                    .build();
            candleDomainEntity = candleRepository.save(candleDomainEntity);
            log.trace("Add new candle {}", candleDomainEntity);
            return candleDomainEntity;
        } catch (Exception e) {
            log.error("Can't add candle", e);
            throw e;
        }
    }

    public List<CandleDomainEntity> getCandlesByFigiBetweenDateTimes(String figi, OffsetDateTime startDateTime, OffsetDateTime endDateTime, String interval) {
        // если слушатель выключен - значит запускаем не в продакшн режиме, можем хранить свечки в памяти, а не на диске (БД)
        if (!isCandleListenerEnabled) {
            var candlesByFigi = interval.equals("1min") ? candlesLocalCacheMinute.get(figi) : candlesLocalCacheDay.get(figi);
            if (candlesByFigi.size() == 0) {
                throw new RuntimeException("Candles not found in local cache for " + figi);
            }
            var candles = candlesByFigi.stream()
                    .filter(c -> c.getDateTime().isAfter(startDateTime))
                    .filter(c -> c.getDateTime().isBefore(endDateTime) || c.getDateTime().isEqual(endDateTime))
                    .collect(Collectors.toList());
            return candles;
        }

        var candles = candleRepository.findByFigiAndIntervalAndBetweenDateTimes(figi,
                interval, startDateTime, endDateTime);
        return candles;
    }

    public List<CandleDomainEntity> getCandlesByFigiAndIntervalAndBeforeDateTimeLimit(String figi, OffsetDateTime dateTime, Integer length, String interval) {
        // если слушатель выключен - значит запускаем не в продакшн режиме, можем хранить свечки в памяти, а не на диске (БД)
        if (!isCandleListenerEnabled) {
            var candlesByFigi = interval.equals("1min") ? candlesLocalCacheMinute.get(figi) : candlesLocalCacheDay.get(figi);
            if (candlesByFigi.size() == 0) {
                throw new RuntimeException("Candles not found in local cache for " + figi);
            }

            var startDateTime = dateTime.minusDays(15).minusMinutes(interval.equals("1min") ? length : length * 60 * 24);
            var candles = candlesByFigi.stream()
                    .filter(c -> c.getDateTime().isAfter(startDateTime))
                    .filter(c -> c.getDateTime().isBefore(dateTime) || c.getDateTime().isEqual(dateTime))
                    .collect(Collectors.toList());
            candles.sort(Comparator.comparing(CandleDomainEntity::getDateTime));
            return candles.subList(Math.max(0, candles.size() - length), candles.size());
        }

        Pageable top = PageRequest.of(0, length);
        var candles = candleRepository.findByFigiAndIntervalAndBeforeDateTimeLimit(figi,
                interval, dateTime, top);
        Collections.reverse(candles);
        return candles;
    }

    /**
     * Request candles by api, try several times with 10s pause in case of error
     *
     * @param figi
     * @param start
     * @param end
     * @param resolution
     * @param tries
     * @return
     */
    @SneakyThrows
    private List<HistoricCandle> requestCandles(String figi, OffsetDateTime start, OffsetDateTime end, CandleInterval resolution, int tries) {
        try {
            var candles = tinkoffCommonAPI.getApi().getMarketDataService().getCandles(figi,
                    start.toInstant(), end.toInstant(), resolution).get();
            return candles;
        } catch (Exception e) {
            log.error("Can't get candles for figi {}", e);
            Thread.sleep(10000);
            return requestCandles(figi, start, end, resolution, tries--);
        }
    }

    /**
     * Request candle history by API and save it to DB
     *
     * @param days
     * @return
     */
    @SneakyThrows
    public void requestCandlesHistoryForDays(long days) {
        if (days == 0) {
            return;
        }

        var now = OffsetDateTime.now();
        var figies = strategySelector.getFigiesForActiveStrategies();
        log.info("Start to load history {} days, for figies {}", days, figies);
        figies.stream().forEach(figi -> {
            loadCandlesHistory(figi, days, CandleInterval.CANDLE_INTERVAL_1_MIN, now);
        });

        figies.stream().forEach(figi -> {
            loadCandlesHistory(figi, days, CandleInterval.CANDLE_INTERVAL_DAY, now);
        });
        log.info("Loaded to load history for {} days", days);
    }

    private void loadCandlesHistory(String figi, Long days, CandleInterval candleInterval, OffsetDateTime now)
    {
        String interval = candleInterval == CandleInterval.CANDLE_INTERVAL_1_MIN ? "1min": "1day";
        Integer minutesInInterval = candleInterval == CandleInterval.CANDLE_INTERVAL_1_MIN ? 60 * 24: 60 * 24 * 365;
        var candlesMax = candleRepository.findFirstByFigiAndIntervalOrderByDateTimeDesc(figi, interval);
        var candlesMin = candleRepository.findFirstByFigiAndIntervalOrderByDateTimeAsc(figi, interval);

        var dayStart = 0;
        var dayEnd = candleInterval == CandleInterval.CANDLE_INTERVAL_1_MIN ? days : Long.max(Long.divideUnsigned(days, 365), 1);
        log.info("Candles history from {} to {} {}", dayStart, dayEnd, interval);
        LongStream datesRange = LongStream.empty();
        var timeStart = now.minusMinutes((dayEnd) * minutesInInterval);
        var timeEnd = now.minusMinutes(0);
        if (candlesMax != null && candlesMin != null) {
            log.info("History {} candles {} has period from {} to {}. We need period from {} to {}", interval, figi, candlesMin.getDateTime(), candlesMax.getDateTime(), timeStart, timeEnd);
            if (candlesMin.getDateTime().isAfter(timeStart) && candlesMin.getDateTime().isBefore(timeEnd)) {
                var dayEndChanged = dayStart + Duration.between(timeStart, candlesMin.getDateTime()).toMinutes() / minutesInInterval  + 1;
                datesRange = LongStream.concat(datesRange, LongStream.range(dayStart, dayEndChanged));
                log.info("History start {} candles {} from {} to {} already loaded. Load from {} to {}", interval, figi, timeStart, candlesMin.getDateTime(), dayStart, dayEndChanged);
            }
            if (candlesMax.getDateTime().isAfter(timeStart) && candlesMax.getDateTime().isBefore(timeEnd)) {
                var dayStartChanged = dayEnd - (Duration.between(candlesMax.getDateTime(), timeEnd).toMinutes() / minutesInInterval) - 1;
                datesRange = LongStream.concat(datesRange, LongStream.range(dayStartChanged, dayEnd));
                log.info("History end {} candles {} from {} to {} already loaded. Load from {} to {}", interval, figi, candlesMax.getDateTime(), timeEnd, dayStartChanged, dayEnd);
            }
            if (!candlesMin.getDateTime().isBefore(timeEnd) || !candlesMax.getDateTime().isAfter(timeStart)) {
                datesRange = LongStream.range(dayStart, dayEnd);
            }
        } else {
            datesRange = LongStream.range(dayStart, dayEnd);
        }
        //Supplier<LongStream> datesRangeSupplier = ()->datesRange.mapToObj ();
        //log.info("Request {} candles {} for {} days", interval, figi, datesRange.count());
        datesRange.forEach(i -> {
            var start = now.minusMinutes((i + 1) * minutesInInterval);
            var end = now.minusMinutes(i * minutesInInterval);
            log.info("Request {} candles {}, {} day from {} to {}", interval, figi, i, start, end);
            var candles = requestCandles(figi, start, end, candleInterval, 12);
            log.info("Save {} {} candles to DB {}, {} day", interval, candles.size(), figi, i);
            candles.forEach(c -> addOrReplaceCandles(c, figi, interval));
            log.info("Candles {} was saved to DB {}, {} day", interval, figi, i);
        });
    }

    public OffsetDateTime getFirstCandleDateTime(String figi) {
        var firstCandleDateTime = firstCandleDateTimeByFigi.get(figi);
        if (firstCandleDateTime == null) {
            var firstCandle = candleRepository.findByFigiAndIntervalOrderByDateTime(figi, "1min").get(0);
            firstCandleDateTime = firstCandle.getDateTime();
            firstCandleDateTimeByFigi.put(figi, firstCandleDateTime);
        }
        return firstCandleDateTime;
    }

    @PostConstruct
    void init() {
        firstCandleDateTimeByFigi = new ConcurrentHashMap<>();
        var historyDurationByStrategies = strategySelector.getActiveStrategies().stream()
                .filter(s -> s.getType() == AStrategy.Type.instrumentByFiat)
                .map(s -> s.getHistoryDuration())
                .reduce((d1, d2) -> d1.toSeconds() > d2.toSeconds() ? d1 : d2)
                .stream().findFirst().orElse(Duration.ZERO);
        requestCandlesHistoryForDays(historyDurationByStrategies.toSeconds() > historyDuration.toSeconds()
                ? historyDurationByStrategies.toDays() : historyDuration.toDays());

        if (!isCandleListenerEnabled) {
            var candles = candleRepository.findByIntervalOrderByDateTime("1min");
            candlesLocalCacheMinute = candles.stream()
                    .peek(c -> entityManager.detach(c))
                    .collect(Collectors.groupingBy(CandleDomainEntity::getFigi));

            var candlesDay = candleRepository.findByIntervalOrderByDateTime("1day");
            candlesLocalCacheDay = candlesDay.stream()
                    .peek(c -> entityManager.detach(c))
                    .collect(Collectors.groupingBy(CandleDomainEntity::getFigi));
        }
    }
}
