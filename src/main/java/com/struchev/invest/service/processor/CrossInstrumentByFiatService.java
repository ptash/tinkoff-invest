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

        var smaTube = getSma(figi, currentDateTime, strategy.getSmaTubeLength(), strategy.getInterval(), keyExtractor);

        var smaSlowest = getSma(figi, currentDateTime, strategy.getSmaSlowestLength(), strategy.getInterval(), keyExtractor, strategy.getTicksMoveUp());

        var smaSlow = getSma(figi, currentDateTime, strategy.getSmaSlowLength(), strategy.getInterval(), keyExtractor);

        var smaFast = getSma(figi, currentDateTime, strategy.getSmaFastLength(), strategy.getInterval(), keyExtractor);

        var emaFast = getEma(figi, currentDateTime, strategy.getEmaFastLength(), strategy.getInterval(), keyExtractor);

        var ema2 = getEma(figi, currentDateTime, 2, strategy.getInterval(), keyExtractor);

        if (null == smaTube || null == smaSlowest || null == smaSlow || null == smaFast || null == emaFast) {
            log.info("There is not enough data for the interval: currentDateTime = {}", currentDateTime);
            return null;
        }

        var smaTubeCur = smaTube.get(smaTube.size() - 1);

        var smaSlowestCur = smaSlowest.get(smaSlowest.size() - 1);
        var deadLineTop = BigDecimal.valueOf(smaFast.get(smaFast.size() - 1));
        var b100 = BigDecimal.valueOf(100);
        deadLineTop = deadLineTop.add(deadLineTop.multiply(BigDecimal.valueOf(strategy.getDeadLinePercent())).divide(b100));
        var deadLineBottom = BigDecimal.valueOf(smaSlowestCur);
        deadLineTop = deadLineTop.min(deadLineBottom.add(deadLineBottom.multiply(BigDecimal.valueOf(strategy.getDeadLinePercentFromSmaSlowest())).divide(b100)));
        deadLineBottom = deadLineBottom.subtract(deadLineBottom.multiply(BigDecimal.valueOf(strategy.getDeadLinePercentFromSmaSlowest())).divide(b100));

        var tubeMaxPercent = strategy.getDeadLinePercentFromSmaSlowest() / 2;
        var deltaTubePercent = ((smaTubeCur - smaSlowestCur) * 100)/smaTubeCur;
        Double tubeTopToBy = null;
        if (Math.abs(deltaTubePercent) < tubeMaxPercent) {
            // рядом
            tubeTopToBy = smaTubeCur - ((smaTubeCur * tubeMaxPercent) / 100);
            if (deadLineTop.compareTo(BigDecimal.valueOf(tubeTopToBy)) > 0) {
                deadLineTop = BigDecimal.valueOf(tubeTopToBy);
            } else {
                tubeTopToBy = null;
            }
        } else if (deltaTubePercent > 0) {
            // средняя выше
            deadLineTop = deadLineTop.min(BigDecimal.valueOf(smaSlowestCur - ((smaSlowestCur * tubeMaxPercent * 2)/ 100)));
            deadLineTop = deadLineTop.min(BigDecimal.valueOf(smaSlowestCur - (smaTubeCur - smaSlowestCur)));
        } else {
            // средняя ниже
            deadLineTop = deadLineTop.min(BigDecimal.valueOf(Math.max(
                    smaTubeCur + (smaTubeCur * tubeMaxPercent * 3) / 100,
                    smaSlowestCur + (smaTubeCur * strategy.getDeadLinePercent()) / 100
            )));
        }

        var result = (candle.getClosingPrice().compareTo(deadLineTop) < 0
                && candle.getClosingPrice().compareTo(deadLineBottom) > 0);
        var annotation = "";
        if (result) {
            if (tubeTopToBy != null && getPercentMoveUp(smaTube) >= strategy.getMinPercentTubeMoveUp() && candle.getClosingPrice().compareTo(BigDecimal.valueOf(tubeTopToBy)) < 0) {
                annotation += " tubeTopToBy " + getPercentMoveUp(smaTube) + " (" + smaTube + ")";
            } else if (isCrossover(smaFast, smaSlow)) {
                annotation += " smaFast/smaSlow";
            //} else if (isCrossover(smaSlow, smaSlowest)) {
            //    annotation += " smaSlow/smaSlowest";
            } else if (isCrossover(emaFast, smaFast)) {
                annotation += " emaFast/smaFast";
            } else if (isCrossover(emaFast, smaSlow)) {
                annotation += " emaFast/smaSlow";
            } else if (isCrossover(smaSlowest, ema2)) {
                annotation += " smaSlowest/ema2";
                var crossPercent = getCrossPercent(smaSlowest, ema2);
                var percentMoveUp = getPercentMoveUp(smaSlowest);
                if (crossPercent < strategy.getMaxSmaFastCrossPercent()
                        && percentMoveUp >= strategy.getMinPercentMoveUp()) {

                } else {
                    annotation += " false";
                    result = false;
                }
                annotation += " crossPercent (" + (crossPercent < strategy.getMaxSmaFastCrossPercent()) + ") = " + crossPercent + " < " + strategy.getMaxSmaFastCrossPercent() + "; percentMoveUp (" + (percentMoveUp >= strategy.getMinPercentMoveUp()) + "): " + smaSlowest + " " + percentMoveUp + " >= " + strategy.getMinPercentMoveUp();
            } else {
                result = false;
            }
        }

        reportLog(
                strategy,
                figi,
                "{} | {} | {} | {} | {} | {} | {} | | {} | {} | {} | {} |by{}",
                notificationService.formatDateTime(currentDateTime),
                smaSlowest.get(1),
                smaSlow.get(1),
                smaFast.get(1),
                emaFast.get(1),
                ema2.get(1),
                result ? candle.getClosingPrice() : "",
                result ? candle.getClosingPrice() : "",
                deadLineBottom,
                deadLineTop,
                smaTubeCur,
                annotation
        );

        return result;
    }

    private Boolean calculateCellCriteria(CandleDomainEntity candle,
                                         Function<? super CandleDomainEntity, ? extends BigDecimal> keyExtractor,
                                         AInstrumentByFiatCrossStrategy strategy) {
        OffsetDateTime currentDateTime = candle.getDateTime();
        String figi = candle.getFigi();

        var smaTube = getSma(figi, currentDateTime, strategy.getSmaTubeLength(), strategy.getInterval(), keyExtractor, 0);

        var smaSlowest = getSma(figi, currentDateTime, strategy.getSmaSlowestLength(), strategy.getInterval(), keyExtractor);

        var smaSlow = getSma(figi, currentDateTime, strategy.getSmaSlowLength(), strategy.getInterval(), keyExtractor);

        var ema2 = getEma(figi, currentDateTime, 2, strategy.getInterval(), keyExtractor);

        var emaFast = getEma(figi, currentDateTime, strategy.getEmaFastLength(), strategy.getInterval(), keyExtractor);

        var smaFast = getSma(figi, currentDateTime, strategy.getSmaFastLength(), strategy.getInterval(), keyExtractor);

        if (null == smaTube || null == smaSlowest || null == smaSlow || null == ema2 || null == emaFast) {
            log.info("There is not enough data for the interval: currentDateTime = {}", currentDateTime);
            return null;
        }

        var result = isCrossover(smaSlowest, smaSlow)
                || isCrossover(smaSlow, emaFast)
                || isCrossover(ema2, emaFast)
                || isCrossover(emaFast, ema2)
                || isCrossover(ema2, smaSlowest)
        ;

        reportLog(
                strategy,
                figi,
                "{} | {} | {} | {} | {} | {} | | {} | {} ||| {} | calculateCellCriteria",
                notificationService.formatDateTime(currentDateTime),
                smaSlowest.get(1),
                smaSlow.get(1),
                smaFast.get(1),
                emaFast.get(1),
                ema2.get(1),
                result ? candle.getClosingPrice() : "",
                result ? candle.getClosingPrice() : "",
                smaTube.get(0)
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
        int xPrev = x.size() - 2;
        int yPrev = y.size() - 2;
        int xCur = xPrev + 1;
        int yCur = yPrev + 1;
        return x.get(xPrev) < y.get(yPrev) && x.get(xCur) >= y.get(yCur);
    }

    private Double getCrossPercent(List<Double> x, List<Double> y) {
        int xPrev = x.size() - 2;
        int yPrev = y.size() - 2;
        int xCur = xPrev + 1;
        int yCur = yPrev + 1;
        var yAv = y.get(yCur) + (y.get(yPrev) - y.get(yCur)) / 2;
        var delta = Math.abs(Math.abs(y.get(yCur) - y.get(yPrev)) - Math.abs(x.get(xCur) - x.get(xPrev)));
        return (delta * 100 / yAv);
    }

    private Double getPercentMoveUp(List<Double> x) {
        int xPrev = 0;
        int xCur = x.size() - 1;
        return ((x.get(xCur) - x.get(xPrev)) * 100) / x.get(xPrev);
    }

    private List<Double> getSma(
            String figi,
            OffsetDateTime currentDateTime,
            Integer length,
            String interval,
            Function<? super CandleDomainEntity, ? extends BigDecimal> keyExtractor
    ) {
        return getSma(figi, currentDateTime, length, interval, keyExtractor, 1);
    }

    private List<Double> getSma(
            String figi,
            OffsetDateTime currentDateTime,
            Integer length,
            String interval,
            Function<? super CandleDomainEntity, ? extends BigDecimal> keyExtractor,
            Integer prevTicks
    ) {
        var candleList = getCandlesByFigiByLength(figi,
                currentDateTime, length + prevTicks, interval);
        if (candleList == null) {
            return null;
        }
        List<Double> ret = new ArrayList<Double>();
        for (var i = 0; i < prevTicks + 1; i++) {
            var candleListPrev = new ArrayList<CandleDomainEntity>(candleList);
            for (var j = 0; j < i; j++) {
                candleListPrev.remove(0);
            }
            for (var j = i; j < prevTicks; j++) {
                candleListPrev.remove(candleListPrev.size() - 1);
            }
            Double smaPrev = candleListPrev.stream().mapToDouble(a -> Optional.ofNullable(a).map(keyExtractor).orElse(null).doubleValue()).average().orElse(0);
            ret.add(smaPrev);
        }
        return ret;
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
                "Date|smaSlowest|smaSlow|smaFast|emaFast|ema2|bye|sell|position|deadLineBottom|deadLineTop|smaTube|strategy",
                format,
                arguments
        );
    }
}
