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
    private Map<String, List<CandleDomainEntity>> candlesLocalCacheHour;

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
                    candleDomainEntity.setVolume(newCandle.getVolume());
                    candleDomainEntity.setIsComplete(newCandle.getIsComplete());
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
                    .volume(newCandle.getVolume())
                    .isComplete(newCandle.getIsComplete())
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
            var candlesByFigi = interval.equals("1min") ? candlesLocalCacheMinute.get(figi)
                    : (interval.equals("1hour") ? candlesLocalCacheHour.get(figi) : candlesLocalCacheDay.get(figi));
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

    public List<CandleDomainEntity> getCandlesByFigiByLength(String figi, OffsetDateTime currentDateTime, Integer length, String interval)
    {
        if (interval.equals("1hour")) {
            currentDateTime = currentDateTime.minusMinutes(currentDateTime.getMinute() + 1);
        }
        var candleHistoryLocal = getCandlesByFigiAndIntervalAndBeforeDateTimeLimit(figi,
                currentDateTime, length, interval);

        if (candleHistoryLocal.size() < length) {
            // недостаточно данных за промежуток (мало свечек) - не калькулируем
            return null;
        }
        return candleHistoryLocal;
    }

    public List<CandleDomainEntity> getAllCandlesByFigiByLength(String figi, OffsetDateTime currentDateTime, Integer length, String interval)
    {
        Pageable top = PageRequest.of(0, length);
        var candles = candleRepository.findByFigiAndIntervalAndBeforeDateTimeLimit(figi, interval, currentDateTime, top);
        if (candles.size() < length) {
            // недостаточно данных за промежуток (мало свечек) - не калькулируем
            return null;
        }
        Collections.reverse(candles);
        return candles;
    }

    public List<CandleDomainEntity> getCandlesByFigiAndIntervalAndBeforeDateTimeLimit(String figi, OffsetDateTime dateTime, Integer length, String interval) {
        // если слушатель выключен - значит запускаем не в продакшн режиме, можем хранить свечки в памяти, а не на диске (БД)
        if (!isCandleListenerEnabled) {
            var candlesByFigi = interval.equals("1min") ? candlesLocalCacheMinute.get(figi)
                    : (interval.equals("1hour") ? candlesLocalCacheHour.get(figi) : candlesLocalCacheDay.get(figi));
            if (candlesByFigi.size() == 0) {
                throw new RuntimeException("Candles not found in local cache for " + figi);
            }

            var startDateTime = dateTime.minusDays(30).minusMinutes((interval.equals("1min") ? length
                    : (interval.equals("1hour") ? (length * 24 * 2) : (length * 60 * 24))) * 10);
            var candles = candlesByFigi.stream()
                    //.filter(c -> c.getDateTime().isAfter(startDateTime))
                    .filter(c -> c.getDateTime().isBefore(dateTime) || c.getDateTime().isEqual(dateTime))
                    .collect(Collectors.toList());
            if (length > candles.size()) {
                log.info("candles {} figi {} from {} to {}. Expect length {}, real {}, total {}, begin {}, end {}",
                        interval, figi, startDateTime, dateTime, length, candles.size(), candlesByFigi.size(),
                        candlesByFigi.get(0).getDateTime(), candlesByFigi.get(candlesByFigi.size() - 1).getDateTime());
            }
            candles.sort(Comparator.comparing(CandleDomainEntity::getDateTime));
            return candles.subList(Math.max(0, candles.size() - length), candles.size());
        }

        Pageable top = PageRequest.of(0, length);
        //var candles = interval.equals("1min")
        //        ? candleRepository.findByFigiAndIntervalAndBeforeDateTimeLimit(figi, interval, dateTime, top)
        //        : candleRepository.findByFigiAndIntervalAndBeforeDateTimeCompletedLimit(figi, interval, dateTime, top);
        var candles = candleRepository.findByFigiAndIntervalAndBeforeDateTimeLimit(figi, interval, dateTime, top);
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
            log.error("Can't get candles for figi " + figi, e);
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
        var instruments = strategySelector.getInstrumentsForActiveStrategies();
        log.info("Start to load history {} days, for figies {}", days, figies);
        instruments.stream().forEach(instrument -> {
            var maxDays = Duration.between(instrument.getFirst1MinCandleDate(), now).toDays();
            var curDays = days;
            if (maxDays < days) {
                log.info("Change loading history for {}: new days {} from {}", instrument.getFigi(), maxDays, instrument.getFirst1MinCandleDate());
                curDays = maxDays;
            }
            loadCandlesHistory(instrument.getFigi(), curDays, CandleInterval.CANDLE_INTERVAL_1_MIN, now);
            loadCandlesHistory(instrument.getFigi(), curDays, CandleInterval.CANDLE_INTERVAL_DAY, now);
            loadCandlesHistory(instrument.getFigi(), curDays, CandleInterval.CANDLE_INTERVAL_HOUR, now);
        });

        log.info("Loaded to load history for {} days", days);
    }

    public void loadCandlesHistory(String figi, Long days, CandleInterval candleInterval, OffsetDateTime now)
    {
        String interval = candleInterval == CandleInterval.CANDLE_INTERVAL_1_MIN ? "1min"
                : (candleInterval == CandleInterval.CANDLE_INTERVAL_HOUR ? "1hour" : "1day");
        Integer minutesInInterval = candleInterval == CandleInterval.CANDLE_INTERVAL_1_MIN ? 60 * 24
                : (candleInterval == CandleInterval.CANDLE_INTERVAL_HOUR ? 60 * 24 * 6 : 60 * 24 * 365);
        var candlesMax = candleRepository.findFirstByFigiAndIntervalAndIsCompleteOrderByDateTimeDesc(figi, interval, true);
        var candlesMin = candleRepository.findFirstByFigiAndIntervalAndIsCompleteOrderByDateTimeAsc(figi, interval, true);

        var dayStart = 0;
        var dayEnd = candleInterval == CandleInterval.CANDLE_INTERVAL_1_MIN ? days
                : (candleInterval == CandleInterval.CANDLE_INTERVAL_HOUR ? days
                : Long.max(Long.divideUnsigned(days, 365), 1));
        log.info("Candles history from {} to {} {}", dayStart, dayEnd, interval);
        LongStream datesRange = LongStream.empty();
        var timeStart = now.minusMinutes((dayEnd) * minutesInInterval);
        var timeEnd = now.minusMinutes(0);
        if (candlesMax != null && candlesMin != null) {
            log.info("History {} candles {} has period from {} to {}. We need period from {} to {}", interval, figi, candlesMin.getDateTime(), candlesMax.getDateTime(), timeStart, timeEnd);
            if (candlesMin.getDateTime().isAfter(timeStart) && candlesMin.getDateTime().isBefore(timeEnd)) {
                var dayEndChanged = Math.max(0, dayEnd - Duration.between(timeStart, candlesMin.getDateTime()).toMinutes() / minutesInInterval - 1);
                datesRange = LongStream.concat(datesRange, LongStream.range(dayEndChanged, dayEnd));
                log.info("History start {} candles {} from {} to {} already loaded. Load from {} to {}", interval, figi, timeStart, candlesMin.getDateTime(), dayEndChanged, dayEnd);
            }
            if (candlesMax.getDateTime().isAfter(timeStart) && candlesMax.getDateTime().isBefore(timeEnd)) {
                var dayStartChanged = Math.max(1, dayStart + (Duration.between(candlesMax.getDateTime(), timeEnd).toMinutes() / minutesInInterval) + 1);
                datesRange = LongStream.concat(datesRange, LongStream.range(dayStart, dayStartChanged));
                log.info("History end {} candles {} from {} to {} already loaded. Load from {} to {}", interval, figi, candlesMin.getDateTime(), candlesMax.getDateTime(), dayStart, dayStartChanged);
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
            log.info("Save {} {} candles to DB {}, {} day from {} to {} {}", interval, candles.size(), figi, i, start, end, candles.size() > 0 ? candles.get(0).getTime() : "");
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
        var days = historyDurationByStrategies.toSeconds() > historyDuration.toSeconds()
                ? historyDurationByStrategies.toDays() : historyDuration.toDays();
        requestCandlesHistoryForDays(days);

        if (!isCandleListenerEnabled) {
            var figies = strategySelector.getFigiesForActiveStrategies();
            log.info("Load candles for {} from {} days {} strategy days {}", figies, OffsetDateTime.now().minusDays(days), days, historyDurationByStrategies.toDays());
            var candles = candleRepository.findByByFigiesAndIntervalOrderByDateTime(figies, "1min", OffsetDateTime.now().minusDays(days));
            candlesLocalCacheMinute = candles.stream()
                    .peek(c -> entityManager.detach(c))
                    .collect(Collectors.groupingBy(CandleDomainEntity::getFigi));

            log.info("Loading candles for 1day");
            var candlesDay = candleRepository.findByFigiesAndByIntervalOrderByDateTime(figies, "1day");
            candlesLocalCacheDay = candlesDay.stream()
                    .peek(c -> entityManager.detach(c))
                    .collect(Collectors.groupingBy(CandleDomainEntity::getFigi));

            log.info("Loading candles for 1hour");
                var candlesHour = candleRepository.findByFigiesAndByIntervalOrderByDateTime(figies, "1hour");
            candlesLocalCacheHour = candlesHour.stream()
                    .peek(c -> entityManager.detach(c))
                    .collect(Collectors.groupingBy(CandleDomainEntity::getFigi));
            log.info("Loaded candles 1hour {}", candlesHour.size());
            candlesLocalCacheHour.forEach((c, b) -> log.info("Candles {} {}", c, b.size()));
        }
    }
}
