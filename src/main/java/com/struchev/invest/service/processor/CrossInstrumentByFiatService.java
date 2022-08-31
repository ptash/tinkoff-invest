package com.struchev.invest.service.processor;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.service.candle.CandleHistoryService;
import com.struchev.invest.service.notification.NotificationService;
import com.struchev.invest.strategy.AStrategy;
import com.struchev.invest.strategy.instrument_by_fiat_cross.AInstrumentByFiatCrossStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

/**
 * Сервис для калькуляции решения купить/продать для стратегий с типом instrumentByFiat
 * Торгует при изменении стоимости торгового инструмента относительно его валюты продажи/покупки
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CrossInstrumentByFiatService implements ICalculatorService<AInstrumentByFiatCrossStrategy> {
    private final CandleHistoryService candleHistoryService;

    private final NotificationService notificationService;

    /**
     * Расчет перцентиля по цене инструмента за определенный промежуток
     *
     * @param candle
     * @param keyExtractor
     * @param strategy
     * @return
     */
    private Boolean calculateBuyCriteria(CandleDomainEntity candle,
                                         Function<? super CandleDomainEntity, ? extends BigDecimal> keyExtractor,
                                         AInstrumentByFiatCrossStrategy strategy) {
        OffsetDateTime currentDateTime = candle.getDateTime();
        String figi = candle.getFigi();

        var smaSlowest = getSma(figi, currentDateTime, strategy.getSmaSlowestLength(), strategy.getInterval(), keyExtractor);

        var smaSlow = getSma(figi, currentDateTime, strategy.getSmaSlowLength(), strategy.getInterval(), keyExtractor);

        var smaFast = getSma(figi, currentDateTime, strategy.getSmaFastLength(), strategy.getInterval(), keyExtractor);

        var emaFast = getEma(figi, currentDateTime, strategy.getEmaFastLength(), strategy.getInterval(), keyExtractor);

        var ema2 = getEma(figi, currentDateTime, 2, strategy.getInterval(), keyExtractor);

        if (null == smaSlowest || null == smaSlow || null == smaFast || null == emaFast) {
            log.info("There is not enough data for the interval: currentDateTime = {}", currentDateTime);
            return null;
        }

        var result = isCrossover(smaFast, smaSlow)
                || isCrossover(smaSlow, smaSlowest)
                || isCrossover(emaFast, smaFast)
                //|| isCrossover(emaFast, smaSlow)
        ;

        reportLog(
                strategy,
                figi,
                "{} | {} | {} | {} | {} | {} | {} | | {} | calculateBuyCriteria",
                currentDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE) + " " + currentDateTime.format(DateTimeFormatter.ISO_LOCAL_TIME),
                smaSlowest.get(1),
                smaSlow.get(1),
                smaFast.get(1),
                emaFast.get(1),
                ema2.get(1),
                result ? candle.getClosingPrice() : "",
                result ? candle.getClosingPrice() : ""
        );

        return result;
    }

    private Boolean calculateCellCriteria(CandleDomainEntity candle,
                                         Function<? super CandleDomainEntity, ? extends BigDecimal> keyExtractor,
                                         AInstrumentByFiatCrossStrategy strategy) {
        OffsetDateTime currentDateTime = candle.getDateTime();
        String figi = candle.getFigi();


        var smaSlowest = getSma(figi, currentDateTime, strategy.getSmaSlowestLength(), strategy.getInterval(), keyExtractor);

        var smaSlow = getSma(figi, currentDateTime, strategy.getSmaSlowLength(), strategy.getInterval(), keyExtractor);

        var ema2 = getEma(figi, currentDateTime, 2, strategy.getInterval(), keyExtractor);

        var emaFast = getEma(figi, currentDateTime, strategy.getEmaFastLength(), strategy.getInterval(), keyExtractor);

        var smaFast = getSma(figi, currentDateTime, strategy.getSmaFastLength(), strategy.getInterval(), keyExtractor);

        if (null == smaSlowest || null == smaSlow || null == ema2 || null == emaFast) {
            log.info("There is not enough data for the interval: currentDateTime = {}", currentDateTime);
            return null;
        }

        var result = isCrossover(smaSlowest, smaSlow)
                || isCrossover(smaSlow, emaFast)
                || isCrossover(ema2, emaFast)
                || isCrossover(ema2, smaSlowest)
        ;

        reportLog(
                strategy,
                figi,
                "{} | {} | {} | {} | {} | {} | | {} | {} | calculateCellCriteria",
                currentDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE) + " " + currentDateTime.format(DateTimeFormatter.ISO_LOCAL_TIME),
                smaSlowest.get(1),
                smaSlow.get(1),
                smaFast.get(1),
                emaFast.get(1),
                ema2.get(1),
                result ? candle.getClosingPrice() : "",
                result ? candle.getClosingPrice() : ""
        );

        return result;
    }

    /**
     * The `x`-series is defined as having crossed over `y`-series if the value of `x` is greater than the value of `y`
     * and the value of `x` was less than the value of `y` on the bar immediately preceding the current bar.
     *
     * @param x
     * @param y
     * @return
     */
    private Boolean isCrossover(List<Double> x, List<Double> y)
    {
        if (null == x || null == y) {
            return false;
        }
        return x.get(0) < y.get(0) && x.get(1) >= y.get(1);
    }

    private List<Double> getSma(
            String figi,
            OffsetDateTime currentDateTime,
            Integer length,
            String interval,
            Function<? super CandleDomainEntity, ? extends BigDecimal> keyExtractor
    ) {
        var candleList = getCandlesByFigiByLength(figi,
                currentDateTime, length + 1, interval);
        if (candleList == null) {
            return null;
        }
        var candleListPrev = new ArrayList<CandleDomainEntity>(candleList);
        candleList.remove(0);
        candleListPrev.remove(candleListPrev.size() - 1);
        Double sma = candleList.stream().mapToDouble(a -> Optional.ofNullable(a).map(keyExtractor).orElse(null).doubleValue()).average().orElse(0);
        Double smaPrev = candleListPrev.stream().mapToDouble(a -> Optional.ofNullable(a).map(keyExtractor).orElse(null).doubleValue()).average().orElse(0);
        return List.of(smaPrev, sma);
    }

    private List<Double> getEma(
            String figi,
            OffsetDateTime currentDateTime,
            Integer length,
            String interval,
            Function<? super CandleDomainEntity, ? extends BigDecimal> keyExtractor
    ) {
        var candleList = getCandlesByFigiByLength(figi,
                currentDateTime, length + 1, interval);
        if (candleList == null) {
            return null;
        }
        Double ema = Optional.ofNullable(candleList.get(1)).map(keyExtractor).orElse(null).doubleValue();
        for (int i = 2; i < (length + 1); i++) {
            var alpha = 2f / (i + 1);
            ema = alpha * Optional.ofNullable(candleList.get(i)).map(keyExtractor).orElse(null).doubleValue() + (1 - alpha) * ema;
        }

        Double emaPrev = Optional.ofNullable(candleList.get(0)).map(keyExtractor).orElse(null).doubleValue();
        for (int i = 1; i < length; i++) {
            var alpha = 2f / (i + 1 + 1);
            emaPrev = alpha * Optional.ofNullable(candleList.get(i)).map(keyExtractor).orElse(null).doubleValue() + (1 - alpha) * emaPrev;
        }
        return List.of(emaPrev, ema);
    }

    private List<CandleDomainEntity> getCandlesByFigiBetweenDateTimes(String figi, OffsetDateTime currentDateTime, Duration duration, String interval)
    {
        var startDateTime = currentDateTime.minus(duration);
        var candleHistoryLocal = candleHistoryService.getCandlesByFigiBetweenDateTimes(figi,
                startDateTime, currentDateTime, interval);

        if (interval.equals("1min")) {
            // недостаточно данных за промежуток (мало свечек) - не калькулируем
            if (Duration.ofSeconds(candleHistoryLocal.size()).compareTo(duration) < 0) {
                return null;
            }
        } else {
            if (Duration.ofDays(candleHistoryLocal.size()).compareTo(duration) < 0) {
                return null;
            }
        }
        return candleHistoryLocal;
    }

    private List<CandleDomainEntity> getCandlesByFigiByLength(String figi, OffsetDateTime currentDateTime, Integer length, String interval)
    {
        var candleHistoryLocal = candleHistoryService.getCandlesByFigiAndIntervalAndBeforeDateTimeLimit(figi,
                currentDateTime, length, interval);

        if (candleHistoryLocal.size() < length) {
            // недостаточно данных за промежуток (мало свечек) - не калькулируем
            return null;
        }
        return candleHistoryLocal;
    }

    /**
     * Расчет цены для покупки на основе персентиля по цене инструмента за определенный промежуток
     * Будет куплен, если текущая цена < значения персентиля
     *
     * @param strategy
     * @param candle
     * @return
     */
    @Override
    public boolean isShouldBuy(AInstrumentByFiatCrossStrategy strategy, CandleDomainEntity candle) {
        return calculateBuyCriteria(candle,
                CandleDomainEntity::getClosingPrice, strategy);
    }


    /**
     * Расчет цены для продажи на основе персентиля по цене инструмента за определенный промежуток (takeProfitPercentile, stopLossPercentile)
     * Расчет цены для продажи на основе цены покупки (takeProfitPercent, stopLossPercent)
     * Будет куплен если
     * - сработал один из takeProfitPercent, takeProfitPercentile
     * - сработали оба stopLossPercent, stopLossPercentile are happened
     *
     * @param candle
     * @param purchaseRate
     * @return
     */
    @Override
    public boolean isShouldSell(AInstrumentByFiatCrossStrategy strategy, CandleDomainEntity candle, BigDecimal purchaseRate) {
        var sellCriteria = strategy.getSellCriteria();
        var profitPercent = candle.getClosingPrice().subtract(purchaseRate)
                .multiply(BigDecimal.valueOf(100))
                .divide(purchaseRate, 4, RoundingMode.HALF_DOWN);

        if (sellCriteria.getStopLossPercent() != null && profitPercent.floatValue() < -2 * sellCriteria.getStopLossPercent()) {
            return true;
        }

        if (!calculateCellCriteria(candle, CandleDomainEntity::getClosingPrice, strategy)) {
            return false;
        }

        if (sellCriteria.getTakeProfitPercent() == null && candle.getClosingPrice().compareTo(purchaseRate) > 0) {
            return true;
        }

        if (sellCriteria.getStopLossPercent() == null && candle.getClosingPrice().compareTo(purchaseRate) < 0) {
            return true;
        }

        // profit % > take profit %, profit % > 0.1%
        if (sellCriteria.getTakeProfitPercent() != null
                && profitPercent.floatValue() > sellCriteria.getTakeProfitPercent()
                && profitPercent.floatValue() > 0.1f) {
            return true;
        }

        if (sellCriteria.getStopLossPercent() != null && profitPercent.floatValue() < -1 * sellCriteria.getStopLossPercent()) {
            return true;
        }

        return false;
    }

    @Override
    public AStrategy.Type getStrategyType() {
        return AStrategy.Type.instrumentCrossByFiat;
    }

    private void reportLog(AInstrumentByFiatCrossStrategy strategy, String figi, String format, Object... arguments)
    {
        notificationService.reportStrategy(
                strategy,
                figi,
                "Date|smaSlowest|smaSlow|smaFast|emaFast|ema2|bye|sell|position|strategy",
                format,
                arguments
        );
    }
}
