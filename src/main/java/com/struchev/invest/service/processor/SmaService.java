package com.struchev.invest.service.processor;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.expression.Date;
import com.struchev.invest.service.candle.ICandleHistoryService;
import com.struchev.invest.service.notification.INotificationService;
import com.struchev.invest.service.order.IOrderService;
import com.struchev.invest.strategy.AStrategy;
import com.struchev.invest.strategy.sma.ASmaStrategy;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SmaService implements
        ICalculatorService<ASmaStrategy>,
        ICalculatorTrendService<ASmaStrategy>,
        ICalculatorShortService,
        ICalculatorDetailsService,
        Cloneable
{
    private ICandleHistoryService candleHistoryService;
    private INotificationService notificationService;

    private IOrderService orderService;

    private final Map<String, Map<String, Boolean>> booleanDataMap = new ConcurrentHashMap<>();

    @Override
    public synchronized Map<String, Boolean> getOrderBooleanDataMap(AStrategy strategy, CandleDomainEntity candle) {
        String key = strategy.getExtName() + candle.getFigi();
        return booleanDataMap.getOrDefault(key, new ConcurrentHashMap<>()).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private synchronized void setOrderBooleanData(FactorialDiffAvgAdapterStrategy strategy, CandleDomainEntity candle, String key, Boolean value)
    {
        String keyS = strategy.getExtName() + candle.getFigi();
        if (!booleanDataMap.containsKey(keyS)) {
            booleanDataMap.put(keyS, new ConcurrentHashMap<>());
        }
        booleanDataMap.get(keyS).put(key, value);
    }

    private final Map<String, Map<String, BigDecimal>> bigDecimalDataMap = new ConcurrentHashMap<>();

    @Override
    public synchronized Map<String, BigDecimal> getOrderBigDecimalDataMap(AStrategy strategy, CandleDomainEntity candle) {
        String key = strategy.getExtName() + candle.getFigi();
        return bigDecimalDataMap.getOrDefault(key, new ConcurrentHashMap<>()).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private synchronized void setOrderBigDecimalData(AStrategy strategy, CandleDomainEntity candle, String key, BigDecimal value)
    {
        String keyS = strategy.getExtName() + candle.getFigi();
        if (!bigDecimalDataMap.containsKey(keyS)) {
            bigDecimalDataMap.put(keyS, new ConcurrentHashMap<>());
        }
        bigDecimalDataMap.get(keyS).put(key, value);
    }

    @Override
    public boolean isShouldBuy(ASmaStrategy strategy, CandleDomainEntity candle) {
        return isShouldBuyInternal(strategy, candle, true);
    }

    public boolean isShouldBuyInternal(ASmaStrategy strategy, CandleDomainEntity candle, Boolean isReport) {
        log.trace("isShouldBuy {} {} begin", candle.getFigi(), candle.getDateTime());
        var annotation = "";
        var resBuy = false;
        var resBuyMax = false;
        var resBuyMin = false;

        var currentPrice = candle.getLowestPrice();
        var purchaseRate = candle.getClosingPrice();

        var candleListPrev = getCandlesByFigiByLength(candle.getFigi(), candle.getDateTime(), 1, strategy.getInterval());
        var prevCandle = candleListPrev.get(0);

        Double smaUp = null;
        Double smaDown = null;
        var isTrendUp = true;
        Double sma = null;

        var smaList = getSma(candle.getFigi(), candle.getDateTime(), strategy.getSmaLength(), strategy.getInterval(), CandleDomainEntity::getMedianPrice, 2);
        sma = smaList != null && smaList.size() > 1 ? smaList.get(1) : null;
        var smaPrev = smaList != null && smaList.size() > 0 ? smaList.get(0) : null;
        if (sma != null && smaPrev != null) {
            isTrendUp = smaPrev <= sma;
            if (isTrendUp) {
                smaUp = sma;
            } else {
                smaDown = sma;
            }
        }

        log.trace("isShouldBuy {} {} sma={}", candle.getFigi(), candle.getDateTime(), sma);

        Double limitPrice = null;
        Double smaUnderPrice = null;
        Double smaOverPrice = null;
        Double stopLoss = null;
        Double limitPercent = null;
        Double stopPercent = null;
        Double k = 1.0;
        var smaAverage = getOverSmaAverage(candle.getFigi(), candle.getDateTime(), strategy, CandleDomainEntity::getMedianPrice);
        if (smaAverage != null) {
            annotation += " overSma=" + printPrice(smaAverage.getOverSma());
            annotation += " underSma=" + printPrice(smaAverage.getUnderSma());
            limitPercent = ((100.f * (smaAverage.getOverSma()) / Math.abs(purchaseRate.doubleValue())));
            annotation += " limitPercent=" + printPrice(limitPercent);
            stopPercent = ((100.f * (smaAverage.getUnderSma()) / Math.abs(purchaseRate.doubleValue())));
            annotation += " stopPercent=" + printPrice(stopPercent);

            limitPrice = purchaseRate.doubleValue() + smaAverage.getOverSma();
            smaOverPrice = sma + smaAverage.getOverSma();
            smaUnderPrice = sma - smaAverage.getUnderSma();
            annotation += " limitPrice=" + printPrice(limitPrice);
            annotation += " smaOverPrice=" + printPrice(smaOverPrice);
            annotation += " smaUnderPrice=" + printPrice(smaUnderPrice);
            //k = (limitPercent + stopPercent) / strategy.getDiffFromSmaStandard();
            //annotation += " k=" + printPrice(k);
        }
        Double minLimitPercent = 0.;
        if (
                prevCandle.getHighestPrice().doubleValue() < sma
                && candle.getHighestPrice().doubleValue() < sma
                && !isTrendUp
                && smaAverage != null
        ) {
            annotation += " expProfitPer=" + printPrice(strategy.getSellLimitCriteriaOrig().getExitProfitPercent() * k);
            var stopPercentK = stopPercent * strategy.getStopPercentK();
            annotation += " stopPercentK=" + stopPercentK;
            if (limitPercent > stopPercentK) {
                if (limitPercent > strategy.getSellLimitCriteriaOrig().getExitProfitPercent() * k) {
                    resBuy = true;
                    //limitPercent = strategy.getSellLimitCriteriaOrig().getExitProfitPercent() * k;
                    annotation += " OK BY DOWN";
                    minLimitPercent = Double.valueOf(strategy.getSellLimitCriteriaOrig().getExitProfitPercent()) * k;
                    stopLoss = purchaseRate.doubleValue() - Math.max(smaAverage.getUnderSma() * strategy.getStopPercentK(), smaAverage.getOverSma());
                }
            }
        }

        if (    !resBuy
                && prevCandle.getHighestPrice().doubleValue() < sma
                && candle.getHighestPrice().doubleValue() < sma
                && !isTrendUp
                && smaAverage != null
        ) {
            annotation += " expProfitPerUnder=" + printPrice(strategy.getSellLimitPercentForUnderStop() * k);
            if (
                    limitPercent > strategy.getSellLimitPercentForUnderStop() * k
                    && prevCandle.getHighestPrice().doubleValue() < smaUnderPrice
                    && candle.getHighestPrice().doubleValue() < smaUnderPrice
                    && limitPrice > sma
            ) {
                resBuy = true;
                stopLoss = purchaseRate.doubleValue() - smaAverage.getOverSma();
                //limitPercent = strategy.getSellLimitPercentForUnderStop() * k;
                minLimitPercent = strategy.getSellLimitPercentForUnderStop() * k;
                annotation += " OK BY UNDER STOP";
            }
        }

        if (resBuy) {
            if (stopLoss == null) {
                stopLoss = purchaseRate.doubleValue() - Math.max(smaAverage.getUnderSma() * 2, smaAverage.getOverSma());
            }
            annotation += " stopLoss=" + printPrice(stopLoss);
            var candleMaxLimitPrice = getCandlesByFigiByLength(candle.getFigi(), candle.getDateTime(), strategy.getDeepForMaxLimitPrice(), strategy.getInterval());
            if (null != candleMaxLimitPrice) {
                var maxPrice = candleMaxLimitPrice.stream().mapToDouble(c -> c.getMedianPrice().doubleValue()).max().orElse(0.);
                annotation += " maxPrice=" + printPrice(maxPrice);
                maxPrice -= ((smaAverage.getUnderSma() + smaAverage.getOverSma()) / 4);
                annotation += " maxPrice=" + printPrice(maxPrice);
                if (maxPrice < limitPrice) {
                    limitPrice = maxPrice;
                    limitPercent = 100. * (maxPrice - candle.getClosingPrice().doubleValue()) / candle.getClosingPrice().abs().doubleValue();
                    annotation += " new limitPercent=" + printPrice(limitPercent);
                    annotation += " minLimitPercent=" + printPrice(minLimitPercent);
                    if (limitPercent < minLimitPercent) {
                        resBuy = false;
                        annotation += " SKIP BY new limitPrice";
                    }
                }
            }

            if (resBuy) {
                limitPrice = purchaseRate.doubleValue() + (limitPercent / 100.) * purchaseRate.abs().doubleValue();
                annotation += "new limitPrice=" + printPrice(limitPrice);

                setOrderBigDecimalData(strategy, candle, "limitPrice", BigDecimal.valueOf(limitPrice));
                setOrderBigDecimalData(strategy, candle, "limitPercent", BigDecimal.valueOf(limitPercent));
                setOrderBigDecimalData(strategy, candle, "stopLoss", BigDecimal.valueOf(stopLoss));
            }
        }

        log.trace("isShouldBuy {} {} smaAverage resBuy={}", candle.getFigi(), candle.getDateTime(), resBuy);

        var isDayEnd = false;
        if (resBuy) {
            if (null != strategy.getDayTimeEndBuy(candle.getDateTime())) {
                var dayEndDateTime = Date.getDateTimeInZone(strategy.getDayTimeEndBuy(candle.getDateTime()));
                var candleDateTime = Date.getDateTimeInZone(candle.getDateTime());
                var curDayEnd = candleDateTime
                        .withHour(dayEndDateTime.getHour())
                        .withMinute(dayEndDateTime.getMinute())
                        .withSecond(dayEndDateTime.getSecond());
                annotation += " " + printDateTime(candleDateTime) + ">curDayEnd=" + printDateTime(curDayEnd);
                if (candleDateTime.compareTo(curDayEnd) > 0) {
                    annotation += " SKIP by day end";
                    resBuy = false;
                    isDayEnd = true;
                }
            }
        }

        if (
                resBuy
                && null != strategy.getSellLimitCriteria(candle.getFigi())
                && null == orderService.findActiveByFigiAndStrategy(candle.getFigi(), strategy)
        ) {
            var sellLimitCriteria = strategy.getSellLimitCriteria(candle.getFigi());
            sellLimitCriteria.setExitProfitPercent(strategy.getSellLimitCriteriaOrig().getExitProfitPercent());
            strategy.setSellLimitCriteria(candle.getFigi(), sellLimitCriteria);
        }

        log.trace("isShouldBuy {} {} after setSellLimitCriteria", candle.getFigi(), candle.getDateTime());

        if (isReport) {
            log.trace("isShouldBuy {} {} report begin", candle.getFigi(), candle.getDateTime());
            annotation = "res = " + resBuy + " " + annotation;
            notificationService.reportStrategyExt(
                    resBuy,
                    strategy,
                    candle,
                    "Date|open|high|low|close|ema2|profit|loss|limitPrice|lossAvg|deadLineTop|investBottom|investTop|smaTube|strategy"
                            + "|stopLoss|isDayEnd|smaUp|smaDown|smaOver|smaUnder|stopLoss2",
                    "{} | {} | {} | {} | {} | | {} | {} | {} | {} | ||||by {}"
                            + "| {} | {} | {} | {} | {} | {} | {}",
                    printDateTime(candle.getDateTime()),
                    candle.getOpenPrice(),
                    candle.getHighestPrice(),
                    candle.getLowestPrice(),
                    candle.getClosingPrice(),
                    "",
                    "",
                    limitPrice == null ? "" : printPrice(limitPrice),
                    "",
                    annotation,
                    stopLoss == null ? "" : printPrice(stopLoss),
                    isDayEnd ? candle.getLowestPrice().subtract(candle.getLowestPrice().abs().multiply(BigDecimal.valueOf(0.01))) : "",
                    smaUp != null ? smaUp : "",
                    smaDown != null ? smaDown : "",
                    smaOverPrice != null ? printPrice(smaOverPrice) : "",
                    smaUnderPrice != null ? printPrice(smaUnderPrice) : "",
                    stopLoss == null ? "" : printPrice(stopLoss)
            );
        }
        log.trace("isShouldBuy {} {} end resBuy={}", candle.getFigi(), candle.getDateTime(), resBuy);
        return resBuy;
    }

    @Override
    public boolean isShouldSell(ASmaStrategy strategy, CandleDomainEntity candle, BigDecimal purchaseRate) {
        log.trace("isShouldSell {} {} begin", candle.getFigi(), candle.getDateTime());
        var annotation = "";
        var res = false;

        Double zs = null;
        Double stopLoss = null;
        Boolean isStopLossForce = true;

        Double limitPrice = null;

        var order = orderService.findActiveByFigiAndStrategy(candle.getFigi(), strategy);

        stopLoss = order.getDetails().getCurrentPrices().getOrDefault("stopLoss", BigDecimal.ZERO).doubleValue();

        var sellLimitCriteria = strategy.getSellLimitCriteria(candle.getFigi());
        limitPrice = order.getDetails().getCurrentPrices().getOrDefault("limitPrice", BigDecimal.ZERO).doubleValue();
        Float newLimitPercent = order.getDetails().getCurrentPrices().getOrDefault("limitPercent", BigDecimal.ZERO).floatValue();

        annotation += " stopLoss=" + printPrice(stopLoss);
        annotation += " limitPrice=" + printPrice(limitPrice);
        annotation += " limitPercent=" + printPrice(newLimitPercent);
        sellLimitCriteria.setExitProfitPercent(newLimitPercent);
        strategy.setSellLimitCriteria(candle.getFigi(), sellLimitCriteria);

        Double smaUp = null;
        Double smaDown = null;
        var isTrendUp = true;
        Double sma = null;

        var smaList = getSma(candle.getFigi(), candle.getDateTime(), strategy.getSmaLength(), strategy.getInterval(), CandleDomainEntity::getMedianPrice, 2);
        sma = smaList != null && smaList.size() > 1 ? smaList.get(1) : null;
        var smaPrev = smaList != null && smaList.size() > 0 ? smaList.get(0) : null;
        if (sma != null && smaPrev != null) {
            isTrendUp = smaPrev <= sma;
            if (isTrendUp) {
                smaUp = sma;
            } else {
                smaDown = sma;
            }
        }

        var isStopLoss = false;

        if (null != stopLoss) {
            if (!isStopLossForce && candle.getClosingPrice().doubleValue() < stopLoss) {
                annotation += " stop lost OK";
                res = true;
                isStopLoss = true;
            } else if (isStopLossForce && candle.getHighestPrice().doubleValue() < stopLoss) {
                annotation += " stop lost force OK";
                res = true;
                isStopLoss = true;
            }
        }

        log.trace("isShouldSell {} {} before sma res={}", candle.getFigi(), candle.getDateTime(), res);

        var profit = (float) ((100.f * (candle.getClosingPrice().floatValue() - purchaseRate.floatValue()) / Math.abs(purchaseRate.floatValue())));
        annotation += " profit=" + printPrice(profit);

        var isDayEnd = false;
        if (!res) {
            if (strategy.getDayTimeEndTrading(candle.getDateTime()) != null) {
                var dayEndDateTime = strategy.getDayTimeEndTrading(candle.getDateTime());
                var curDayEnd = candle.getDateTime()
                        .withHour(dayEndDateTime.getHour())
                        .withMinute(dayEndDateTime.getMinute())
                        .withSecond(dayEndDateTime.getSecond());
                annotation += " curDayEnd=" + printDateTime(curDayEnd);
                if (candle.getDateTime().compareTo(curDayEnd) > 0) {
                    annotation += " SELL by day end";
                    res = true;
                    isDayEnd = true;
                }
            }
        }

        log.trace("isShouldSell {} {} report", candle.getFigi(), candle.getDateTime());

        annotation = "res = " + res + " " + annotation;

        var smaAverage = getOverSmaAverage(candle.getFigi(), candle.getDateTime(), strategy, CandleDomainEntity::getMedianPrice);

        notificationService.reportStrategyExt(
                res,
                strategy,
                candle,
                "Date|open|high|low|close|ema2|profit|loss|limitPrice|lossAvg|deadLineTop|investBottom|investTop|smaTube|strategy"
                        + "|stopLoss|isDayEnd|smaUp|smaDown|smaOver|smaUnder|stopLoss2",
                "{} | {} | {} | {} | {} | | {} | {} | {} | {} | ||||sell {}"
                        + "| {} | {} | {} | {} | {} | {} | {}",
                printDateTime(candle.getDateTime()),
                candle.getOpenPrice(),
                candle.getHighestPrice(),
                candle.getLowestPrice(),
                candle.getClosingPrice(),
                "",
                "",
                printPrice(limitPrice),
                "",
                annotation,
                printPrice(stopLoss),
                isDayEnd ? candle.getLowestPrice().subtract(candle.getLowestPrice().abs().multiply(BigDecimal.valueOf(0.01))) : "",
                smaUp != null ? smaUp : "",
                smaDown != null ? smaDown : "",
                smaAverage != null ? printPrice(sma + smaAverage.getOverSma()) : "",
                smaAverage != null ? printPrice(sma - smaAverage.getUnderSma()) : "",
                printPrice(stopLoss)
        );
        log.trace("isShouldSell {} {} end res", candle.getFigi(), candle.getDateTime(), res);
        return res;
    }

    @Override
    public AStrategy.Type getStrategyType() {
        return AStrategy.Type.sma;
    }

    @Override
    public ICalculatorShortService cloneService(IOrderService orderService) throws CloneNotSupportedException {
        var obj = new SmaService();
        obj.orderService = orderService;
        return obj;
    }

    @Override
    public void setCandleHistoryService(ICandleHistoryService candleHistoryService) {
        this.candleHistoryService = candleHistoryService;
    }

    @Override
    public void setNotificationService(INotificationService notificationForShortService) {
        this.notificationService = notificationForShortService;
    }

    @Override
    public void setOrderService(IOrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public boolean isTrendBuy(ASmaStrategy strategy, CandleDomainEntity candle) {
        return false;
    }

    @Override
    public boolean isTrendSell(ASmaStrategy strategy, CandleDomainEntity candle) {
        return false;
    }

    public Integer getLengthToDayEnd(
            String figi,
            OffsetDateTime currentDateTime,
            OffsetDateTime dayEndDateTime,
            String interval
    ) {
        for(var i = 1; i < 30; i++) {
            var begin = currentDateTime.minusDays(i);
            var end = currentDateTime.minusDays(i)
                    .withHour(dayEndDateTime.getHour())
                    .withMinute(dayEndDateTime.getMinute())
                    .withSecond(dayEndDateTime.getSecond());
            if (begin.compareTo(end) >= 0) {
                return 0;
            }
            var candleList = candleHistoryService.getCandlesByFigiBetweenDateTimes(figi, begin, end, interval);
            if (candleList != null && candleList.size() > 0) {
                return candleList.size();
            }
        }
        return null;
    }

    @Builder
    @Data
    public static class AlligatorMouthAverage {
        Integer size;
        String annotation;
        Double price;
        Double priceAlligator;
    }

    private Map<String, AlligatorMouthAverage> alligatorMouthAverageCashMap = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 20;
        }
    };

    private synchronized AlligatorMouthAverage getAlligatorMouthAverageFromCache(String indent)
    {
        if (alligatorMouthAverageCashMap.containsKey(indent)) {
            return alligatorMouthAverageCashMap.get(indent);
        }
        return null;
    }

    private synchronized void addAlligatorMouthAverageToCache(String indent, AlligatorMouthAverage v)
    {
        alligatorMouthAverageCashMap.put(indent, v);
    }

    @Builder
    @Data
    public static class AlligatorMouth {
        CandleDomainEntity candleBegin;
        CandleDomainEntity candleEnd;
        Integer size;
        Boolean isFindBegin;
        Boolean isFindEnd;
        Boolean isUp;
        CandleDomainEntity candleMin;
        CandleDomainEntity candleMax;
    }

    @Builder
    @Data
    public static class OverSmaAverage {
        Double overSma;
        Double underSma;
    }

    private OverSmaAverage getOverSmaAverage(
            String figi,
            OffsetDateTime currentDateTime,
            ASmaStrategy strategy,
            Function<? super CandleDomainEntity, ? extends BigDecimal> keyExtractor
    ) {
        var candleList = getCandlesByFigiByLength(figi, currentDateTime, strategy.getMaxDeep(), strategy.getInterval());
        if (candleList == null) {
            return null;
        }
        var smaList = getSma(figi, candleList.get(candleList.size() - 1).getDateTime(), strategy.getSmaLength(), strategy.getInterval(), CandleDomainEntity::getMedianPrice, strategy.getMaxDeep());

        List<Double> overSma = new ArrayList<>();
        List<Double> underSma = new ArrayList<>();
        for (var i = 0; i < candleList.size(); i++) {
            var candleV = keyExtractor.apply(candleList.get(i)).doubleValue();
            if (candleV > smaList.get(i)) {
                overSma.add(candleV - smaList.get(i));
            } else if (candleV < smaList.get(i)) {
                underSma.add(smaList.get(i) - candleV);
            }
        }
        return OverSmaAverage.builder()
                .overSma(overSma.stream().mapToDouble(v -> v).average().orElse(0))
                .underSma(underSma.stream().mapToDouble(v -> v).average().orElse(0))
                .build();
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
        List<Double> ret = new ArrayList<Double>();
        var prevTicksToCalc = prevTicks;
        var candleList = getCandlesByFigiByLength(figi,
                currentDateTime, prevTicks + 1, interval);
        if (candleList == null) {
            return null;
        }
        for (var i = 0; i < prevTicks + 1; i++) {
            var indent = "sma" + figi + printDateTime(candleList.get(i).getDateTime()) + interval + length + getMethodKey(keyExtractor);
            var smaCashed = getCashedValueEma(indent);
            if (smaCashed == null) {
                break;
            }
            ret.add(smaCashed);
            prevTicksToCalc--;
        }

        candleList = getCandlesByFigiByLength(figi,
                currentDateTime, length + prevTicksToCalc, interval);
        if (candleList == null) {
            return null;
        }

        for (var i = 0; i < prevTicksToCalc + 1; i++) {
            var candleListPrev = new ArrayList<CandleDomainEntity>(candleList);
            for (var j = 0; j < i; j++) {
                candleListPrev.remove(0);
            }
            for (var j = i; j < prevTicksToCalc; j++) {
                candleListPrev.remove(candleListPrev.size() - 1);
            }
            Double smaPrev = candleListPrev.stream().mapToDouble(a -> Optional.ofNullable(a).map(keyExtractor).orElse(null).doubleValue()).average().orElse(0);
            ret.add(smaPrev);
            var indent = "sma" + figi + printDateTime(candleListPrev.get(candleListPrev.size() - 1).getDateTime()) + interval + length + getMethodKey(keyExtractor);
            //addCashedValue(indent, smaPrev);
        }
        return ret;
    }

    private List<Double> getSmma(
            String figi,
            OffsetDateTime currentDateTime,
            Integer length,
            String interval,
            Function<? super CandleDomainEntity, ? extends BigDecimal> keyExtractor
    ) {
        return getSmma(figi, currentDateTime, length, interval, keyExtractor, 2);
    }

    private List<Double> getSmma(
            String figi,
            OffsetDateTime currentDateTime,
            Integer length,
            String interval,
            Function<? super CandleDomainEntity, ? extends BigDecimal> keyExtractor,
            Integer prevTicks
    ) {
        return getGeneralEMA(figi, currentDateTime, length, interval, keyExtractor, prevTicks,
                1 / Double.valueOf(length)
        );
    }

    private List<Double> getGeneralEMA(
            String figi,
            OffsetDateTime currentDateTime,
            Integer length,
            String interval,
            Function<? super CandleDomainEntity, ? extends BigDecimal> keyExtractor,
            Integer prevTicks,
            Double multiplier
    ) {
        List<Double> ret = new ArrayList<Double>();
        var curPrevTicks = length * 10;
        var maxPrevTicks = curPrevTicks + prevTicks;
        var candleList = getCandlesByFigiByLength(figi,
                currentDateTime, length + maxPrevTicks - 1, interval);
        if (candleList == null) {
            return null;
        }
        var smaList = getSma(figi, currentDateTime, length, interval, keyExtractor, maxPrevTicks);
        if (smaList == null) {
            return null;
        }
        for (var j = 0; j < maxPrevTicks; j++) {
            if (ret.size() > 0) {
                var prev = ret.get(ret.size() - 1);
                var ema = (Objects.requireNonNull(Optional.ofNullable(candleList.get(j + length - 1)).map(keyExtractor).orElse(null)).doubleValue() - prev)
                        * multiplier + prev;
                ret.add(ema);
            } else {
                ret.add(smaList.get(j));
            }
        }
        for (var j = 0; j < curPrevTicks; j++) {
            ret.remove(0);
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
        return getEma(figi, currentDateTime, length, interval, keyExtractor, 2);
    }

    private List<Double> getEma(
            String figi,
            OffsetDateTime currentDateTime,
            Integer length,
            String interval,
            Function<? super CandleDomainEntity, ? extends BigDecimal> keyExtractor,
            Integer prevTicks
    ) {
        return getGeneralEMA(figi, currentDateTime, length, interval, keyExtractor, prevTicks,
                2 / (Double.valueOf(length) + 1.0)
        );
    }

    private List<CandleDomainEntity> getCandlesByFigiByLength(String figi, OffsetDateTime currentDateTime, Integer length, String interval)
    {
        var minInInterval = 0;
        if (interval.equals("5min")) {
            minInInterval = 5;
        }
        if (interval.equals("15min")) {
            minInInterval = 15;
        }
        if (minInInterval > 0) {
            var minusS = currentDateTime.getMinute() % minInInterval;
            minusS += minInInterval;
            var currentDateTimeNew = currentDateTime
                    .minusMinutes(minusS)
                    .minusSeconds(currentDateTime.getSecond())
                    .minusNanos(currentDateTime.getNano())
            ;
            //log.trace("getCandlesByFigiByLength: currentDateTime from {} to {}", printDateTime(currentDateTime), printDateTime(currentDateTimeNew));
            currentDateTime = currentDateTimeNew;
        }

        String key = "len" + figi + "-" + printDateTime(currentDateTime) + "-" + interval;
        var res = getCashedValueCandleList(key);
        if (res != null) {
            log.trace("getCandlesByFigiByLength: find value in cash by key {} size {}. Need {}", key, res.size(), length);
            if (res.size() == length) {
                return res;
            }
            if (res.size() > length) {
                return res.subList(res.size() - length, res.size());
            }
        }

        res = candleHistoryService.getCandlesByFigiByLength(figi, currentDateTime, length, interval);
        addCashedValueCandleList(key, res);
        log.trace("getCandlesByFigiByLength: add value to cash with key {} size {}", key, length);
        return res;
    }

    private Map<String, List<CandleDomainEntity>> candleListCashMap = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 4 * 20;
        }
    };

    private synchronized List<CandleDomainEntity> getCashedValueCandleList(String indent)
    {
        if (candleListCashMap.containsKey(indent)) {
            return candleListCashMap.get(indent);
        }
        return null;
    }

    private synchronized void addCashedValueCandleList(String indent, List<CandleDomainEntity> v)
    {
        candleListCashMap.put(indent, v);
    }

    private String printPrice(BigDecimal s)
    {
        if (s == null) {
            return "null";
        }
        return printPrice(s.toString());
    }

    private String printPrice(Double s)
    {
        if (s == null) {
            return "null";
        }
        return printPrice(s.toString());
    }

    private String printPrice(Float s)
    {
        if (s == null) {
            return "null";
        }
        return printPrice(s.toString());
    }

    private String printPrice(String s)
    {
        return s.indexOf(".") < 0 ? s : s.replaceAll("0*$", "").replaceAll("\\.$", "");
    }

    private String printDateTime(OffsetDateTime dt)
    {
        return notificationService.formatDateTime(dt);
    }

    private String printDateTime(ZonedDateTime dt)
    {
        return dt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private String getMethodKey(Function<? super CandleDomainEntity, ? extends BigDecimal> keyExtractor)
    {
        return Integer.toHexString(keyExtractor.hashCode());
    }

    private synchronized Double getCashedValueEma(String indent)
    {
        return null;
    }

    private Map<String, Double> doubleCashMap = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 500 * 2 * 3 * 20;
        }
    };

    private synchronized Double getCashedValueDouble(String indent)
    {
        if (doubleCashMap.containsKey(indent)) {
            return doubleCashMap.get(indent);
        }
        return null;
    }

    private synchronized void addCashedValueDouble(String indent, Double v)
    {
        doubleCashMap.put(indent, v);
    }
}
