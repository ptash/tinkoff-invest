package com.struchev.invest.service.processor;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.service.candle.ICandleHistoryService;
import com.struchev.invest.service.notification.INotificationService;
import com.struchev.invest.service.order.IOrderService;
import com.struchev.invest.strategy.AStrategy;
import com.struchev.invest.strategy.alligator.AAlligatorStrategy;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.relational.core.sql.In;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AlligatorService implements
        ICalculatorService<AAlligatorStrategy>,
        ICalculatorTrendService<AAlligatorStrategy>,
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
    public boolean isShouldBuy(AAlligatorStrategy strategy, CandleDomainEntity candle) {
        return isShouldBuyInternal(strategy, candle, true);
    }

    public boolean isShouldBuyInternal(AAlligatorStrategy strategy, CandleDomainEntity candle, Boolean isReport) {
        var annotation = "";
        var resBuy = false;

        var currentPrice = candle.getLowestPrice();

        var blue = getAlligatorBlue(candle.getFigi(), candle.getDateTime(), strategy);
        var red = getAlligatorRed(candle.getFigi(), candle.getDateTime(), strategy);
        var green = getAlligatorGreen(candle.getFigi(), candle.getDateTime(), strategy);

        var candleMinMaxList = getCandlesByFigiByLength(candle.getFigi(), candle.getDateTime(), 5, strategy.getInterval());
        var maxCandle = candleMinMaxList.stream().reduce((first, second) ->
                first.getHighestPrice().compareTo(second.getHighestPrice()) > 0 ? first : second
        ).orElse(null);
        var minCandle = candleMinMaxList.stream().reduce((first, second) ->
                first.getLowestPrice().compareTo(second.getLowestPrice()) < 0 ? first : second
        ).orElse(null);
        var middleCandle = candleMinMaxList.get(2);
        var isMax = maxCandle == middleCandle;
        var isMin = minCandle == middleCandle;

        var lastFMaxCandle = getLastFMaxCandle(candle.getFigi(), candle.getDateTime(), strategy);
        if (null != lastFMaxCandle) {
            var candleListMax = candleHistoryService.getCandlesByFigiBetweenDateTimes(candle.getFigi(), lastFMaxCandle.getDateTime(), candle.getDateTime(), strategy.getInterval());
            var maxIntervalCandle = candleListMax.stream().reduce((first, second) ->
                    first.getHighestPrice().compareTo(second.getHighestPrice()) > 0 ? first : second
            ).orElse(null);
            if (
                    maxIntervalCandle != lastFMaxCandle
                            && maxIntervalCandle.getHighestPrice().compareTo(lastFMaxCandle.getHighestPrice()) > 0
            ) {
                if (
                        currentPrice.compareTo(lastFMaxCandle.getClosingPrice().max(lastFMaxCandle.getOpenPrice())) < 0
                        && currentPrice.doubleValue() > blue
                        && green > red
                        && red > blue
                ) {
                    setOrderBigDecimalData(strategy, candle, "priceWanted", currentPrice.max(lastFMaxCandle.getClosingPrice().max(lastFMaxCandle.getOpenPrice())));
                    resBuy = true;
                }
            }
        }

        Double zs = null;
        if (green != null && blue != null) {
            zs = green + (green - blue) * 1.618;
            Float newGreenPercent = (float) ((100.f * (zs - green) / Math.abs(green)));
            var average = getAveragePercent(candle.getFigi(), candle.getDateTime(), strategy);
            annotation += " newGreenPercent=" + printPrice(newGreenPercent);
            annotation += " average=" + printPrice(average);
            Float newGreenPercentAverage = (float) (newGreenPercent / average);
            annotation += " newGreenPercentAverage=" + printPrice(newGreenPercentAverage);
            Float maxBuyPercentAverage = null;
            if (resBuy) {
                var maxBuy = lastFMaxCandle.getClosingPrice().max(lastFMaxCandle.getOpenPrice());
                Float maxBuyPercent = (float) Math.abs(((100.f * (maxBuy.doubleValue() - green) * 1.618 / green)));
                maxBuyPercentAverage = (float) (maxBuyPercent / average);
                annotation += " maxBuyPercent=" + printPrice(maxBuyPercent);
                annotation += " maxBuyPercentAverage=" + printPrice(maxBuyPercentAverage);
            }
            if (currentPrice.doubleValue() > green && newGreenPercentAverage < strategy.getMinGreenPercent()) {
                annotation += " skip by percent<" + strategy.getMinGreenPercent();
                resBuy = false;
            }
            if (
                    currentPrice.doubleValue() < green
                    && (maxBuyPercentAverage != null && maxBuyPercentAverage > strategy.getMinGreenPercent())
            ) {
                annotation += " skip by buy percent>" + strategy.getMinGreenPercent();
                resBuy = false;
            }
            if (newGreenPercentAverage > strategy.getMaxGreenPercent()) {
                annotation += " skip by percent<" + strategy.getMaxGreenPercent();
                resBuy = false;
            }
        }
        if (resBuy) {
            var alligatorLengthAverage = getAlligatorLengthAverage(candle.getFigi(), candle.getDateTime(), strategy);
            var curAlligatorMouth = getAlligatorMouth(candle.getFigi(), candle.getDateTime(), strategy);
            annotation += " MonthBegin=" + printDateTime(curAlligatorMouth.getCandleBegin().getDateTime());
            annotation += " MonthEnd=" + printDateTime(curAlligatorMouth.getCandleEnd().getDateTime());
            var curAlligatorLength = curAlligatorMouth.getSize();
            annotation += " alligatorLengthAverage=" + printPrice(alligatorLengthAverage);
            annotation += " curAlligatorLength=" + curAlligatorLength;
            if (curAlligatorLength > alligatorLengthAverage) {
                annotation += " skip by AlligatorLength>" + printPrice(alligatorLengthAverage);
                resBuy = false;
            }
        }

        if (isReport) {
            notificationService.reportStrategyExt(
                    resBuy,
                    strategy,
                    candle,
                    "Date|open|high|low|close|ema2|profit|loss|limitPrice|lossAvg|deadLineTop|investBottom|investTop|smaTube|strategy"
                            + "|emaBlue1|emaRed|emaGreen|emaBlue|max|min|zs|waitMax|maxBuy|limitPrice",
                    "{} | {} | {} | {} | {} | | {} | {} | | {} | ||||by {}"
                            + "| {} | {} | {} | {} | {} | {} | {} | {} | {} |",
                    printDateTime(candle.getDateTime()),
                    candle.getOpenPrice(),
                    candle.getHighestPrice(),
                    candle.getLowestPrice(),
                    candle.getClosingPrice(),
                    "",
                    "",
                    "",
                    annotation,
                    blue == null ? "" : blue,
                    red == null ? "" : red,
                    green == null ? "" : green,
                    blue == null ? "" : blue,
                    isMax ? middleCandle.getHighestPrice() : "",
                    isMin ? middleCandle.getLowestPrice() : "",
                    zs == null ? "" : zs,
                    lastFMaxCandle == null ? "" : lastFMaxCandle.getHighestPrice(),
                    lastFMaxCandle == null ? "" : lastFMaxCandle.getClosingPrice().max(lastFMaxCandle.getOpenPrice())
            );
        }
        return resBuy;
    }

    @Override
    public boolean isShouldSell(AAlligatorStrategy strategy, CandleDomainEntity candle, BigDecimal purchaseRate) {
        var annotation = "";
        var res = false;

        var blue = getAlligatorBlue(candle.getFigi(), candle.getDateTime(), strategy);
        var red = getAlligatorRed(candle.getFigi(), candle.getDateTime(), strategy);
        var green = getAlligatorGreen(candle.getFigi(), candle.getDateTime(), strategy);

        var candleMinMaxList = getCandlesByFigiByLength(candle.getFigi(), candle.getDateTime(), 5, strategy.getInterval());
        var maxCandle = candleMinMaxList.stream().reduce((first, second) ->
                first.getHighestPrice().compareTo(second.getHighestPrice()) > 0 ? first : second
        ).orElse(null);
        var minCandle = candleMinMaxList.stream().reduce((first, second) ->
                first.getLowestPrice().compareTo(second.getLowestPrice()) < 0 ? first : second
        ).orElse(null);
        var middleCandle = candleMinMaxList.get(2);
        var isMax = maxCandle == middleCandle;
        var isMin = minCandle == middleCandle;

        Double zs = null;
        if (green != null) {
            zs = green + (green - blue) * 1.618;
            if (red > candle.getClosingPrice().doubleValue()) {
                annotation += " red stop lost OK";
                res = true;
            }
        }

        Double limitPrice = null;
        if (
                zs != null
                && zs > green
                && green > red
                && red > blue
        ) {
            limitPrice = zs;
        }
        if (limitPrice != null) {
            var sellLimitCriteria = strategy.getSellLimitCriteria(candle.getFigi());
            Float newLimitPercent = (float) ((100.f * (limitPrice.floatValue() - purchaseRate.floatValue()) / Math.abs(purchaseRate.floatValue())));
            Float newGreenPercent = (float) ((100.f * (zs - green) / Math.abs(green)));
            var average = getAveragePercent(candle.getFigi(), candle.getDateTime(), strategy);
            annotation += " average=" + printPrice(average);
            Float newGreenPercentAverage = (float) (newGreenPercent / average);
            Float newLimitPercentAverage = (float) (newLimitPercent / average);

            annotation += " limitPrice=" + printPrice(limitPrice);
            annotation += " newLimitPercent=" + printPrice(newLimitPercent);
            annotation += " newLimitPercentAverage=" + printPrice(newLimitPercentAverage);
            annotation += " newGreenPercent=" + printPrice(newGreenPercent);
            annotation += " newGreenPercentAverage=" + printPrice(newGreenPercentAverage);
            annotation += " origProfitPercent=" + strategy.getSellLimitCriteriaOrig().getExitProfitPercent();
            if (
                    newLimitPercentAverage > strategy.getSellLimitCriteriaOrig().getExitProfitPercent()
                    && newGreenPercentAverage > strategy.getSellLimitCriteriaOrig().getExitProfitPercent()
                    && false
            ) {
                sellLimitCriteria.setExitProfitPercent(newLimitPercent);
                strategy.setSellLimitCriteria(candle.getFigi(), sellLimitCriteria);
            } else {
                limitPrice = null;
            }

            if (res) {
                var alligatorLengthAverage = getAlligatorLengthAverage(candle.getFigi(), candle.getDateTime(), strategy);
                var curAlligatorMouth = getAlligatorMouth(candle.getFigi(), candle.getDateTime(), strategy);
                annotation += " MonthBegin=" + printDateTime(curAlligatorMouth.getCandleBegin().getDateTime());
                annotation += " MonthEnd=" + printDateTime(curAlligatorMouth.getCandleEnd().getDateTime());
                var curAlligatorLength = curAlligatorMouth.getSize();
                annotation += " alligatorLengthAverage=" + printPrice(alligatorLengthAverage);
                annotation += " curAlligatorLength=" + curAlligatorLength;
                if (curAlligatorLength < alligatorLengthAverage / 3) {
                    annotation += " skip by AlligatorLength<" + printPrice(alligatorLengthAverage / 3);
                    res = false;
                }
                if (
                        res
                        && newGreenPercentAverage < strategy.getMinGreenPercent()
                        && curAlligatorLength < alligatorLengthAverage
                ) {
                    annotation += " skip by percent<" + strategy.getMinGreenPercent();
                    annotation += " AlligatorLength<" + printPrice(alligatorLengthAverage);
                    res = false;
                }
            }
        }

        if (res) {
            var alligatorLengthAverage = getAlligatorLengthAverage(candle.getFigi(), candle.getDateTime(), strategy);
            var curAlligatorMouth = getAlligatorMouth(candle.getFigi(), candle.getDateTime(), strategy);
            annotation += " MonthBegin=" + printDateTime(curAlligatorMouth.getCandleBegin().getDateTime());
            annotation += " MonthEnd=" + printDateTime(curAlligatorMouth.getCandleEnd().getDateTime());
            var curAlligatorLength = curAlligatorMouth.getSize();
            annotation += " alligatorLengthAverage=" + printPrice(alligatorLengthAverage);
            annotation += " curAlligatorLength=" + curAlligatorLength;
            if (curAlligatorLength < alligatorLengthAverage / 3) {
                annotation += " skip by AlligatorLength<" + printPrice(alligatorLengthAverage / 3);
                res = false;
            }
        }
        if (res) {
            if (isShouldBuyInternal(strategy, candle, false)) {
                annotation += " skip by buy";
                res = false;
            }
        }

        if (
                limitPrice != null
                && candle.getClosingPrice().doubleValue() > limitPrice
        ) {
            annotation += " limit OK";
            res = true;
        }

        notificationService.reportStrategyExt(
                res,
                strategy,
                candle,
                "Date|open|high|low|close|ema2|profit|loss|limitPrice|lossAvg|deadLineTop|investBottom|investTop|smaTube|strategy"
                        + "|emaBlue1|emaRed|emaGreen|emaBlue|max|min|zs|waitMax|maxBuy|limitPrice",
                "{} | {} | {} | {} | {} | | {} | {} | | {} | ||||sell {}"
                        + "| {} | {} | {} | {} | {} | {} | {} ||| {}",
                printDateTime(candle.getDateTime()),
                candle.getOpenPrice(),
                candle.getHighestPrice(),
                candle.getLowestPrice(),
                candle.getClosingPrice(),
                "",
                "",
                "",
                annotation,
                blue == null ? "" : blue,
                red == null ? "" : red,
                green == null ? "" : green,
                blue == null ? "" : blue,
                isMax ? middleCandle.getHighestPrice() : "",
                isMin ? middleCandle.getLowestPrice() : "",
                zs == null ? "" : zs,
                limitPrice == null ? "" : limitPrice
        );
        return res;
    }

    @Override
    public AStrategy.Type getStrategyType() {
        return AStrategy.Type.alligator;
    }

    @Override
    public ICalculatorShortService cloneService(IOrderService orderService) throws CloneNotSupportedException {
        var obj = new AlligatorService();
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
    public boolean isTrendBuy(AAlligatorStrategy strategy, CandleDomainEntity candle) {
        return false;
    }

    @Override
    public boolean isTrendSell(AAlligatorStrategy strategy, CandleDomainEntity candle) {
        return false;
    }

    private Double getAlligatorLengthAverage(
            String figi,
            OffsetDateTime currentDateTime,
            AAlligatorStrategy strategy
    ) {
        List<Double> ret = new ArrayList<Double>();
        for(var i = 0; i < strategy.getMaxDeepAlligatorMouth(); i++) {
            var mouth = getAlligatorMouth(figi, currentDateTime, strategy);
            if (mouth.isFindBegin && mouth.isFindEnd) {
                ret.add(Double.valueOf(mouth.size));
            } else if (ret.size() > 0) {
                break;
            }
            currentDateTime = mouth.candleBegin.getDateTime();
        }
        var average = ret.stream().mapToDouble(a -> a).average().orElse(0);
        return average;
        //var dispersia = Math.sqrt(ret.stream().mapToDouble(a -> (a - average) * (a - average)).sum() / ret.size());
        //var retFiltered = ret.stream().filter(a -> a >= average - dispersia && a <= average + dispersia);
        //return retFiltered.mapToDouble(a -> a).average().orElse(average);
    }

    @Builder
    @Data
    public static class AlligatorMouth {
        CandleDomainEntity candleBegin;
        CandleDomainEntity candleEnd;
        Integer size;
        Boolean isFindBegin;
        Boolean isFindEnd;
    }

    private AlligatorMouth getAlligatorMouth(
            String figi,
            OffsetDateTime currentDateTime,
            AAlligatorStrategy strategy
    ) {
        var candleList = getCandlesByFigiByLength(figi, currentDateTime, strategy.getMaxDeep(), strategy.getInterval());
        Boolean isUpCur = null;
        Integer size = 0;
        var resBuilder = AlligatorMouth.builder()
                .isFindBegin(false)
                .isFindEnd(false)
                .size(0);
        for (var i = candleList.size() - 1; i >= 0; i--) {
            var blue = getAlligatorBlue(figi, candleList.get(i).getDateTime(), strategy);
            var red = getAlligatorRed(figi, candleList.get(i).getDateTime(), strategy);
            var green = getAlligatorGreen(figi, candleList.get(i).getDateTime(), strategy);
            if (blue == null || red == null || green == null) {
                break;
            }
            var isUp = green > red && red > blue;
            var isDown = green < red && red < blue;
            if (null == isUpCur && isUp) {
                isUpCur = true;
                resBuilder.candleEnd(candleList.get(i));
                resBuilder.isFindEnd(i < candleList.size() - 1);
            }
            if (null == isUpCur && isDown) {
                isUpCur = false;
                resBuilder.candleEnd(candleList.get(i));
                resBuilder.isFindEnd(i < candleList.size() - 1);
            }
            if (null != isUpCur && isUpCur && !isUp) {
                resBuilder.isFindBegin(true);
                break;
            }
            if (null != isUpCur && !isUpCur && !isDown) {
                resBuilder.isFindBegin(true);
                break;
            }
            if (null != isUpCur) {
                resBuilder.candleBegin(candleList.get(i));
                size++;
            }
        }
        return resBuilder
                .size(size)
                .build();
    }

    private Double getAveragePercent(
            String figi,
            OffsetDateTime currentDateTime,
            AAlligatorStrategy strategy
    ) {
        var candleList = getCandlesByFigiByLength(figi, currentDateTime, strategy.getMaxDeep(), strategy.getInterval());
        Double average = 0.0;
        var size = strategy.getMaxDeep();
        for (var i = 0; i < candleList.size(); i++) {
            var blue = getAlligatorBlue(figi, candleList.get(i).getDateTime(), strategy);
            var green = getAlligatorGreen(figi, candleList.get(i).getDateTime(), strategy);
            if (blue == null || green == null) {
                size--;
                continue;
            }
            average += 100 * Math.abs(blue - green) / Math.abs(green) / size;
        }
        return average;
    }

    private CandleDomainEntity getLastFMaxCandle(
            String figi,
            OffsetDateTime currentDateTime,
            AAlligatorStrategy strategy
    ) {
        var candleList = getCandlesByFigiByLength(figi, currentDateTime, strategy.getMaxDeep(), strategy.getInterval());
        if (candleList == null) {
            return null;
        }
        CandleDomainEntity maxCandle = null;
        for (var i = candleList.size() - 1 - 2; i >= 2; i--) {
            var curCandleList = candleList.subList(i - 2, i + 3);
            var middleCandle = curCandleList.get(2);
            var blue = getAlligatorBlue(figi, middleCandle.getDateTime(), strategy);
            var red = getAlligatorRed(figi, middleCandle.getDateTime(), strategy);
            var green = getAlligatorGreen(figi, middleCandle.getDateTime(), strategy);
            if (
                    blue == null
                    || !(
                        (blue < red && red < green)
                        || middleCandle.getLowestPrice().doubleValue() > red
                        || middleCandle.getHighestPrice().doubleValue() > red
                    )
            ) {
                break;
            }
            var curMaxCandle = curCandleList.stream().reduce((first, second) ->
                    first.getHighestPrice().compareTo(second.getHighestPrice()) > 0 ? first : second
            ).orElse(null);
            var isMax = middleCandle == curMaxCandle;
            if (
                    isMax
                    //&& (
                    //        maxCandle == null
                    //        || middleCandle.getHighestPrice().compareTo(maxCandle.getHighestPrice()) > 0
                    //)
                    && middleCandle.getLowestPrice().doubleValue() > Math.max(blue, green)
            ) {
                maxCandle = middleCandle;
            }
        }
        return maxCandle;
    }

    private Double getAlligatorBlue(
            String figi,
            OffsetDateTime currentDateTime,
            AAlligatorStrategy strategy
    ) {
        String key = "Blue" + strategy.getExtName() + figi + currentDateTime;
        var ret = getCashedValueDouble(key);
        if (ret != null) {
            return ret;
        }
        List<Double> list = getSmma(figi, currentDateTime, strategy.getSmaBlueLength(), strategy.getInterval(), CandleDomainEntity::getMedianPrice, strategy.getSmaBlueOffset());
        var v = list == null ? null : list.get(0);
        addCashedValueDouble(key, v);
        return v;
    }

    private Double getAlligatorRed(
            String figi,
            OffsetDateTime currentDateTime,
            AAlligatorStrategy strategy
    ) {
        String key = "Red" + strategy.getExtName() + figi + currentDateTime;
        var ret = getCashedValueDouble(key);
        if (ret != null) {
            return ret;
        }
        List<Double> list = getSmma(figi, currentDateTime, strategy.getSmaRedLength(), strategy.getInterval(), CandleDomainEntity::getMedianPrice, strategy.getSmaRedOffset());
        var v = list == null ? null : list.get(0);
        addCashedValueDouble(key, v);
        return v;
    }

    private Double getAlligatorGreen(
            String figi,
            OffsetDateTime currentDateTime,
            AAlligatorStrategy strategy
    ) {
        String key = "Green" + strategy.getExtName() + figi + currentDateTime;
        var ret = getCashedValueDouble(key);
        if (ret != null) {
            return ret;
        }
        List<Double> list = getSmma(figi, currentDateTime, strategy.getSmaGreenLength(), strategy.getInterval(), CandleDomainEntity::getMedianPrice, strategy.getSmaGreenOffset());
        var v = list == null ? null : list.get(0);
        addCashedValueDouble(key, v);
        return v;
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
        return candleHistoryService.getCandlesByFigiByLength(figi,
                currentDateTime, length, interval);
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
            return size() > 1000;
        }
    };

    private synchronized Double getCashedValueDouble(String indent)
    {
        if (doubleCashMap.containsKey(indent)) {
            return doubleCashMap.get(indent);
        }
        if (doubleCashMap.size() > 1000) {
            doubleCashMap.clear();
        }
        return null;
    }

    private synchronized void addCashedValueDouble(String indent, Double v)
    {
        doubleCashMap.put(indent, v);
    }
}
