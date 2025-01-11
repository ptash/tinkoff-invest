package com.struchev.invest.service.processor;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.service.candle.CandleHistoryReverseForShortService;
import com.struchev.invest.service.candle.ICandleHistoryService;
import com.struchev.invest.service.notification.INotificationService;
import com.struchev.invest.service.order.IOrderService;
import com.struchev.invest.strategy.AStrategy;
import com.struchev.invest.strategy.IStrategyShort;
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

    private final CandleHistoryReverseForShortService candleHistoryReverseForShortService;

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
        var resBuyMax = false;
        var resBuyMin = false;

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
        BigDecimal waitMax = null;
        BigDecimal waitMax2 = null;
        BigDecimal waitMaxBuy = null;
        BigDecimal delta = null;
        if (null != lastFMaxCandle && green != null && blue != null) {
            waitMax = lastFMaxCandle.getHighestPrice();
            delta = lastFMaxCandle.getHighestPrice().subtract(lastFMaxCandle.getClosingPrice()).abs()
                    .min(lastFMaxCandle.getHighestPrice().subtract(lastFMaxCandle.getOpenPrice()).abs());
            var candleListMax = candleHistoryService.getCandlesByFigiBetweenDateTimes(candle.getFigi(), lastFMaxCandle.getDateTime(), candle.getDateTime(), strategy.getInterval());
            var maxIntervalCandle = candleListMax.stream().reduce((first, second) ->
                    first.getHighestPrice().compareTo(second.getHighestPrice()) > 0 ? first : second
            ).orElse(null);
            var maxFirstIntervalCandle = candleListMax.stream().filter(v ->
                    v.getHighestPrice().compareTo(lastFMaxCandle.getHighestPrice()) > 0
            ).findFirst().orElse(null);
            if (
                    maxIntervalCandle != lastFMaxCandle
                    && maxFirstIntervalCandle != null
                    && maxIntervalCandle.getHighestPrice().compareTo(lastFMaxCandle.getHighestPrice()) > 0
            ) {
                var blueMax = getAlligatorBlue(candle.getFigi(), maxFirstIntervalCandle.getDateTime(), strategy);
                var greenMax = getAlligatorGreen(candle.getFigi(), maxFirstIntervalCandle.getDateTime(), strategy);
                if (blueMax != null && greenMax != null) {
                    var averageMax = getAveragePercent(candle.getFigi(), maxFirstIntervalCandle.getDateTime(), strategy);
                    var zsMax = greenMax + (greenMax - blueMax);
                    Float newGreenPercentMax = (float) ((100.f * (zsMax - greenMax) / Math.abs(greenMax)));
                    annotation += " averageMax=" + printPrice(averageMax);
                    Float newGreenPercentAverage = (float) (newGreenPercentMax / averageMax);
                    annotation += " newGreenPercentAverageMax=" + printPrice(newGreenPercentAverage);
                    delta = delta.max(BigDecimal.valueOf(Math.abs(greenMax - blueMax) / newGreenPercentAverage));
                }
                waitMax2 = waitMax.add(delta);
                var isMax2 = maxIntervalCandle.getHighestPrice().compareTo(waitMax2) > 0;
                var isUp = currentPrice.doubleValue() > blue
                        && green > red
                        && red > blue;
                waitMaxBuy = waitMax.subtract(delta);
                if (
                        isMax2
                        && isUp
                ) {
                    if (
                            currentPrice.compareTo(waitMaxBuy) < 0
                    ) {
                        setOrderBigDecimalData(strategy, candle, "priceWanted", currentPrice.max(lastFMaxCandle.getClosingPrice().max(lastFMaxCandle.getOpenPrice())));
                        annotation += " OK by DOWN after MAX";
                        resBuy = true;
                    }
                    if (
                            currentPrice.compareTo(waitMaxBuy) > 0
                    ) {
                        setOrderBigDecimalData(strategy, candle, "priceWanted", currentPrice.max(lastFMaxCandle.getClosingPrice().max(lastFMaxCandle.getOpenPrice())));
                        annotation += " OK by UP";
                        resBuy = true;
                        resBuyMax = true;
                    }
                } else {
                    if (
                            currentPrice.compareTo(waitMaxBuy) < 0
                            && isUp
                    ) {
                        setOrderBigDecimalData(strategy, candle, "priceWanted", currentPrice.max(lastFMaxCandle.getClosingPrice().max(lastFMaxCandle.getOpenPrice())));
                        annotation += " OK by DOWN";
                        resBuyMin = true;
                    }
                }
            }
        }

        Double zs = null;
        var average = getAveragePercent(candle.getFigi(), candle.getDateTime(), strategy);
        if (green != null && blue != null) {
            zs = green + (green - blue) * 1.618;
            Float newGreenPercent = (float) ((100.f * (zs - green) / Math.abs(green)));
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
            if (
                    !resBuy
                    && resBuyMin
                    && newGreenPercentAverage > strategy.getMinGreenPercent()
            ) {
                annotation += " OK by DOWN percent>" + strategy.getMinGreenPercent();
                resBuy = true;
            }
            if (!resBuyMax && currentPrice.doubleValue() > green && newGreenPercentAverage < strategy.getMinGreenPercent()) {
                annotation += " skip by percent<" + strategy.getMinGreenPercent();
                resBuy = false;
            }
            if (
                    currentPrice.doubleValue() < green
                    && (maxBuyPercentAverage != null && maxBuyPercentAverage > strategy.getMinGreenPercent())
                    && !resBuyMax
            ) {
                annotation += " skip by buy percent>" + strategy.getMinGreenPercent();
                resBuy = false;
            }
            if (!resBuyMax && newGreenPercentAverage > strategy.getMaxGreenPercent()) {
                annotation += " skip by percent>" + strategy.getMaxGreenPercent();
                resBuy = false;
            }
            if (resBuyMax && newGreenPercentAverage < strategy.getMinGreenPercent()) {
                annotation += " skip max by percent<" + strategy.getMinGreenPercent();
                resBuy = false;
            }
        }
        if (resBuy) {
            var alligatorAverage = getAlligatorLengthAverage(candle.getFigi(), candle.getDateTime(), strategy);
            var curAlligatorMouth = getAlligatorMouth(candle.getFigi(), candle.getDateTime(), strategy);
            annotation += " MonthBegin=" + printDateTime(curAlligatorMouth.getCandleBegin().getDateTime());
            annotation += " MonthEnd=" + printDateTime(curAlligatorMouth.getCandleEnd().getDateTime());
            var curAlligatorLength = curAlligatorMouth.getSize();
            annotation += " alligatorLengthAverage=" + alligatorAverage.getSize();
            //annotation += " Average=" + alligatorAverage.getAnnotation();
            annotation += " curAlligatorLength=" + curAlligatorLength;
            if (resBuyMax && curAlligatorLength > alligatorAverage.getSize()) {
                annotation += " skip max by AlligatorLength>" + alligatorAverage.getSize();
                resBuy = false;
            }
            if (!resBuyMax && curAlligatorLength > alligatorAverage.getSize() / strategy.getSellSkipCurAlligatorLengthDivider()) {
                annotation += " skip by AlligatorLength>" + printPrice(alligatorAverage.getSize() / strategy.getSellSkipCurAlligatorLengthDivider());
                resBuy = false;
            }
        }
        var isDayEnd = false;
        if (resBuy) {
            var alligatorAverage = getAlligatorLengthAverage(candle.getFigi(), candle.getDateTime(), strategy);
            var orderAlligatorMouth = getAlligatorMouth(candle.getFigi(), candle.getDateTime(), strategy);
            var greenMonthBegin = getAlligatorGreen(candle.getFigi(), orderAlligatorMouth.getCandleBegin().getDateTime(), strategy);
            var startPrice = greenMonthBegin;
            var purchaseRate = candle.getClosingPrice();
            Double limitPercent;
            if (strategy.getLimitPercentByCandle() > 0) {
                annotation += " limitPercentByCandle=" + printPrice(strategy.getLimitPercentByCandle());
                limitPercent = Math.max(1, (alligatorAverage.getSize()))
                        * strategy.getLimitPercentByCandle() * average;
            } else {
                var limitPercentByCandle = Math.abs(100. * alligatorAverage.getPrice() / alligatorAverage.getSize() / purchaseRate.doubleValue());
                annotation += " alligatorAveragePrice=" + printPrice(alligatorAverage.getPrice());
                annotation += " limitPercentByCandle=" + printPrice(limitPercentByCandle);
                limitPercent = Math.max(1, (alligatorAverage.getSize()))
                        * limitPercentByCandle;
            }
            annotation += " limitPercent=" + printPrice(limitPercent);
            Double limitPrice = startPrice.doubleValue()
                    + Math.abs((startPrice.doubleValue() / 100.) * limitPercent)
                    - delta.doubleValue();

            Float newLimitPercent = (float) ((100.f * (limitPrice.floatValue() - purchaseRate.floatValue()) / Math.abs(purchaseRate.floatValue())));
            annotation += " limitPrice=" + printPrice(limitPrice);
            annotation += " newLimitPercent=" + printPrice(newLimitPercent);
            if (newLimitPercent < 0) {
                limitPrice = limitPrice
                        + Math.abs((startPrice.doubleValue() / 100.) * limitPercent)
                        - delta.doubleValue();
                newLimitPercent = (float) ((100.f * (limitPrice.floatValue() - purchaseRate.floatValue()) / Math.abs(purchaseRate.floatValue())));
                annotation += " limitPrice=" + printPrice(limitPrice);
                annotation += " newLimitPercent=" + printPrice(newLimitPercent);
            }
            //Float newLimitPercentAverage = (float) (newLimitPercent / average);

            annotation += " origProfitPercent=" + strategy.getSellLimitCriteriaOrig().getExitProfitPercent();
            if (newLimitPercent < strategy.getSellLimitCriteriaOrig().getExitProfitPercent()) {
                annotation += " SKIP ProfitPercent";
                resBuy = false;
            } else {
                setOrderBigDecimalData(strategy, candle, "limitPrice", BigDecimal.valueOf(limitPrice));
                setOrderBigDecimalData(strategy, candle, "limitPercent", BigDecimal.valueOf(newLimitPercent));
            }

            if (null != strategy.getDayTimeEndBuy()) {
                var dayEndDateTime = strategy.getDayTimeEndBuy();
                var curDayEnd = candle.getDateTime()
                        .withHour(dayEndDateTime.getHour())
                        .withMinute(dayEndDateTime.getMinute())
                        .withSecond(dayEndDateTime.getSecond());
                annotation += " curDayEnd=" + printDateTime(curDayEnd);
                if (candle.getDateTime().compareTo(curDayEnd) > 0) {
                    annotation += " SKIP by day end";
                    resBuy = false;
                    isDayEnd = true;
                }
            } else if (null != strategy.getDayTimeEndTrading()) {
                var lengthToDayEnd = getLengthToDayEnd(candle.getFigi(), candle.getDateTime(), strategy.getDayTimeEndTrading(), strategy.getInterval());
                annotation += " lengthToDayEnd=" + lengthToDayEnd;
                if (
                        lengthToDayEnd != null
                        && lengthToDayEnd < (alligatorAverage.getSize() - orderAlligatorMouth.getSize())
                ) {
                    annotation += " SKIP lengthToDayEnd<" + (alligatorAverage.getSize() - orderAlligatorMouth.getSize());
                    resBuy = false;
                    isDayEnd = true;
                }
            }
        }

        if (isReport) {
            notificationService.reportStrategyExt(
                    resBuy,
                    strategy,
                    candle,
                    "Date|open|high|low|close|ema2|profit|loss|limitPrice|lossAvg|deadLineTop|investBottom|investTop|smaTube|strategy"
                            + "|emaBlue1|emaRed|emaGreen|emaBlue|max|min|zs|waitMax|maxBuy|stopLoss|waitMax2|isDayEnd",
                    "{} | {} | {} | {} | {} | | {} | {} | | {} | ||||by {}"
                            + "| {} | {} | {} | {} | {} | {} | {} | {} | {} || {} | {}",
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
                    waitMax == null ? "" : waitMax,
                    waitMaxBuy == null ? "" : waitMaxBuy,
                    waitMax2 == null ? "" : waitMax2,
                    isDayEnd ? candle.getLowestPrice().subtract(candle.getLowestPrice().abs().multiply(BigDecimal.valueOf(0.01))) : ""
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
        Double stopLoss = null;
        Boolean isStopLossForce = false;
        if (green != null) {
            stopLoss = red;
            zs = green + (green - blue) * 1.618;
        }

        Double limitPrice = null;
        AlligatorMouthAverage alligatorAverage = null;
        AlligatorMouth curAlligatorMouth = null;
        if (green != null && blue != null) {
            var stopLossForce = blue - Math.abs(red - blue);
            var sellLimitCriteria = strategy.getSellLimitCriteria(candle.getFigi());
            Float newGreenPercent = (float) ((100.f * (zs - green) / Math.abs(green)));
            var average = getAveragePercent(candle.getFigi(), candle.getDateTime(), strategy);
            annotation += " average=" + printPrice(average);
            Float newGreenPercentAverage = (float) (newGreenPercent / average);

            alligatorAverage = getAlligatorLengthAverage(candle.getFigi(), candle.getDateTime(), strategy);
            curAlligatorMouth = getAlligatorMouth(candle.getFigi(), candle.getDateTime(), strategy);
            annotation += " MonthBegin=" + printDateTime(curAlligatorMouth.getCandleBegin().getDateTime());
            annotation += " MonthEnd=" + printDateTime(curAlligatorMouth.getCandleEnd().getDateTime());
            var curAlligatorLength = curAlligatorMouth.getSize();
            annotation += " alligatorLengthAverage=" + alligatorAverage.getSize();
            //annotation += " Average=" + alligatorAverage.getAnnotation();
            annotation += " curAlligatorLength=" + curAlligatorLength;
            if (curAlligatorLength < alligatorAverage.getSize() / 3. + 1) {
                annotation += " skip by AlligatorLength<" + printPrice(alligatorAverage.getSize() / 3.);
                stopLoss = stopLossForce;
                isStopLossForce = true;
            }
            if (
                    (newGreenPercentAverage < strategy.getMinGreenPercent() && newGreenPercentAverage > 0)
                            && curAlligatorLength < alligatorAverage.getSize()
            ) {
                annotation += " skip by percent<" + strategy.getMinGreenPercent();
                annotation += " AlligatorLength<" + alligatorAverage.getSize();
                stopLoss = stopLossForce;
                isStopLossForce = true;
            }

            if (curAlligatorLength < alligatorAverage.getSize() / strategy.getSellSkipCurAlligatorLengthDivider() + 1) {
                annotation += " skip by AlligatorLength<" + alligatorAverage.getSize() / strategy.getSellSkipCurAlligatorLengthDivider();
                stopLoss = stopLossForce;
                isStopLossForce = true;
            }

            //limitPrice = green + strategy.getSellLimitCriteriaOrig().getExitProfitPercent() * Math.abs(green * average / 100.) * 1.618;
            var order = orderService.findActiveByFigiAndStrategy(candle.getFigi(), strategy);
            var orderAlligatorMouth = getAlligatorMouth(candle.getFigi(), order.getPurchaseDateTime(), strategy);
            annotation += " orderDate=" + printDateTime(order.getPurchaseDateTime());
            annotation += " orderAlligatorMouthSize=" + orderAlligatorMouth.getSize();
            annotation += " purchaseRate=" + printPrice(purchaseRate);
            Double limitPercent;
            /*if (strategy.getLimitPercentByCandle() > 0) {
                annotation += " limitPercentByCandle=" + printPrice(strategy.getLimitPercentByCandle());
                limitPercent = Math.max(1, (alligatorAverage.getSize() - orderAlligatorMouth.getSize()))
                        * strategy.getLimitPercentByCandle() * average;
            } else {
                var limitPercentByCandle = Math.abs(100. * alligatorAverage.getPrice() / alligatorAverage.getSize() / purchaseRate.doubleValue());
                annotation += " alligatorAveragePrice=" + printPrice(alligatorAverage.getPrice());
                annotation += " limitPercentByCandle=" + printPrice(limitPercentByCandle);
                limitPercent = Math.max(1, (alligatorAverage.getSize() - orderAlligatorMouth.getSize()))
                        * limitPercentByCandle;
            }*/
            //annotation += " limitPercent=" + printPrice(limitPercent);
            //limitPrice = purchaseRate.doubleValue()
            //        + Math.abs((purchaseRate.doubleValue() / 100.) * limitPercent);
            limitPrice = order.getDetails().getCurrentPrices().getOrDefault("limitPrice", BigDecimal.ZERO).doubleValue();
            Float newLimitPercent = order.getDetails().getCurrentPrices().getOrDefault("limitPercent", BigDecimal.ZERO).floatValue();

            //Float newLimitPercent = (float) ((100.f * (limitPrice.floatValue() - purchaseRate.floatValue()) / Math.abs(purchaseRate.floatValue())));
            //Float newLimitPercentAverage = (float) (newLimitPercent / average);

            annotation += " limitPrice=" + printPrice(limitPrice);
            annotation += " newLimitPercent=" + printPrice(newLimitPercent);
            //annotation += " newLimitPercentAverage=" + printPrice(newLimitPercentAverage);
            annotation += " newGreenPercent=" + printPrice(newGreenPercent);
            annotation += " newGreenPercentAverage=" + printPrice(newGreenPercentAverage);
            annotation += " origProfitPercent=" + strategy.getSellLimitCriteriaOrig().getExitProfitPercent();
            if (newLimitPercent < strategy.getSellLimitCriteriaOrig().getExitProfitPercent()) {
                newLimitPercent = strategy.getSellLimitCriteriaOrig().getExitProfitPercent();
                limitPrice = (double) (purchaseRate.floatValue() + Math.abs(purchaseRate.floatValue() * newLimitPercent / 100.f));
                annotation += " new limitPrice=" + printPrice(limitPrice);
                annotation += " new newLimitPercent=" + printPrice(newLimitPercent);
            }
            if (
                    true
                    //&& newLimitPercent > strategy.getSellLimitCriteriaOrig().getExitProfitPercent()
                    //&& newGreenPercentAverage > strategy.getSellLimitCriteriaOrig().getExitProfitPercent()
            ) {
                sellLimitCriteria.setExitProfitPercent(newLimitPercent);
                strategy.setSellLimitCriteria(candle.getFigi(), sellLimitCriteria);
            } else {
                limitPrice = null;
            }
        }
        if (null != stopLoss) {
            if (!isStopLossForce && candle.getClosingPrice().doubleValue() < stopLoss) {
                annotation += " stop lost OK";
                res = true;
            } else if (isStopLossForce && candle.getHighestPrice().doubleValue() < stopLoss) {
                annotation += " stop lost force OK";
                res = true;
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

        var isDayEnd = false;
        if (!res && alligatorAverage != null && curAlligatorMouth != null) {
            if (strategy.getDayTimeEndTrading() != null) {
                var dayEndDateTime = strategy.getDayTimeEndTrading();
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

        notificationService.reportStrategyExt(
                res,
                strategy,
                candle,
                "Date|open|high|low|close|ema2|profit|loss|limitPrice|lossAvg|deadLineTop|investBottom|investTop|smaTube|strategy"
                        + "|emaBlue1|emaRed|emaGreen|emaBlue|max|min|zs|waitMax|maxBuy|stopLoss|waitMax2|isDayEnd",
                "{} | {} | {} | {} | {} | | {} | {} | {} | {} | ||||sell {}"
                        + "| {} | {} | {} | {} | {} | {} | {} ||| {}|| {}",
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
                blue == null ? "" : blue,
                red == null ? "" : red,
                green == null ? "" : green,
                blue == null ? "" : blue,
                isMax ? middleCandle.getHighestPrice() : "",
                isMin ? middleCandle.getLowestPrice() : "",
                zs == null ? "" : zs,
                stopLoss == null ? "" : printPrice(stopLoss),
                isDayEnd ? candle.getLowestPrice().subtract(candle.getLowestPrice().abs().multiply(BigDecimal.valueOf(0.01))) : ""
        );
        return res;
    }

    @Override
    public AStrategy.Type getStrategyType() {
        return AStrategy.Type.alligator;
    }

    @Override
    public ICalculatorShortService cloneService(IOrderService orderService) throws CloneNotSupportedException {
        var obj = new AlligatorService(candleHistoryReverseForShortService);
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
    public static class AlligatorMouthAverage implements Cloneable {
        Integer size;
        String annotation;
        Double price;

        public AlligatorMouthAverage clone() {
            try {
                return (AlligatorMouthAverage)super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Map<String, AlligatorMouthAverage> alligatorMouthAverageCashMap = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 20;
        }
    };

    private synchronized AlligatorMouthAverage getAlligatorMouthAverageFromCache(
            String figi,
            AAlligatorStrategy strategy,
            OffsetDateTime currentDateTime
    ) {
        String indent;
        if (strategy.isShort()) {
            indent = figi + strategy.getInterval() + "Short" + printDateTime(currentDateTime);
        } else {
            indent = figi + strategy.getInterval() + "" + printDateTime(currentDateTime);
        }
        if (alligatorMouthAverageCashMap.containsKey(indent)) {
            return alligatorMouthAverageCashMap.get(indent);
        }
        return null;
    }

    private synchronized void addAlligatorMouthAverageToCache(
            String figi,
            AAlligatorStrategy strategy,
            OffsetDateTime currentDateTime,
            AlligatorMouthAverage v
    ) {
        String indent;
        String indent2;
        if (strategy.isShort()) {
            indent = figi + strategy.getInterval() + "Short" + printDateTime(currentDateTime);
            indent2 = figi + strategy.getInterval() + "" + printDateTime(currentDateTime);
        } else {
            indent = figi + strategy.getInterval() + "" + printDateTime(currentDateTime);
            indent2 = figi + strategy.getInterval() + "Short" + printDateTime(currentDateTime);
        }
        alligatorMouthAverageCashMap.put(indent, v);
        var v2 = v.clone();
        v2.setPrice(candleHistoryReverseForShortService.preparePrice(BigDecimal.valueOf(v.getPrice())).doubleValue());
        alligatorMouthAverageCashMap.put(indent2, v2);
    }

    private AlligatorMouthAverage getAlligatorLengthAverage(
            String figi,
            OffsetDateTime currentDateTime,
            AAlligatorStrategy strategy
    ) {
        List<Double> ret = new ArrayList<Double>();
        List<Double> retPrice = new ArrayList<Double>();
        var skipped = 0;
        String annotation = "";
        var mouthCur = getAlligatorMouth(figi, currentDateTime, strategy);
        annotation += " size=" + alligatorMouthAverageCashMap.size();
        var v = getAlligatorMouthAverageFromCache(figi, strategy, currentDateTime);
        if (v != null) {
            return v;
        }
        var candleList = getCandlesByFigiByLength(figi, currentDateTime, 1, strategy.getInterval());
        if (candleList != null && mouthCur.size > 1) {
            // можно в кеше поискать предыдущее значение
            v = getAlligatorMouthAverageFromCache(figi, strategy, candleList.get(0).getDateTime());
            if (v != null) {
                v.setAnnotation("from Cache " + v.getAnnotation());
                addAlligatorMouthAverageToCache(figi, strategy, currentDateTime, v);
                return v;
            }
        }
        var loopDateTime = mouthCur.candleBegin.getDateTime();
        for(var i = 0; i < strategy.getMaxDeepAlligatorMouth() + skipped; i++) {
            var mouth = getAlligatorMouth(figi, loopDateTime, strategy);
            annotation += " i=" + i;
            annotation += " size=" + mouth.size;
            annotation += " begin=" + printDateTime(mouth.getCandleBegin().getDateTime());
            annotation += " end=" + printDateTime(mouth.getCandleEnd().getDateTime());
            annotation += " isFindBegin=" + mouth.isFindBegin;
            annotation += " isFindEnd=" + mouth.isFindEnd;
            if (mouth.isFindBegin) {
                if (
                        strategy.isAlligatorMouthAverageLikeCur()
                        && ((mouthCur.isUp && !mouth.isUp) || (!mouthCur.isUp && mouth.isUp))
                ) {
                    skipped++;
                } else if (mouth.size > strategy.getAlligatorMouthAverageMinSize()) {
                    ret.add(Double.valueOf(mouth.size));
                    retPrice.add(Math.abs(mouth.candleMax.getHighestPrice().doubleValue() - mouth.candleMin.getLowestPrice().doubleValue()));
                } else {
                    skipped++;
                }
            } else if (ret.size() > 0) {
                break;
            }
            loopDateTime = mouth.candleBegin.getDateTime();
        }
        var average = ret.stream().mapToDouble(a -> a).average().orElse(0);
        v = AlligatorMouthAverage.builder()
                .size((int) Math.round(Math.ceil(average)))
                .price(retPrice.stream().mapToDouble(a -> a).average().orElse(0))
                .annotation(annotation)
                .build();
        v.setAnnotation("orig " + v.getAnnotation());
        addAlligatorMouthAverageToCache(figi, strategy, currentDateTime, v);
        return v;
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
        Boolean isUp;
        CandleDomainEntity candleMin;
        CandleDomainEntity candleMax;
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
            var candle = candleList.get(i);
            var blue = getAlligatorBlue(figi, candle.getDateTime(), strategy);
            var red = getAlligatorRed(figi, candle.getDateTime(), strategy);
            var green = getAlligatorGreen(figi, candle.getDateTime(), strategy);
            if (blue == null || red == null || green == null) {
                break;
            }
            var isUp = green > red && red > blue || candle.getHighestPrice().doubleValue() > red;
            var isDown = green < red && red < blue || candle.getLowestPrice().doubleValue() < red;
            if (null == isUpCur && isUp) {
                isUpCur = true;
                resBuilder.candleEnd(candleList.get(i));
                resBuilder.isFindEnd(i < candleList.size() - 1);
                resBuilder.isUp(true);
                resBuilder.candleMin(candleList.get(i));
                resBuilder.candleMax(candleList.get(i));
            }
            if (null == isUpCur && isDown) {
                isUpCur = false;
                resBuilder.candleEnd(candleList.get(i));
                resBuilder.isFindEnd(i < candleList.size() - 1);
                resBuilder.isUp(false);
                resBuilder.candleMin(candleList.get(i));
                resBuilder.candleMax(candleList.get(i));
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
                if (resBuilder.candleBegin.getHighestPrice().compareTo(resBuilder.candleMax.getHighestPrice()) > 0) {
                    resBuilder.candleMax(resBuilder.candleBegin);
                }
                if (resBuilder.candleBegin.getLowestPrice().compareTo(resBuilder.candleMin.getLowestPrice()) < 0) {
                    resBuilder.candleMin(resBuilder.candleBegin);
                }
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
        var ret = getCashedAlligatorDouble(figi, "Blue", currentDateTime, strategy);
        if (ret != null) {
            return ret;
        }
        List<Double> list = getSmma(figi, currentDateTime, strategy.getSmaBlueLength(), strategy.getInterval(), CandleDomainEntity::getMedianPrice, strategy.getSmaBlueOffset());
        var v = list == null ? null : list.get(0);
        addCashedAlligatorDouble(figi, "Blue", currentDateTime, strategy, v);
        return v;
    }

    private Double getAlligatorRed(
            String figi,
            OffsetDateTime currentDateTime,
            AAlligatorStrategy strategy
    ) {
        var ret = getCashedAlligatorDouble(figi, "Red", currentDateTime, strategy);
        if (ret != null) {
            return ret;
        }
        List<Double> list = getSmma(figi, currentDateTime, strategy.getSmaRedLength(), strategy.getInterval(), CandleDomainEntity::getMedianPrice, strategy.getSmaRedOffset());
        var v = list == null ? null : list.get(0);
        addCashedAlligatorDouble(figi, "Red", currentDateTime, strategy, v);
        return v;
    }

    private Double getAlligatorGreen(
            String figi,
            OffsetDateTime currentDateTime,
            AAlligatorStrategy strategy
    ) {
        var ret = getCashedAlligatorDouble(figi, "Green", currentDateTime, strategy);
        if (ret != null) {
            return ret;
        }
        List<Double> list = getSmma(figi, currentDateTime, strategy.getSmaGreenLength(), strategy.getInterval(), CandleDomainEntity::getMedianPrice, strategy.getSmaGreenOffset());
        var v = list == null ? null : list.get(0);
        addCashedAlligatorDouble(figi, "Green", currentDateTime, strategy, v);
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
        if (interval.equals("5min")) {
            currentDateTime = currentDateTime.minusMinutes(1);
        }
        if (interval.equals("15min")) {
            currentDateTime = currentDateTime.minusMinutes(1);
        }
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
            return size() > 500 * 2 * 3;
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

    private Double getCashedAlligatorDouble(
            String figi,
            String name,
            OffsetDateTime currentDateTime,
            AAlligatorStrategy strategy
    ) {
        String key;
        if (strategy.isShort()) {
            key = name + figi + "Short" + printDateTime(currentDateTime);
        } else {
            key = name + figi + "" + printDateTime(currentDateTime);
        }
        return getCashedValueDouble(key);
    }

    private void addCashedAlligatorDouble(
            String figi,
            String name,
            OffsetDateTime currentDateTime,
            AAlligatorStrategy strategy,
            Double v
    ) {
        String key;
        String key2;
        if (strategy.isShort()) {
            key = name + figi + "Short" + printDateTime(currentDateTime);
            key2 = name + figi + "" + printDateTime(currentDateTime);
        } else {
            key = name + figi + "" + printDateTime(currentDateTime);
            key2 = name + figi + "Short" + printDateTime(currentDateTime);
        }
        addCashedValueDouble(key, v);
        addCashedValueDouble(key2, candleHistoryReverseForShortService.preparePrice(BigDecimal.valueOf(v)).doubleValue());
    }
}
