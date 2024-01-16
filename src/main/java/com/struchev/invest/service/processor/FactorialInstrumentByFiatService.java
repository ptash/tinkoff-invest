package com.struchev.invest.service.processor;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.entity.CandleStrategyResultEntity;
import com.struchev.invest.entity.OrderDetails;
import com.struchev.invest.entity.OrderDomainEntity;
import com.struchev.invest.repository.CandleStrategyResultRepository;
import com.struchev.invest.service.candle.ICandleHistoryService;
import com.struchev.invest.service.notification.INotificationService;
import com.struchev.invest.service.order.IOrderService;
import com.struchev.invest.service.order.Order;
import com.struchev.invest.service.order.OrderService;
import com.struchev.invest.strategy.AStrategy;
import com.struchev.invest.strategy.instrument_by_fiat_factorial.AInstrumentByFiatFactorialStrategy;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class FactorialInstrumentByFiatService implements
        ICalculatorService<AInstrumentByFiatFactorialStrategy>,
        ICalculatorTrendService<AInstrumentByFiatFactorialStrategy>,
        ICalculatorShortService,
        ICalculatorDetailsService,
        Cloneable
{

    private final CandleStrategyResultRepository candleStrategyResultRepository;

    private ICandleHistoryService candleHistoryService;
    private INotificationService notificationService;

    private IOrderService orderService;

    @Override
    public boolean isTrendBuy(AInstrumentByFiatFactorialStrategy strategy, CandleDomainEntity candle) {
        var isTrendUp = getTrendUp(strategy, candle);
        if (null != isTrendUp) {
            return isTrendUp;
        }
        isTrendUp = isTrendBuyCalc(strategy, candle);
        setTrendUp(strategy, candle, isTrendUp);
        return isTrendUp;
    }


    public boolean isTrendBuyCalc(AInstrumentByFiatFactorialStrategy strategy, CandleDomainEntity candle) {
        var newStrategy = buildAvgStrategy(strategy, candle);
        if (null == newStrategy) {
            return false;
        }
        var buyCriteria = newStrategy.getBuyCriteria();
        clearCandleInterval(newStrategy, candle);
        var candleBuyRes = getCandleBuyRes(newStrategy, candle);
        if (candleBuyRes.isIntervalUp || candleBuyRes.candleIntervalBuy) {
            return candleBuyRes.isIntervalUp;
        }
        var candleIntervalUpDownData = candleBuyRes.candleIntervalUpDownData;
        if (candleIntervalUpDownData.maxCandle == null) {
            return false;
        }
        var candleIntervalUpDownDataPrev = getPrevCandleIntervalUpDownData(newStrategy, candleIntervalUpDownData);
        if (candleIntervalUpDownData.maxCandle == null) {
            return false;
        }

        var isIntervalUpResMaybe = calcIsIntervalUpMaybe(
                candle,
                null,
                buyCriteria,
                newStrategy,
                candleIntervalUpDownData,
                candleIntervalUpDownDataPrev
        );
        return (isIntervalUpResMaybe != null && isIntervalUpResMaybe.isIntervalUp);
    }

    private LinkedHashMap<String, Boolean> trendUpMap = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 100;
        }
    };

    public synchronized Boolean getTrendUp(AStrategy strategy, CandleDomainEntity candle)
    {
        String key = strategy.getExtName() + candle.getFigi() + printDateTime(candle.getDateTime());
        if (trendUpMap.containsKey(key)) {
            return trendUpMap.get(key);
        }
        return null;
    }

    private synchronized void setTrendUp(AStrategy strategy, CandleDomainEntity candle, Boolean value)
    {
        String key = strategy.getExtName() + candle.getFigi() + printDateTime(candle.getDateTime());
        trendUpMap.put(key, value);
    }

    @Override
    public boolean isTrendSell(AInstrumentByFiatFactorialStrategy strategy, CandleDomainEntity candle) {
        var isTrendDown = getTrendDown(strategy, candle);
        if (null != isTrendDown) {
            return isTrendDown;
        }
        var newStrategy = buildAvgStrategy(strategy, candle);
        isTrendDown = isTrendSellCalc(newStrategy, candle).isIntervalDown;
        setTrendUp(strategy, candle, isTrendDown);
        return isTrendDown;
    }

    private LinkedHashMap<String, Boolean> trendDownMap = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 100;
        }
    };

    @Builder
    @Data
    public static class CandleIntervalDownResult {
        String annotation;
        Boolean isIntervalDown;
        BigDecimal minDiffPercentPrev;
        BigDecimal minDiffPercent;
        BigDecimal maxDiffPercent;
        BigDecimal maxDiffCurPercent;
    }

    public CandleIntervalDownResult isTrendSellCalc(FactorialDiffAvgAdapterStrategy strategy, CandleDomainEntity candle) {
        var res = isTrendSellCalcInternal(strategy, candle);
        if (res.isIntervalDown) {
            var candleIntervalUpDownData = getCurCandleIntervalUpDownData(strategy, candle);
            if (candleIntervalUpDownData.maxCandle == null) {
                return res;
            }
            var candleIntervalUpDownDataPrev = getPrevCandleIntervalUpDownData(strategy, candleIntervalUpDownData);
            if (candleIntervalUpDownDataPrev.maxCandle == null) {
                return res;
            }
            var candleIntervalUpDownDataPrevPrev = getPrevCandleIntervalUpDownData(strategy, candleIntervalUpDownDataPrev);
            if (candleIntervalUpDownDataPrevPrev.maxCandle == null) {
                return res;
            }
            var order = orderService.findLastByFigiAndStrategy(candle.getFigi(), strategy);
            var upRes = calcIsIntervalUp(
                    candle,
                    order,
                    strategy,
                    candleIntervalUpDownData,
                    candleIntervalUpDownDataPrev,
                    candleIntervalUpDownDataPrevPrev,
                    getPrevCandleIntervalUpDownData(strategy, candleIntervalUpDownDataPrevPrev)
            );
            if (upRes.isIntervalUpAfterDown) {
                res.isIntervalDown = false;
                res.annotation += " SKIP DOWN after down";
            }
        }
        return res;
    }

    public CandleIntervalDownResult isTrendSellCalcInternal(AInstrumentByFiatFactorialStrategy strategy, CandleDomainEntity candle) {
        var res = isTrendSellCalcInternal2(strategy, candle);
        if (res.isIntervalDown && strategy.getSellCriteria().getIsMaxPriceByFib()) {
            var newStrategy = buildAvgStrategy(strategy, candle);
            var candleIntervalUpDownDataCur = getCurCandleIntervalUpDownData(newStrategy, candle);
            if (candleIntervalUpDownDataCur.maxCandle != null) {
                var maxClose = candleIntervalUpDownDataCur.maxClose;
                var minClose = candleIntervalUpDownDataCur.minClose;
                var k = 0;
                for (var i = 0; i < 10; i++) {
                    var candleIntervalUpDownDataPrev = getPrevCandleIntervalUpDownData(newStrategy, candleIntervalUpDownDataCur);
                    if (candleIntervalUpDownDataPrev.maxCandle == null) {
                        break;
                    }
                    if (candleIntervalUpDownDataCur.maxClose < candleIntervalUpDownDataPrev.maxClose) {
                        maxClose = candleIntervalUpDownDataPrev.maxClose;
                        minClose = Math.min(minClose, candleIntervalUpDownDataPrev.minClose);
                    } else {
                        k++;
                        minClose = Math.min(minClose, candleIntervalUpDownDataPrev.minClose);
                    }
                    if (k > 1) {
                        break;
                    }
                    candleIntervalUpDownDataCur = candleIntervalUpDownDataPrev;
                }
                res.annotation += " maxClose=" + printPrice(maxClose);
                res.annotation += " minClose=" + printPrice(minClose);
                var sellTrendPrice = minClose + Math.abs(maxClose - minClose) * (1 - 0.382);
                res.annotation += " sellTrendPrice=" + printPrice(sellTrendPrice) + ">=" + printPrice(candle.getClosingPrice().floatValue());
                if (candle.getClosingPrice().floatValue() >= sellTrendPrice) {
                    res.isIntervalDown = false;
                    res.annotation += " SKIP DOWN UNDER TREND";
                } else {
                    res.annotation += " DOWN UNDER TREND";
                }
            }
        }
        return res;
    }

    public CandleIntervalDownResult isTrendSellCalcInternal2(AInstrumentByFiatFactorialStrategy strategy, CandleDomainEntity candle) {
        var newStrategy = buildAvgStrategy(strategy, candle);
        var res = CandleIntervalDownResult.builder()
                .isIntervalDown(false)
                .annotation("")
                .build();
        if (null == newStrategy) {
            return res;
        }
        var candleIntervalUpDownData = getCurCandleIntervalUpDownData(newStrategy, candle);

        if (candleIntervalUpDownData.maxCandle == null) {
            return res;
        }
        var candleIntervalUpDownDataPrev = getPrevCandleIntervalUpDownData(newStrategy, candleIntervalUpDownData);
        if (candleIntervalUpDownDataPrev.maxCandle == null) {
            return res;
        }

        var candleIntervalUpDownDataPrevPrev = getPrevCandleIntervalUpDownData(newStrategy, candleIntervalUpDownDataPrev);
        if (candleIntervalUpDownDataPrevPrev.maxCandle == null) {
            return res;
        }

        var maxDiff = candleIntervalUpDownData.maxClose - candleIntervalUpDownDataPrev.maxClose;
        var maxDiffPrev = candleIntervalUpDownDataPrev.maxClose - candleIntervalUpDownDataPrevPrev.maxClose;
        var minDiff = candleIntervalUpDownDataPrev.minClose - candleIntervalUpDownData.minClose;
        var maxDiffCur = candle.getClosingPrice().floatValue() - candleIntervalUpDownDataPrev.maxClose;

        var sizePrev = Math.max(
                candleIntervalUpDownDataPrevPrev.maxClose - candleIntervalUpDownDataPrevPrev.minClose,
                candleIntervalUpDownDataPrev.maxClose - candleIntervalUpDownDataPrev.minClose
        );
        res.annotation += " sizePrev=" + printPrice(sizePrev);
        var sizePercentPrev = sizePrev / Math.abs(candleIntervalUpDownDataPrev.minClose) * 100f;
        if (sizePercentPrev < strategy.getBuyCriteria().getCandlePriceMinFactor()) {
            res.annotation += " sizePercentPrev=" + printPrice(sizePercentPrev);
            sizePrev = Math.abs(candleIntervalUpDownDataPrev.minClose) * strategy.getBuyCriteria().getCandlePriceMinFactor() / 100f;
        }
        var sizeOrig = Math.max(
                candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose,
                candleIntervalUpDownDataPrev.maxClose - candleIntervalUpDownDataPrev.minClose
        );
        var size = Math.max(
                sizePrev,
                sizeOrig
        );
        res.annotation += " size=" + printPrice(size);
        var sizePercent = size / Math.abs(candleIntervalUpDownData.minClose) * 100f;
        if (sizePercent < strategy.getBuyCriteria().getCandlePriceMinFactor()) {
            res.annotation += " sizePercent=" + printPrice(sizePercent);
            size = Math.abs(candleIntervalUpDownData.minClose) * strategy.getBuyCriteria().getCandlePriceMinFactor() / 100f;
        }
        var minDiffPercent = minDiff / size * 100f;
        var minDiffPrev = candleIntervalUpDownDataPrevPrev.minClose - candleIntervalUpDownDataPrev.minClose;
        var minDiffPercentPrev = minDiffPrev / sizePrev * 100f;
        res.annotation += " minDiffPrev=" + printPrice(minDiffPrev);
        res.annotation += " maxDiff=" + printPrice(maxDiff);
        res.annotation += " maxDiffPrev=" + printPrice(maxDiffPrev);
        res.annotation += " maxDiffCur=" + printPrice(maxDiffCur);
        res.annotation += " minDiff=" + printPrice(minDiff);
        res.annotation += " minDiffPercent=" + printPrice(minDiffPercent);
        res.annotation += " minDiffPercentPrev=" + printPrice(minDiffPercentPrev);
        res.minDiffPercentPrev = BigDecimal.valueOf(minDiffPercent);
        res.minDiffPercent = BigDecimal.valueOf(minDiffPercent);

        /*
        if (
                maxDiff > 0
                && maxDiffCur > 0
                && minDiff > 0
                && minDiff/maxDiff > 0.99
                && minDiff/maxDiffCur > 0.99
                && minDiffPercent > 25
                && candle.getClosingPrice().floatValue() > candleIntervalUpDownData.maxClose
        ) {
            res.isIntervalDown = true;
            return res;
        }*/

        if (
                maxDiff > 0
                && minDiff > 0
                && minDiff/maxDiff > 0.99
                && minDiffPercent > 25
                && minDiffPercent <= 50
        ) {
            res.annotation += " DOWN by 25";
            res.isIntervalDown = true;
            return res;
        }
        var isCheckMaxLassMin = false;
        if (
                minDiffPercentPrev > 50
                && minDiffPercent > 50
                && maxDiff < minDiff
                && candle.getClosingPrice().floatValue() < candleIntervalUpDownDataPrev.maxClose
        ) {
            res.annotation += " DOWN by 50 1";
            isCheckMaxLassMin = true;
            //res.isIntervalDown = true;
            //return res;
        }

        if (
                maxDiff < 0
                && maxDiffPrev < 0
                && candle.getClosingPrice().floatValue() < candleIntervalUpDownData.maxClose
        ) {
            res.annotation += " DOWN by down2";
            res.isIntervalDown = true;
            return res;
        }

        //if (candle.getClosingPrice().floatValue() <= candleIntervalUpDownData.maxClose) {
        //    return res;
        //}
        var cList = candleHistoryService.getCandlesByFigiBetweenDateTimes(candle.getFigi(), candleIntervalUpDownData.endPost.candle.getDateTime(), candle.getDateTime(), strategy.getInterval());
        //var maxClose = cList.stream().mapToDouble(v -> v.getClosingPrice().max(v.getOpenPrice()).doubleValue()).max().orElse(-1);
        var maxCandle = cList.stream().reduce((first, second) ->
                first.getClosingPrice().max(first.getOpenPrice()).compareTo(second.getClosingPrice().max(second.getOpenPrice())) > 0 ? first : second
        ).orElse(null);
        float maxClose = -1;
        double minClose = -1;
        if (maxCandle != null) {
            maxClose = maxCandle.getClosingPrice().max(maxCandle.getOpenPrice()).floatValue();
            minClose = cList.stream().filter(v -> !v.getDateTime().isAfter(maxCandle.getDateTime())).mapToDouble(v -> v.getClosingPrice().min(v.getOpenPrice()).doubleValue()).min().orElse(-1);
        }
        size = (float) Math.max(
                size,
                sizeOrig
        );
        res.annotation += " size=" + printPrice(size);
        sizePercent = (float) (size / Math.abs(minClose)) * 100f;
        if (sizePercent < strategy.getBuyCriteria().getCandlePriceMinFactor()) {
            res.annotation += " sizePercent=" + printPrice(sizePercent);
            size = (float) (Math.abs(minClose) * strategy.getBuyCriteria().getCandlePriceMinFactor() / 100f);
        }

        var maxDiffPrevPrev = maxDiffPrev;
        maxDiffPrev = maxDiff;
        maxDiff = (float) (maxClose - candleIntervalUpDownData.maxClose);
        maxDiffCur = candle.getClosingPrice().floatValue() - candleIntervalUpDownData.maxClose;
        minDiff = (float) (candleIntervalUpDownData.minClose - minClose);
        minDiffPercentPrev = minDiffPercent;
        minDiffPercent = minDiff / size * 100f;
        var maxDiffPercent = maxDiff / size * 100f;
        var maxDiffCurPercent = maxDiffCur / size * 100f;

        res.annotation += " maxDiff=" + printPrice(maxDiff);
        res.annotation += " minDiff=" + printPrice(minDiff);
        res.annotation += " minDiffPercent=" + printPrice(minDiffPercent);
        res.annotation += " maxDiffPercent=" + printPrice(maxDiffPercent);
        res.annotation += " maxDiffCurPercent=" + printPrice(maxDiffCurPercent);
        res.minDiffPercent = BigDecimal.valueOf(minDiffPercent);
        res.maxDiffPercent = BigDecimal.valueOf(maxDiffPercent);
        res.maxDiffCurPercent = BigDecimal.valueOf(maxDiffCurPercent);

        if (
                isCheckMaxLassMin
                && maxDiff < minDiff
        ) {
            res.annotation += " Max < Min";
            res.isIntervalDown = true;
            return res;
        }

        if (
                maxDiff > 0
                && minDiff > 0
                && minDiff/maxDiff > 0.8
                && minDiffPercent > 25
                && minDiffPercent <= 50
        ) {
            res.annotation += " DOWN by 25";
            res.isIntervalDown = true;
            return res;
        }
        if (
                minDiffPercentPrev > 50
                && minDiffPercent > 50
                && maxDiff < minDiff
        ) {
            res.annotation += " DOWN by 50";
            res.isIntervalDown = true;
            return res;
        }
        if (maxDiff < 0
            && maxDiffPrev < 0
        ) {
            res.annotation += " DOWN by down2";
            res.isIntervalDown = true;
            return res;
        }
        if (maxDiffPrevPrev < 0
            && maxDiffPrev < 0
        ) {
            if (minDiff > 0 && maxDiff < minDiff) {
                res.annotation += " DOWN by down2 2";
                res.isIntervalDown = true;
                return res;
            }
            if (minDiff <= 0) {
                var maxLimitPrice = Math.min(
                        candleIntervalUpDownDataPrevPrev.maxClose,
                        candleIntervalUpDownData.maxClose + (candleIntervalUpDownData.maxClose - minClose)
                );
                res.annotation += " maxLimitPrice=" + maxLimitPrice;
                if (maxClose < maxLimitPrice) {
                    res.annotation += " DOWN by down2 3";
                    res.isIntervalDown = true;
                    return res;
                }
            }
        }
        return res;
    }

    public synchronized Boolean getTrendDown(AStrategy strategy, CandleDomainEntity candle)
    {
        String key = strategy.getExtName() + candle.getFigi() + printDateTime(candle.getDateTime());
        if (trendDownMap.containsKey(key)) {
            return trendDownMap.get(key);
        }
        return null;
    }

    private synchronized void setTrendDown(AStrategy strategy, CandleDomainEntity candle, Boolean value)
    {
        String key = strategy.getExtName() + candle.getFigi() + printDateTime(candle.getDateTime());
        trendDownMap.put(key, value);
    }

    @Builder
    @Data
    public static class FactorialData {
        Integer i;
        Integer size;
        Integer length;
        Float diffPrice;
        Float diffPriceOpen;
        Float diffPriceCandle;
        Float diffPriceCandleMax;
        Float diffValue;
        Float diffTime;
        Float diff;
        List<CandleDomainEntity> candleList;
        List<CandleDomainEntity> candleListFeature;
        List<CandleDomainEntity> candleListPast;
        String info;
        Float expectProfit;
        Float expectLoss;
        Double profit;
        Double loss;
        OffsetDateTime dateTime;
    }

    @Builder
    @Data
    public static class BuyData {
        Double price;
        Double minPrice;
        Double maxPrice;
        Boolean isResOverProfit;
        Boolean isProfitSecond;
        OffsetDateTime dateTime;
    }

    public ICalculatorShortService cloneService(IOrderService orderService) {
        var obj = new FactorialInstrumentByFiatService(this.candleStrategyResultRepository);
        obj.orderService = orderService;
        return obj;
    }

    public void setCandleHistoryService(ICandleHistoryService candleHistoryService)
    {
        this.candleHistoryService = candleHistoryService;
    }

    public void setNotificationService(INotificationService notificationForShortService)
    {
        this.notificationService = notificationForShortService;
    }

    public void setOrderService(IOrderService orderService)
    {
        this.orderService = orderService;
    }

    public boolean isShouldBuy(AInstrumentByFiatFactorialStrategy strategy, CandleDomainEntity candle) {
        return isShouldBuyFactorial(strategy, candle);
    }

    public boolean isShouldBuyFactorial(AInstrumentByFiatFactorialStrategy strategy, CandleDomainEntity candle) {
        var curDateTime = candle.getDateTime();
        if (strategy.getFactorialInterval().equals("1hour")) {
            curDateTime = curDateTime.plusHours(1);
        }
        var candleList = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                candle.getDateTime(), 2, strategy.getFactorialInterval());
        var candleList2 = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                curDateTime, 1, strategy.getFactorialInterval());
        var curHourCandleForFactorial = getCandleHour(strategy, candle);
        var curHourCandle = candleList2.get(0);
        var factorial = findBestFactorialInPast(strategy, curHourCandleForFactorial);
        if (null == factorial) {
            return false;
        }
        String annotation = curHourCandleForFactorial.getDateTime().toString();
        var newStrategy = buildAvgStrategy(strategy, candle);
        if (null == newStrategy) {
            return false;
        }
        if (null != newStrategy.getPriceDiffAvgReal()) {
            annotation += " priceDiffAvgReal=" + newStrategy.getPriceDiffAvgReal();
        }
        var buyCriteria = newStrategy.getBuyCriteria();
        var sellCriteria = newStrategy.getSellCriteria();
        var res = false;
        var isResOverProfit = false;
        var isProfitSecond = false;
        Double curPriceMin = candle.getClosingPrice().doubleValue();
        Double curPriceMax = candle.getClosingPrice().doubleValue();
        if (buyCriteria.getIsCurPriceMinMax()) {
            curPriceMin = candle.getLowestPrice().doubleValue();
            curPriceMax = candle.getHighestPrice().doubleValue();
        }
        Double profit = factorial.getProfit();
        Double loss = factorial.getLoss();
        var futureProfit = 100f * (factorial.getProfit() - candle.getClosingPrice().doubleValue()) / candle.getClosingPrice().abs().doubleValue();
        var futureLoss = 100f * (candle.getClosingPrice().doubleValue() - factorial.getLoss()) / candle.getClosingPrice().abs().doubleValue();
        var closeMax = (candle.getClosingPrice().doubleValue() - factorial.getLoss())/(factorial.getProfit() - factorial.getLoss());
        annotation += " futureProfit=" + futureProfit;
        annotation += " futureLoss=" + futureLoss;
        annotation += " closeMax=" + closeMax;
        annotation += " expectProfit=" + factorial.getExpectProfit();
        annotation += " expectLoss=" + factorial.getExpectLoss();
        annotation += " info: " + factorial.getInfo();
        Double lossAvg = null;
        Double profitAvg = null;
        if (null != factorial && factorial.candleList != null) {
            annotation += "factorial from " + factorial.getCandleList().get(0).getDateTime()
                    + " to " + factorial.getCandleList().get(factorial.getCandleList().size() - 1).getDateTime() + " size=" + factorial.getSize()
                    + " diff=" + factorial.diffPrice
                    + " for from " + factorial.candleListPast.get(0).getDateTime();
            var expectProfit = factorial.getExpectProfit();
            var expectLoss = factorial.getExpectLoss();
            annotation += " expectProfit=" + expectProfit
                    + " expectLoss=" + expectLoss
                    + "(from " + factorial.candleList.get(0).getDateTime() + " to " + factorial.candleList.get(factorial.candleList.size() - 1).getDateTime() + ")"
                    + "(from " + factorial.candleListFeature.get(0).getDateTime() + " to " + factorial.candleListFeature.get(factorial.candleListFeature.size() - 1).getDateTime() + ")";
            annotation += " expectProfit/expectLoss=" + (expectProfit / expectLoss);
            annotation += factorial.info;
            if (closeMax < buyCriteria.getTakeProfitPercentBetweenCloseMax()
                    && ((buyCriteria.getTakeProfitPercentBetween() != null
                    && expectProfit > buyCriteria.getTakeProfitPercentBetween()
                    && expectLoss < buyCriteria.getStopLossPercent())
                    || (
                            buyCriteria.getTakeProfitRatio() != null
                                    && (expectLoss < buyCriteria.getStopLossPercent()
                                    && (expectProfit / expectLoss > buyCriteria.getTakeProfitRatio()
                            || expectLoss < 0))
                    ))
                    //&& candleList.get(1).getClosingPrice().doubleValue() > candle.getClosingPrice().doubleValue()
                    //&& false
            ) {
                annotation += " ok";
                var candleListPrev = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                        candle.getDateTime(), buyCriteria.getTakeProfitPercentBetweenLength(), strategy.getFactorialInterval());
                for (var i = 0; i < buyCriteria.getTakeProfitPercentBetweenLength() - 1; i++) {
                    var factorialPrev = findBestFactorialInPast(strategy, candleListPrev.get(i));
                    if (factorialPrev == null) {
                        res = false;
                        break;
                    }
                    var expectProfitPrev = factorialPrev.getExpectProfit();
                    var expectLossPrev = factorialPrev.getExpectLoss();
                    annotation +=  " i=" + i + " expectProfitPrev=" + expectProfitPrev
                            + " expectLossPrev=" + expectLossPrev;
                    if ((buyCriteria.getTakeProfitPercentBetween() != null
                            && expectProfitPrev > buyCriteria.getTakeProfitPercentBetween()
                            && expectLossPrev < buyCriteria.getStopLossPercent())
                            || (
                                    buyCriteria.getTakeProfitRatio() != null
                                            && (
                                            expectLoss < buyCriteria.getStopLossPercent() && (
                                                    expectProfitPrev / expectLossPrev > buyCriteria.getTakeProfitRatio()
                                                            || expectLossPrev < 0
                                            ))
                            )
                    ) {
                        annotation += " ok";
                        res = true;
                    } else {
                        res = false;
                        break;
                    }
                }
            }

            annotation += " expectLoss/expectProfit=" + (expectLoss/expectProfit);
            if (!res
                    && ((buyCriteria.getTakeLossPercentBetween() != null
                    && expectProfit < buyCriteria.getStopLossPercent()
                    && expectLoss > buyCriteria.getTakeLossPercentBetween())
                    || (buyCriteria.getTakeLossRatio() != null
                    && expectLoss/expectProfit > buyCriteria.getTakeLossRatio()
                    && expectLoss < buyCriteria.getTakeLossRatioMax()
                    )
                    )
                    //&& candleList.get(0).getClosingPrice().doubleValue() > candle.getClosingPrice().doubleValue()
                    && profit > candle.getClosingPrice().doubleValue()
            ) {
                annotation += " ok TakeLossPercentBetween";
                var candleListPrev = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                        candle.getDateTime(), buyCriteria.getTakeLossPercentBetweenLength(), strategy.getFactorialInterval());
                var lowestAvg = candleListPrev.stream().mapToDouble(v -> v.getLowestPrice().doubleValue()).average().orElse(-1);
                lowestAvg -= candleListPrev.stream().mapToDouble(v -> v.getLowestPrice().doubleValue()).max().orElse(-1) / candleListPrev.size();
                lowestAvg = candleListPrev.size() * lowestAvg / (candleListPrev.size() - 1);
                annotation += " lowestAvg=" + lowestAvg;
                var lossPrevAvg = 0f;
                var expectProfitPrevAvg = 0f;
                var expectLossPrevAvg = 0f;
                //if (lowestAvg < candle.getClosingPrice().doubleValue()
                //        || candleList.get(0).getClosingPrice().doubleValue() > candle.getClosingPrice().doubleValue()
                //) {
                    for (var i = 0; i < buyCriteria.getTakeLossPercentBetweenLength() - 1; i++) {
                        var factorialPrev = findBestFactorialInPast(strategy, candleListPrev.get(i));
                        if (factorialPrev == null) {
                            res = false;
                            break;
                        }
                        var expectProfitPrev = factorialPrev.getExpectProfit();
                        var expectLossPrev = factorialPrev.getExpectLoss();
                        lossPrevAvg += factorialPrev.getLoss() / (buyCriteria.getTakeLossPercentBetweenLength() - 1);
                        expectProfitPrevAvg += factorialPrev.getExpectProfit() / (buyCriteria.getTakeLossPercentBetweenLength() - 1);
                        expectLossPrevAvg += factorialPrev.getExpectLoss() / (buyCriteria.getTakeLossPercentBetweenLength() - 1);
                        annotation += " i=" + i + " expectProfitPrev=" + expectProfitPrev
                                + " expectLossPrev=" + expectLossPrev;
                        if (
                                (buyCriteria.getTakeLossPercentBetween() != null
                                && expectProfitPrev < buyCriteria.getStopLossPercent()
                                && expectLossPrev > buyCriteria.getTakeLossPercentBetween())
                                || (
                                        buyCriteria.getTakeLossRatio() != null
                                                && expectLossPrev/expectProfitPrev > buyCriteria.getTakeLossRatio()
                                )
                        ) {
                            annotation += " ok";
                            res = true;
                        } else {
                            res = false;
                            break;
                        }
                    }
                    //annotation += " expectProfitPrevAvg=" + expectProfitPrevAvg
                    //        + " expectLossPrevAvg=" + expectLossPrevAvg;
                /*
                    annotation += " lossPrevAvg=" + lossPrevAvg;
                    if (res
                            //&& expectProfitPrevAvg < buyCriteria.getStopLossPercent()
                            //&& expectLossPrevAvg > buyCriteria.getTakeLossPercentBetween()
                            && lossPrevAvg < loss
                    ) {
                        annotation += " ok";
                        res = true;
                    } else {
                        res = false;
                    }*/
                //}
            }

            if (!res
                    && buyCriteria.getIsAllUnderLoss()
                    && curPriceMin < loss
            ) {
                annotation += " ok < all loss";
                res = true;
            }

            //log.info("FactorialInstrumentByFiatService {} from {} to {} {}", candle.getFigi(), factorial.candleListPast.get(0).getDateTime(), candle.getDateTime(), factorial.candleListFeature.size(), annotation);
            if (!res
                    && buyCriteria.getTakeProfitLossPercent() != null
                    && curPriceMin < loss
                    && futureProfit > buyCriteria.getTakeProfitLossPercent()
                    //&& (expectLoss + expectProfit) > buyCriteria.getTakeProfitPercent()
                    && expectLoss > 0
            ) {
                Double expectLossAvg = Double.valueOf(expectLoss);
                //lossAvg = candleList.get(1).getLowestPrice().doubleValue();
                lossAvg = 0.0;
                if (strategy.getFactorialAvgSize() > 1) {
                    Double maxV = null;
                    Double minV = null;
                    Double minProfit = null;
                    Double beginMiddle = 0.;
                    Double endMiddle = 0.;
                    var beginMiddleSize = Math.max(1, strategy.getFactorialAvgSize() / 2);
                    var candleListPrev = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                            candle.getDateTime(), strategy.getFactorialAvgSize() + 1, strategy.getFactorialInterval());
                    for (var i = 0; i < strategy.getFactorialAvgSize(); i++) {
                        var factorialPrev = findBestFactorialInPast(strategy, candleListPrev.get(i));
                        if (factorialPrev == null) {
                            break;
                        }
                        expectLossAvg += factorialPrev.getExpectLoss();
                        //lossAvg += candleListPrev.get(i).getLowestPrice().doubleValue();
                        lossAvg += factorialPrev.getLoss();
                        if (maxV == null || maxV < factorialPrev.getLoss()) {
                            maxV = factorialPrev.getLoss();
                        }
                        if (minV == null || minV > factorialPrev.getLoss()) {
                            minV = factorialPrev.getLoss();
                        }
                        if (minProfit == null || minProfit > factorialPrev.getProfit()) {
                            minProfit = factorialPrev.getProfit();
                        }
                        if (i < beginMiddleSize) {
                            beginMiddle += factorialPrev.getLoss() + (factorialPrev.getProfit() - factorialPrev.getLoss()) / 2;
                        } else {
                            endMiddle += factorialPrev.getLoss() + (factorialPrev.getProfit() - factorialPrev.getLoss()) / 2;
                        }
                    }
                    if (strategy.getFactorialAvgSize() > 2 && null != maxV) {
                        //expectLossAvg -= maxV;
                        //expectLossAvg -= minV;
                        lossAvg -= minV;
                        if (strategy.getFactorialAvgSize() > 4) {
                            lossAvg -= maxV;
                            lossAvg = lossAvg / (strategy.getFactorialAvgSize() - 2);
                        } else {
                            lossAvg = lossAvg / (strategy.getFactorialAvgSize() - 1);
                        }
                    } else {
                        lossAvg = lossAvg / strategy.getFactorialAvgSize();
                    }
                    //lossAvg = lossAvg / strategy.getFactorialAvgSize();
                    //lossAvg = lossAvg / strategy.getFactorialAvgSize();
                    //lossAvg = Math.min(candleList.get(1).getLowestPrice().doubleValue(), lossAvg);
                    //lossAvg = lossAvg * (1f - expectLossAvg / 100f);
                    //annotation += " expectLossAvg=" + expectLossAvg;
                    annotation += " lossAvg=" + lossAvg;
                    annotation += " minProfit=" + minProfit;
                    var isUp = null != maxV && lossAvg <= loss;
                    if (isUp && strategy.isFactorialAvgByMiddle() && strategy.getFactorialAvgSize() > 1) {
                        beginMiddle = beginMiddle / beginMiddleSize;
                        endMiddle = endMiddle / (strategy.getFactorialAvgSize() - beginMiddleSize);
                        annotation += " beginMiddle=" + beginMiddle;
                        annotation += " endMiddle=" + endMiddle;
                        if (beginMiddle <= endMiddle) {
                            isUp = true;
                        }
                    }
                    if (isUp
                            && factorial.getExpectProfit() > buyCriteria.getTakeProfitLossPercent()
                            //&& minProfit > loss
                            //&& (expectLossAvg + expectProfit) > buyCriteria.getTakeProfitPercent()
                            //&& (expectLoss + expectProfit) > buyCriteria.getTakeProfitPercent()
                            //&& expectLossAvg > 0
                    ) {
                        annotation += " ok < lossAvg";
                        res = true;
                    } else if (null != maxV) {
                        lossAvg = (double) 0;
                        var candleListPrevPrev = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                                candle.getDateTime(), strategy.getFactorialDownAvgSize() + 1, strategy.getFactorialInterval());
                        boolean isLess = false;
                        for (var i = 0; i < strategy.getFactorialDownAvgSize(); i++) {
                            var factorialPrev = findBestFactorialInPast(strategy, candleListPrevPrev.get(i));
                            var curCandle = candleListPrevPrev.get(i + 1);
                            lossAvg += factorialPrev.getLoss();
                            annotation += " i=" + i;
                            annotation += " date=" + curCandle.getDateTime();
                            annotation += " LowestPrice=" + curCandle.getLowestPrice();
                            annotation += " loss=" + factorialPrev.getLoss();
                            if (curCandle.getLowestPrice().doubleValue() < factorialPrev.getLoss()) {
                                annotation += " isLess";
                                isLess = true;
                            }
                        }
                        lossAvg = lossAvg / strategy.getFactorialDownAvgSize();
                        annotation += " lossDownAvg=" + lossAvg;
                        if (true
                                //&& lossAvg > loss
                                && isLess
                        ) {
                            annotation += " ok < lossDownAvg";
                            res = true;
                        }
                    }
                } else {
                    if (futureProfit > buyCriteria.getTakeProfitLossPercent()
                            //&& (expectLoss + expectProfit) > buyCriteria.getTakeProfitPercent()
                            && expectLoss >= 0
                            && factorial.getExpectProfit() > buyCriteria.getTakeProfitLossPercent()
                    ) {
                        annotation += " ok < loss";
                        res = true;
                    }
                }
            }
            if (!res
                    && buyCriteria.getIsAllOverProfit()
                    && curPriceMax > profit
            ) {
                isResOverProfit = true;
            } else if (!res
                    && buyCriteria.getIsOverProfit()
                    && curPriceMax > profit
            ) {
                Boolean isBuyToShort = false;

                if (expectProfit > 0
                        && (buyCriteria.getTakeProfitLossPercent() == null
                        || futureLoss > buyCriteria.getTakeProfitLossPercent())) {
                    profitAvg = 0.0;
                    if (strategy.getFactorialAvgSize() > 1) {
                        Double maxV = null;
                        Double minV = null;
                        Double minProfit = null;
                        Double beginMiddle = 0.;
                        Double endMiddle = 0.;
                        var beginMiddleSize = Math.max(1, strategy.getFactorialAvgSize() / 2);
                        var candleListPrev = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                                candle.getDateTime(), strategy.getFactorialAvgSize() + 1, strategy.getFactorialInterval());
                        for (var i = 0; i < strategy.getFactorialAvgSize(); i++) {
                            var factorialPrev = findBestFactorialInPast(strategy, candleListPrev.get(i));
                            if (factorialPrev == null) {
                                break;
                            }
                            profitAvg += factorialPrev.getProfit();
                            if (maxV == null || maxV < factorialPrev.getProfit()) {
                                maxV = factorialPrev.getProfit();
                            }
                            if (minV == null || minV > factorialPrev.getProfit()) {
                                minV = factorialPrev.getProfit();
                            }
                            if (minProfit == null || minProfit > factorialPrev.getProfit()) {
                                minProfit = factorialPrev.getProfit();
                            }
                            if (i < beginMiddleSize) {
                                beginMiddle += factorialPrev.getLoss() + (factorialPrev.getProfit() - factorialPrev.getLoss()) / 2;
                            } else {
                                endMiddle += factorialPrev.getLoss() + (factorialPrev.getProfit() - factorialPrev.getLoss()) / 2;
                            }
                        }
                        if (strategy.getFactorialAvgSize() > 2 && null != maxV) {
                            //expectLossAvg -= maxV;
                            //expectLossAvg -= minV;
                            profitAvg -= minV;
                            if (strategy.getFactorialAvgSize() > 4) {
                                profitAvg -= maxV;
                                profitAvg = profitAvg / (strategy.getFactorialAvgSize() - 2);
                            } else {
                                profitAvg = profitAvg / (strategy.getFactorialAvgSize() - 1);
                            }
                        } else {
                            profitAvg = profitAvg / strategy.getFactorialAvgSize();
                        }
                        //lossAvg = lossAvg / strategy.getFactorialAvgSize();
                        //lossAvg = lossAvg / strategy.getFactorialAvgSize();
                        //lossAvg = Math.min(candleList.get(1).getLowestPrice().doubleValue(), lossAvg);
                        //lossAvg = lossAvg * (1f - expectLossAvg / 100f);
                        //annotation += " expectLossAvg=" + expectLossAvg;
                        annotation += " profitAvg=" + profitAvg;
                        var isDown = null != maxV && profitAvg >= profit;
                        if (isDown && strategy.isFactorialAvgByMiddle() && strategy.getFactorialAvgSize() > 1) {
                            beginMiddle = beginMiddle / beginMiddleSize;
                            endMiddle = endMiddle / (strategy.getFactorialAvgSize() - beginMiddleSize);
                            annotation += " beginMiddle=" + beginMiddle;
                            annotation += " endMiddle=" + endMiddle;
                            if (beginMiddle >= endMiddle) {
                                isDown = true;
                            }
                        }
                        if (isDown
                                && factorial.getExpectLoss() > buyCriteria.getTakeProfitLossPercent()
                            //&& minProfit > loss
                            //&& (expectLossAvg + expectProfit) > buyCriteria.getTakeProfitPercent()
                            //&& (expectLoss + expectProfit) > buyCriteria.getTakeProfitPercent()
                            //&& expectLossAvg > 0
                        ) {
                            annotation += " ok > profitAvg";
                            isBuyToShort = true;
                        } else if (null != maxV) {
                            profitAvg = (double) 0;
                            var candleListPrevPrev = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                                    candle.getDateTime(), strategy.getFactorialDownAvgSize() + 1, strategy.getFactorialInterval());
                            boolean isLess = false;
                            for (var i = 0; i < strategy.getFactorialDownAvgSize(); i++) {
                                var factorialPrev = findBestFactorialInPast(strategy, candleListPrevPrev.get(i));
                                var curCandle = candleListPrevPrev.get(i + 1);
                                profitAvg += factorialPrev.getProfit();
                                annotation += " i=" + i;
                                annotation += " date=" + curCandle.getDateTime();
                                annotation += " HighestPrice=" + curCandle.getHighestPrice();
                                annotation += " profit=" + factorialPrev.getProfit();
                                if (curCandle.getHighestPrice().doubleValue() > factorialPrev.getProfit()) {
                                    annotation += " isLess";
                                    isLess = true;
                                }
                            }
                            profitAvg = profitAvg / strategy.getFactorialDownAvgSize();
                            annotation += " profitUpAvg=" + profitAvg;
                            if (true
                                    //&& lossAvg > loss
                                    && isLess
                            ) {
                                annotation += " ok > profitUpAvg";
                                isBuyToShort = true;
                            }
                        }
                    } else {
                        if (futureLoss > buyCriteria.getTakeProfitLossPercent()
                                //&& (expectLoss + expectProfit) > buyCriteria.getTakeProfitPercent()
                                && expectLoss >= 0
                                && factorial.getExpectLoss() > buyCriteria.getTakeProfitLossPercent()
                        ) {
                            annotation += " ok > profit";
                            isBuyToShort = true;
                        }
                    }
                }
                var overProfitPercent = 100.0 * (candle.getClosingPrice().doubleValue() - profit) / profit;
                annotation += " overProfitPercent=" + overProfitPercent;
                if (!isBuyToShort
                        && (buyCriteria.getOverProfitMaxPercent() == null
                        || overProfitPercent < buyCriteria.getOverProfitMaxPercent())
                ) {
                    isResOverProfit = true;
                    annotation += " notShort";
                }
                /*
                var percent = 100f * (candle.getClosingPrice().doubleValue() - profit) / profit;
                annotation += " percent=" + percent;
                if (percent < strategy.getFactorialProfitLessPercent()) {
                    var factorialPrevPrev = findBestFactorialInPast(strategy, candleList.get(0));
                    var profitPrev = candleList.get(0).getLowestPrice().doubleValue() * (1f + factorialPrevPrev.getExpectProfit() / 100f);
                    annotation += " expectProfitPrevPrev=" + profitPrev;
                    if (candleList.get(1).getClosingPrice().doubleValue() > profitPrev
                            && factorialPrevPrev.getExpectProfit() < 0)
                    {
                        annotation += "ok < profit";
                        res = true;
                    }
                }
                 */
            }

            var isResOverProfitWaitFirstUnderProfit = false;
            if (!res && !isResOverProfit
                    && buyCriteria.getIsAllOverProfit()
                    && buyCriteria.getIsOverProfitWaitFirstUnderProfit()
                    && candle.getClosingPrice().floatValue() < factorial.getProfit()
            ) {
                var order = orderService.findLastByFigiAndStrategy(candle.getFigi(), strategy);
                var startDateTime = curHourCandle.getDateTime();
                if (order != null && order.getSellDateTime().isAfter(startDateTime)) {
                    startDateTime = order.getSellDateTime();
                }
                var candleMinList = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                        candle.getFigi(),
                        startDateTime,
                        candle.getDateTime(),
                        strategy.getInterval());
                var maxPrice = candleMinList.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).max().orElse(-1);
                if (maxPrice > factorial.getProfit()) {
                    var underProfitPercent = 100f * (factorial.getProfit() - candle.getClosingPrice().floatValue()) / factorial.getProfit();
                    annotation += " underProfitPercent=" + underProfitPercent;
                    if (buyCriteria.getOverProfitWaitFirstUnderProfitPercent() == null
                        || underProfitPercent > buyCriteria.getOverProfitWaitFirstUnderProfitPercent()
                    ) {
                        annotation += " WaitFirstUnderProfit";
                        isResOverProfitWaitFirstUnderProfit = true;
                    }
                }
            }

            if (!res && (isResOverProfit || isResOverProfitWaitFirstUnderProfit)) {
                var order = orderService.findLastByFigiAndStrategy(candle.getFigi(), strategy);
                if (order == null) {
                    annotation += " ok < all profit";
                    res = true;
                } else {
                    annotation += " curHourCandle=" + curHourCandle.getDateTime();
                    annotation += " orderClosedPrice=" + order.getSellPrice();
                    var percentFromLastSell = 100f * (order.getSellPrice().doubleValue() - candle.getClosingPrice().doubleValue()) / order.getSellPrice().doubleValue();
                    annotation += " curHourCandle=" + curHourCandle.getDateTime();
                    annotation += " orderClosedPrice=" + order.getSellPrice();
                    annotation += " percentFromLastSell=" + percentFromLastSell;
                    if (order.getPurchaseDateTime().isBefore(curHourCandle.getDateTime())
                            || (order.getSellPrice().doubleValue() < profit)
                    ) {
                        annotation += " ok < all profit";
                        res = true;
                    } else if (buyCriteria.getAllOverProfitSecondPercent() != null
                            && percentFromLastSell > buyCriteria.getAllOverProfitSecondPercent()
                    ) {
                        annotation += " ok < all profit second";
                        res = true;
                        isProfitSecond = true;
                    }
                }

                if (res && isResOverProfit && !isProfitSecond && buyCriteria.getIsOverProfitWaitFirstUnderProfit()) {
                    var overProfitPercent = 100f * (candle.getClosingPrice().doubleValue() - factorial.getProfit()) / factorial.getProfit();
                    annotation += " overProfitPercent=" + overProfitPercent;
                    if (buyCriteria.getOverProfitSkipWaitFirstOverProfitPercent() == null
                            || buyCriteria.getOverProfitSkipWaitFirstOverProfitPercent() > overProfitPercent
                    ) {
                        annotation += " false: first wait under profit";
                        res = false;
                    }
                }
                isResOverProfit = true;
            }

            if (!res
                    && buyCriteria.getSplashLossPercentMax() != null
                    && factorial.getExpectLoss() < buyCriteria.getSplashLossPercentMax()
                    && factorial.getExpectProfit() > buyCriteria.getSplashProfitPercentMin()
                    && factorial.getLoss() < candle.getClosingPrice().doubleValue()
            ) {
                var factorialPrev = findBestFactorialInPast(strategy, candleList.get(0));
                if (null != factorialPrev) {
                    annotation += " lossRatio=" + (factorialPrev.getExpectLoss() / factorial.getExpectLoss())
                            + " profitRatio=" + factorial.getExpectProfit() / factorialPrev.getExpectProfit()
                            + " factorialPrev.getProfit()=" + factorialPrev.getProfit();
                    if (factorialPrev.getExpectLoss() / factorial.getExpectLoss() > buyCriteria.getSplashLossRatio()
                            && factorial.getExpectProfit() / factorialPrev.getExpectProfit() > buyCriteria.getSplashProfitRatio()
                        //&& factorialPrev.getProfit() > factorial.getProfit()
                    ) {
                        annotation += "ok splash";
                        res = true;
                    }
                }
            }
        }

        clearCandleInterval(newStrategy, candle);
        var candleBuyRes = getCandleBuyRes(newStrategy, candle);
        var candleIntervalBuy = candleBuyRes.candleIntervalBuy;
        var candleIntervalSell = candleBuyRes.candleIntervalSell;
        var candleIntervalUpDownData = candleBuyRes.candleIntervalUpDownData;
        if (candleIntervalUpDownData.beginDownFirst == null) {
            annotation += " something wrong " + candleIntervalUpDownData.annotation;
        }
        if (!res
                //&& candle.getClosingPrice().floatValue() < factorial.getProfit()
                && buyCriteria.getCandleIntervalMinPercent() != null
                && candleIntervalUpDownData.beginDownFirst != null
        ) {
            annotation += candleBuyRes.annotation;
            if (candleBuyRes.res) {
                var order = orderService.findLastByFigiAndStrategy(candle.getFigi(), strategy);
                annotation += " endPost: " + printDateTime(candleBuyRes.candleIntervalUpDownData.endPost.candle.getDateTime());
                var middlePrice = candleIntervalUpDownData.minClose + (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) / 2f;
                annotation += " middlePrice: " + printPrice(middlePrice);
                if (order != null) {
                    annotation += " orderPointLength: " + order.getDetails().getBooleanDataMap().getOrDefault("isPointLength", false);
                    annotation += " isPointLength: " + getOrderBooleanDataMap(strategy, candle).getOrDefault("isPointLength", false);
                }
                if (null == order
                        || !candleBuyRes.candleIntervalUpDownData.endPost.candle.getDateTime().isBefore(order.getPurchaseDateTime())
                        || (order.getPurchasePrice().floatValue() > middlePrice && candle.getClosingPrice().floatValue() < middlePrice)
                        || (!order.getDetails().getBooleanDataMap().getOrDefault("isPointLength", false)
                            && getOrderBooleanDataMap(strategy, candle).getOrDefault("isPointLength", false))
                ) {
                    res = candleBuyRes.res;
                    annotation += " BYU OK";
                }
            }
            //if (candleBuyRes.isIntervalUp) {
            //    setTrendUp(strategy, candle, candleBuyRes.res);
            //} else if (getTrendUp(strategy, candle) != null) {
            //    annotation += " resMaybe=TrendUp=" + getTrendUp(strategy, candle);
            //} else {
                var candleIntervalUpDownDataPrev = getPrevCandleIntervalUpDownData(newStrategy, candleIntervalUpDownData);
                var isIntervalUpResMaybe = calcIsIntervalUpMaybe(
                        candle,
                        null,
                        buyCriteria,
                        newStrategy,
                        candleIntervalUpDownData,
                        candleIntervalUpDownDataPrev
                );
                setTrendUp(strategy, candle, isIntervalUpResMaybe.isIntervalUp || candleBuyRes.res);
                annotation += " resMaybe=" + isIntervalUpResMaybe.isIntervalUp + ": " + isIntervalUpResMaybe.annotation;
                if (
                        null != isIntervalUpResMaybe.isIntervalUpMayBe && isIntervalUpResMaybe.isIntervalUpMayBe
                        || isIntervalUpResMaybe.isIntervalUp
                ) {
                    annotation += " minPercent=" + printPrice(isIntervalUpResMaybe.minPercent);
                    annotation += " minPercentPrev=" + printPrice(isIntervalUpResMaybe.minPercentPrev);
                    annotation += " maxPercent=" + printPrice(isIntervalUpResMaybe.maxPercent);
                    annotation += " maxPercentPrev=" + printPrice(isIntervalUpResMaybe.maxPercentPrev);
                    var minPercent = isIntervalUpResMaybe.minPercent;
                    if (minPercent.compareTo(BigDecimal.ZERO) < 0) {
                        minPercent = isIntervalUpResMaybe.minPercentPrev;
                    }
                    if (
                            minPercent.compareTo(BigDecimal.ZERO) > 0
                            // && isIntervalUpResMaybe.minPercent.floatValue() < 25
                            && isIntervalUpResMaybe.maxPercent.floatValue() < 100
                            && isIntervalUpResMaybe.maxPercent.compareTo(minPercent.add(BigDecimal.valueOf(5f))) > 0f
                            //&& isIntervalUpResMaybe.minPercent.floatValue() > 0
                    ) {
                        res = true;
                        annotation += " BYU MAY BE OK";
                    }
                    if (
                            isIntervalUpResMaybe.maxPercent.compareTo(BigDecimal.valueOf(5f)) > 0
                            && isIntervalUpResMaybe.maxPercent.floatValue() < 100
                            && isIntervalUpResMaybe.maxPercentPrev.compareTo(BigDecimal.ZERO) > 0
                            && isIntervalUpResMaybe.minPercent.compareTo(BigDecimal.valueOf(-60f)) > 0
                            && isIntervalUpResMaybe.minPercentPrev.compareTo(BigDecimal.valueOf(-60f)) > 0
                    ) {
                        res = true;
                        annotation += " BYU MAY BE UP OK";
                    }
                }
            setOrderInfo(newStrategy, candle, true, candleIntervalUpDownData, isIntervalUpResMaybe);

            var isTrendSell = isTrendSellCalc(newStrategy, candle);
            annotation += " isTrendSell=" + isTrendSell.isIntervalDown + ": " + isTrendSell.annotation;
            setTrendDown(strategy, candle, isTrendSell.isIntervalDown);
            if (isTrendSell.isIntervalDown && res) {
                res = false;
                annotation += " SKIP BY TREND SELL";
            }
            if (isTrendSell.maxDiffPercent != null
                    && isTrendSell.maxDiffPercent.floatValue() > 100
                    && isTrendSell.maxDiffCurPercent.floatValue() > 100
                    && !sellCriteria.getIsMaxPriceByFib()
            ) {
                res = false;
                annotation += " SKIP BY maxDiffPercent > 100";
            }
            if (isTrendSell.isIntervalDown) {
                setTrendUp(strategy, candle, false);
            }
            //}
        }

        if (res
                && buyCriteria.getSkipIfOutPrevLength() != null
        ) {
            var candleListPrevPrev = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                    candle.getDateTime(), buyCriteria.getSkipIfOutPrevLength() + 1, strategy.getFactorialInterval());
            var isOut = false;
            annotation += " SkipIfOut";
            for (var i = 0; i < buyCriteria.getSkipIfOutPrevLength(); i++) {
                var factorialPrev = findBestFactorialInPast(strategy, candleListPrevPrev.get(i));
                var candleMinList = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                        candle.getFigi(),
                        candleListPrevPrev.get(i + 1).getDateTime(),
                        candleListPrevPrev.get(i + 1).getDateTime().plusHours(1),
                        strategy.getInterval());
                var minPrice = candleMinList.stream().mapToDouble(value -> value.getLowestPrice().doubleValue()).min().orElse(-1);
                var maxPrice = candleMinList.stream().mapToDouble(value -> value.getHighestPrice().doubleValue()).max().orElse(-1);
                if (maxPrice > factorialPrev.getProfit()) {
                    annotation += " maxPrice=" + maxPrice;
                    isOut = true;
                    break;
                }
                if (factorialPrev.getLoss() > minPrice) {
                    annotation += " minPrice=" + minPrice;
                    isOut = true;
                    break;
                }
            }
            if (!isOut) {
                annotation += " SkipIfOutOk";
                res = false;
            }
        }

        if (res && isResOverProfit && buyCriteria.getOverProfitSkipIfOverProfitLength() != null) {
            var candleListPrevPrev = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                    candle.getDateTime(), buyCriteria.getOverProfitSkipIfOverProfitLength() + 1 + buyCriteria.getOverProfitSkipIfOverProfitLengthError(), strategy.getFactorialInterval());
            var outCount = 0;
            annotation += " SkipIfOverProfit";
            for (var i = 0; i < buyCriteria.getOverProfitSkipIfOverProfitLength() + buyCriteria.getOverProfitSkipIfOverProfitLengthError(); i++) {
                var factorialPrev = findBestFactorialInPast(strategy, candleListPrevPrev.get(i));
                if (null == factorialPrev) {
                    return false;
                }
                var candleMinList = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                        candle.getFigi(),
                        candleListPrevPrev.get(i + 1).getDateTime(),
                        candleListPrevPrev.get(i + 1).getDateTime().plusHours(1),
                        strategy.getInterval());
                var maxPrice = candleMinList.stream().mapToDouble(value -> value.getHighestPrice().doubleValue()).max().orElse(-1);
                if (maxPrice >= factorialPrev.getProfit()) {
                    annotation += " maxPrice=" + maxPrice;
                    outCount++;
                }
            }
            if (outCount >= buyCriteria.getOverProfitSkipIfOverProfitLength()) {
                annotation += " SkipIfOverProfitOk";
                res = false;
            }
        }

        var isTrendUp = getTrendUp(strategy, candle);
        if (
                (res || (isTrendUp != null && isTrendUp))
                && buyCriteria.getNotLossSellLength() > 0
        ) {
            var order = orderService.findLastByFigiAndStrategy(candle.getFigi(), strategy);
            if (order != null) {
                var profitPercent = 100f * (order.getSellPrice().floatValue() - order.getPurchasePrice().floatValue()) / order.getPurchasePrice().floatValue();
                var profitPercentDiff = 100f * (order.getSellPrice().floatValue() - candle.getClosingPrice().floatValue()) / order.getPurchasePrice().floatValue();
                annotation += " NotLossSell profitPercent=" + profitPercent;
                annotation += " profitPercentDiff=" + profitPercentDiff;
                if (profitPercent < buyCriteria.getNotLossSellPercent()
                        && profitPercentDiff < buyCriteria.getNotLossSellPercentDiff()
                ) {
                    var listCandle = candleHistoryService.getCandlesByFigiBetweenDateTimes(candle.getFigi(), order.getSellDateTime().minusHours(1), candle.getDateTime(), strategy.getFactorialInterval());
                    annotation += " NotLossSellLength=" + listCandle.size();
                    if (listCandle.size() <= buyCriteria.getNotLossSellLength()) {
                        annotation += " skipNotLossSellLength=" + buyCriteria.getNotLossSellLength();
                        res = false;
                        setTrendUp(strategy, candle, false);
                    }
                }
            }
        }

        String key = buildKeyHour(strategy.getExtName(), candle);
        var buyPrice = getCashedIsBuyValue(key);
        if (null == buyPrice
                && buyCriteria.getProfitPercentFromBuyMinPriceLength() > 1
        ) {
            String keyPrev = buildKeyHour(strategy.getExtName(), curHourCandleForFactorial);
            buyPrice = getCashedIsBuyValue(keyPrev);
            if (null != buyPrice) {
                if (buyPrice.isResOverProfit) {
                    buyPrice = null;
                } else {
                    key = keyPrev;
                }
            }
        }
        var resBuy = false;
        if ((buyPrice != null || res)
                && (null != buyCriteria.getProfitPercentFromBuyMinPrice()
                    || null != buyCriteria.getProfitPercentFromBuyMinPriceRelativeMin())
                && (!isResOverProfit
                    || (buyCriteria.getIsProfitPercentFromBuyPriceTop() && isResOverProfit)
                    || (isProfitSecond && buyCriteria.getIsProfitPercentFromBuyPriceTopSecond())
        )) {
            annotation += " key = " + key + "(" + res + ")";
            if (buyPrice == null
                    || (res && candle.getClosingPrice().doubleValue() < buyPrice.getPrice())
            ) {
                var maxPrice = candle.getClosingPrice().doubleValue();
                var minPrice = candle.getClosingPrice().doubleValue();
                if (buyCriteria.getProfitPercentFromBuyMinPriceRelativeMin() != null
                        && buyCriteria.getProfitPercentFromBuyMinPrice() == null
                        && !isResOverProfit
                ) {
                    // определяем с какой цены началось падение
                    if (buyCriteria.getIsProfitPercentFromBuyMinPriceRelativeMaxMax()) {
                        minPrice = candle.getLowestPrice().doubleValue();
                    }
                    if (buyCriteria.getIsProfitPercentFromBuyMinPriceRelativeMaxMax()) {
                        maxPrice = candle.getHighestPrice().doubleValue();
                    }
                    List<CandleDomainEntity> candlePrev;
                    var order = orderService.findLastByFigiAndStrategy(candle.getFigi(), strategy);
                    if (null != order && order.getPurchaseDateTime().isBefore(curHourCandle.getDateTime())) {
                        candlePrev = candleHistoryService.getCandlesByFigiBetweenDateTimes(candle.getFigi(), order.getPurchaseDateTime(), candle.getDateTime(), strategy.getInterval());
                    } else {
                        candlePrev = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(), candle.getDateTime(), 200, strategy.getInterval());
                    }
                    var i = candlePrev.size() - 2;
                    for (; i >= 0; i--) {
                        var curPrice = candlePrev.get(i).getClosingPrice().doubleValue();
                        var curMaxPrice = curPrice;
                        if (buyCriteria.getIsProfitPercentFromBuyMinPriceRelativeMaxMax()) {
                            curPrice = curMaxPrice = candlePrev.get(i).getHighestPrice().doubleValue();
                        }
                        if (curMaxPrice > maxPrice) {
                            maxPrice = curMaxPrice;
                        }
                        var percentDown = maxPrice - minPrice;
                        if (percentDown > 0) {
                            percentDown = 100f * (maxPrice - curPrice) / percentDown;
                        }
                        if (percentDown > buyCriteria.getProfitPercentFromBuyMinPriceRelativeTop()) {
                            annotation += " percentDown = " + percentDown;
                            var pDown = 100f * (maxPrice - curPrice) / Math.abs(maxPrice);
                            annotation += " pDown = " + pDown;
                            if (buyCriteria.getProfitPercentFromBuyMinPriceRelativeTopMin() == null
                                    || pDown > buyCriteria.getProfitPercentFromBuyMinPriceRelativeTopMin()) {
                                break;
                            }
                        }
                    }
                    annotation += " i = " + i;
                }
                annotation += " maxPrice = " + maxPrice;
                buyPrice = addCashedIsBuyValue(key, BuyData.builder()
                        .price(candle.getClosingPrice().doubleValue())
                        .minPrice(minPrice)
                        .maxPrice(maxPrice)
                        .isResOverProfit(isResOverProfit)
                        .isProfitSecond(isProfitSecond)
                        .build());
            }
            isResOverProfit = buyPrice.isResOverProfit;
            var profitPercentFromBuyMinPrice = buyCriteria.getProfitPercentFromBuyMinPrice();
            var profitPercentFromBuyMaxPrice = buyCriteria.getProfitPercentFromBuyMaxPrice();
            if (buyPrice.getIsResOverProfit()) {
                if (buyCriteria.getProfitPercentFromBuyMinPriceProfit() != null) {
                    profitPercentFromBuyMinPrice = buyCriteria.getProfitPercentFromBuyMinPriceProfit();
                }
                profitPercentFromBuyMaxPrice = buyCriteria.getProfitPercentFromBuyMaxPriceProfit();
                if (buyPrice.getIsProfitSecond()) {
                    profitPercentFromBuyMaxPrice = buyCriteria.getProfitPercentFromBuyMaxPriceProfitSecond();
                }
            }
            var percentProfit = 100.0 * (candle.getClosingPrice().doubleValue() - buyPrice.getMinPrice()) / buyPrice.getMinPrice();
            var percentFromBy = 100.0 * (candle.getClosingPrice().doubleValue() - buyPrice.getPrice()) / buyPrice.getPrice();
            annotation += " percentProfit = " + percentProfit;
            annotation += " percentFromBy = " + percentFromBy;
            annotation += " minPrice = " + buyPrice.minPrice + " price:" + buyPrice.price;
            if (buyCriteria.getProfitPercentFromBuyMinPriceRelativeMin() != null
                    && profitPercentFromBuyMinPrice == null
            ) {
                var percentUp = buyPrice.getMaxPrice() - buyPrice.getMinPrice();
                if (percentUp > 0) {
                    percentUp = 100f * (candle.getClosingPrice().doubleValue() - buyPrice.minPrice) / percentUp;
                }
                annotation += "maxPrice = " + buyPrice.maxPrice;
                annotation += "percentUp = " + percentUp;
                if (percentUp > buyCriteria.getProfitPercentFromBuyMinPriceRelativeMin()
                        && percentUp < buyCriteria.getProfitPercentFromBuyMinPriceRelativeMax()
                ) {
                    resBuy = true;
                    annotation += " ok loss ProfitPercentFromBuyMinPriceRelative";
                }
            } else if (((profitPercentFromBuyMinPrice > 0 && percentProfit > profitPercentFromBuyMinPrice)
                    || (profitPercentFromBuyMinPrice <= 0 && percentProfit < profitPercentFromBuyMinPrice))
                    && (false //candle.getClosingPrice().doubleValue() < loss
                            || profitPercentFromBuyMaxPrice == null
                            || percentFromBy < buyCriteria.getProfitPercentFromBuyMaxPrice()
            )) {
                resBuy = true;
                annotation += " ok loss ProfitPercentFromBuyMinPrice";
            }

            var curMinPrice = candle.getClosingPrice().doubleValue();
            if (buyCriteria.getIsProfitPercentFromBuyMinPriceRelativeMaxMax()) {
                curMinPrice = candle.getLowestPrice().doubleValue();
            }
            if (curMinPrice < buyPrice.getMinPrice()
                    && (profitPercentFromBuyMinPrice == null || profitPercentFromBuyMinPrice > 0)
            ) {
                buyPrice.setMinPrice(curMinPrice);
                addCashedIsBuyValue(key, buyPrice);
            }
        } else {
            resBuy = res;
        }

        if (resBuy
                && candle.getClosingPrice().floatValue() < factorial.getLoss()
                && buyCriteria.getUnderLostWaitCandleEndInMinutes() != null
                && candle.getDateTime().plusMinutes(buyCriteria.getUnderLostWaitCandleEndInMinutes()).isBefore(curHourCandle.getDateTime().plusHours(1))
        ) {
            annotation += " UnderLostWaitCandleEndInMinutes";
            resBuy = false;
        }

        if (resBuy && isResOverProfit
                && (buyCriteria.getOverProfitSkipIfUnderLossPrev() > 0
                || buyCriteria.getOverProfitSkipIfSellPrev() != null)
        ) {
            var length = buyCriteria.getOverProfitSkipIfUnderLossPrev();
            if (buyCriteria.getOverProfitSkipIfSellPrev() != null) {
                length = Math.max(buyCriteria.getOverProfitSkipIfSellPrev(), length);
            }
            var candleListPrevPrev = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                    candle.getDateTime(), length + 1, strategy.getFactorialInterval());
            var isLoss = false;
            var isUnderLoss = false;
            annotation += " ProfitSkip";
            if (buyCriteria.getOverProfitSkipIfSellPrev() != null) {
                var order = orderService.findLastByFigiAndStrategy(candle.getFigi(), strategy);
                var candleFirst = candleListPrevPrev.get(length - buyCriteria.getOverProfitSkipIfSellPrev());
                annotation += " candleFirst=" + candleFirst.getDateTime();
                if (order != null && order.getSellDateTime().isAfter(candleFirst.getDateTime())) {
                    annotation += " OverProfitSkipIfSellPrev=" + buyCriteria.getOverProfitSkipIfSellPrev();
                    resBuy = false;
                }
            }

            for (var i = length + 1 - buyCriteria.getOverProfitSkipIfUnderLossPrev(); i < (candleListPrevPrev.size() - 1); i++) {
                var factorialPrev = findBestFactorialInPast(strategy, candleListPrevPrev.get(i));
                var candleMinList = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                        candle.getFigi(),
                        candleListPrevPrev.get(i + 1).getDateTime(),
                        candleListPrevPrev.get(i + 1).getDateTime().plusHours(1),
                        strategy.getInterval());
                annotation += " " + i + " " + candleListPrevPrev.get(i + 1).getDateTime();
                annotation += " loss=" + factorialPrev.getLoss();
                var minPrice = candleMinList.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).min().orElse(-1);
                var maxPrice = candleMinList.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).max().orElse(-1);
                if (maxPrice > factorial.getProfit()) {
                    annotation += " maxPrice=" + maxPrice;
                    isLoss = true;
                }
                annotation += " minPrice=" + minPrice;
                if (factorialPrev.getLoss() > minPrice) {
                    annotation += " underLoss";
                    isUnderLoss = true;
                    break;
                }
            }
            if (isLoss && isUnderLoss) {
                annotation += " OverProfitSkipIfUnderLossPrev";
                resBuy = false;
            }
        }

        if (resBuy) {
            //annotation += " info: " + factorial.getInfo();
        }
        var candleIntervalMinPercent = buyCriteria.getCandleIntervalMinPercent();
        if (null != sellCriteria.getProfitPercentFromSellMinPrice()) {
            candleIntervalMinPercent = sellCriteria.getProfitPercentFromSellMinPrice();
        }
        var profitPercentFromBuyMinPrice = sellCriteria.getCandleIntervalMinPercent();
        if (null != buyCriteria.getProfitPercentFromBuyMinPrice()) {
            profitPercentFromBuyMinPrice = (float) -buyCriteria.getProfitPercentFromBuyMinPrice();
        }

        List<Double> ema = null;
        List<Double> emaPrev = null;
        if (null != buyCriteria.getEmaLength() && candleIntervalUpDownData.beginDownFirst != null) {
            annotation += " ema " + candleIntervalUpDownData.beginDownFirst.candle.getDateTime() + " - " + candleIntervalUpDownData.endPost.candle.getDateTime();
            //var candles = candleHistoryService.getCandlesByFigiBetweenDateTimes(candle.getFigi(), candleIntervalUpDownData.beginDownFirst.candle.getDateTime(), candleIntervalUpDownData.endPost.candle.getDateTime(), strategy.getInterval());
            //if (candles != null && candles.size() > 0) {
                //var emaLength = candles.size() * 20;
                var emaLength = buyCriteria.getEmaLength();
                annotation += " size=" + emaLength;
                //ema = getEma(candle.getFigi(), candle.getDateTime(), emaLength, strategy.getInterval(), CandleDomainEntity::getClosingPrice, 1);
                //emaPrev = getSma(candle.getFigi(), candle.getDateTime(), emaLength, strategy.getInterval(), CandleDomainEntity::getClosingPrice, 1);
        }

        annotation = " " + resBuy + " open = " + printPrice(candle.getOpenPrice()) + " close=" + printPrice(candle.getClosingPrice()) + " " + annotation;
        isTrendUp = getTrendUp(strategy, candle);
        var isTrendDown = getTrendDown(strategy, candle);
        notificationService.reportStrategyExt(
                resBuy,
                strategy,
                candle,
                "Date|open|high|low|close|ema2|profit|loss|limitPrice|lossAvg|deadLineTop|investBottom|investTop|smaTube|strategy|average|averageBottom"
                        + "|averageTop|candleBuySell|maxClose|minClose|priceBegin|priceEnd|ema|emaPrev|MinFactor|MaxFactor|underPrice|takeProfit|stopLossPrice|borderClose|takeProfitPriceStart"
                        + "|stopLossPriceBottom|maxPriceProfitStep|trendUp|trendDown|buy|sell",
                "{} | {} | {} | {} | {} | | {} | {} | | {} | ||||by {}||||{}|{}|{}|{}|{}|{}|{}|{}|{}|{}|{}|{}|{}||{}||{}|{}|{}|",
                printDateTime(candle.getDateTime()),
                candle.getOpenPrice(),
                candle.getHighestPrice(),
                candle.getLowestPrice(),
                candle.getClosingPrice(),
                profit,
                loss,
                lossAvg == null ? "" : lossAvg,
                annotation,
                candleIntervalSell ? candle.getClosingPrice().multiply(BigDecimal.valueOf(1. + candleIntervalMinPercent / 100))
                        : (candleIntervalBuy ? candle.getClosingPrice().subtract(candle.getClosingPrice().abs().multiply(BigDecimal.valueOf(profitPercentFromBuyMinPrice / 100))) : ""),
                candleIntervalUpDownData.maxClose,
                candleIntervalUpDownData.minClose,
                candleIntervalUpDownData.priceBegin,
                candleIntervalUpDownData.priceEnd,
                ema == null ? "" : ema.get(ema.size() - 1),
                emaPrev == null ? "" : emaPrev.get(emaPrev.size() - 1),
                candleBuyRes.candlePriceMinFactor == null ? "" : candleIntervalUpDownData.maxClose - (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) * candleBuyRes.candlePriceMinFactor,
                candleBuyRes.candlePriceMaxFactor == null ? "" : candleIntervalUpDownData.maxClose - (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) * candleBuyRes.candlePriceMaxFactor,
                candleBuyRes.notLossBuyUnderPrice == null ? "": candleBuyRes.notLossBuyUnderPrice,
                getOrderBigDecimalDataMap(strategy, candle).getOrDefault("takeProfitPriceStep", BigDecimal.ZERO).equals(BigDecimal.ZERO) ? ""
                        : getOrderBigDecimalDataMap(strategy, candle).getOrDefault("takeProfitPriceStart", BigDecimal.ZERO).add(getOrderBigDecimalDataMap(strategy, candle).getOrDefault("takeProfitPriceStep", BigDecimal.ZERO)),
                getOrderBigDecimalDataMap(strategy, candle).getOrDefault("stopLossPrice", BigDecimal.ZERO).equals(BigDecimal.ZERO) ? "" : getOrderBigDecimalDataMap(strategy, candle).getOrDefault("stopLossPrice", BigDecimal.ZERO),
                candleIntervalUpDownData.endPost == null ? ""
                        : (candleIntervalUpDownData.endPost.candle.getDateTime().equals(candle.getDateTime()) ? candleIntervalUpDownData.minClose : candleIntervalUpDownData.maxClose),
                getOrderBigDecimalDataMap(strategy, candle).getOrDefault("stopLossPriceBottom", BigDecimal.ZERO).equals(BigDecimal.ZERO) ? "" : getOrderBigDecimalDataMap(strategy, candle).getOrDefault("stopLossPriceBottom", BigDecimal.ZERO),
                isTrendUp != null && isTrendUp ? candle.getClosingPrice().add(candle.getClosingPrice().abs().multiply(BigDecimal.valueOf(2 * profitPercentFromBuyMinPrice / 100))) : "",
                isTrendDown != null && isTrendDown ? candle.getClosingPrice().subtract(candle.getClosingPrice().abs().multiply(BigDecimal.valueOf(2 * profitPercentFromBuyMinPrice / 100))) : "",
                res ? candle.getClosingPrice().add(candle.getClosingPrice().abs().multiply(BigDecimal.valueOf(4 * profitPercentFromBuyMinPrice / 100))) : ""
                );
        return resBuy;
    }

    @Override
    public boolean isShouldSell(AInstrumentByFiatFactorialStrategy strategy, CandleDomainEntity candle, BigDecimal purchaseRate) {

        BigDecimal limitPrice = null;
        var profitPercent = candle.getClosingPrice().subtract(purchaseRate)
                .multiply(BigDecimal.valueOf(100))
                .divide(purchaseRate, 4, RoundingMode.HALF_DOWN);

        var candleListPrev = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                candle.getDateTime(), strategy.getFactorialLossIgnoreSize(), strategy.getFactorialInterval());
        var curHourCandle = candleListPrev.get(strategy.getFactorialLossIgnoreSize() - 1);
        var factorial = findBestFactorialInPast(strategy, curHourCandle);

        String key = buildKeyHour(strategy.getExtName(), candle);
        addCashedIsBuyValue(key, null);
        if (strategy.getBuyCriteria().getProfitPercentFromBuyMinPriceLength() > 1) {
            String keyPrev = buildKeyHour(strategy.getExtName(), curHourCandle);
            addCashedIsBuyValue(keyPrev, null);
        }
        var order = orderService.findActiveByFigiAndStrategy(candle.getFigi(), strategy);
        String annotation = " profitPercent=" + profitPercent;
        var newStrategy = buildAvgStrategy(strategy, candle);
        if (null == newStrategy) {
            return false;
        }
        if (null != newStrategy.getPriceDiffAvgReal()) {
            annotation += " priceDiffAvgReal=" + newStrategy.getPriceDiffAvgReal();
        }
        var buyCriteria = newStrategy.getBuyCriteria();
        var sellCriteria = newStrategy.getSellCriteria();
        Boolean res = false;
        var curBeginHour = candle.getDateTime();
        curBeginHour = curBeginHour.minusMinutes(curBeginHour.getMinute() + 1);
        var curEndHour = curBeginHour.plusHours(1);
        annotation += " orderDate=" + order.getPurchaseDateTime() + " curBeginHour=" + curBeginHour;
        if ((sellCriteria.getIsSellUnderProfit()
                && factorial.getProfit() < candle.getClosingPrice().doubleValue())
                || (sellCriteria.getSellUnderLossLength() > 0 && factorial.getLoss() > candle.getClosingPrice().doubleValue())
                /*&& !(
                        order.getPurchaseDateTime().isBefore(curEndHour)
                                && order.getPurchasePrice().doubleValue() >= factorial.getProfit()
                )*/
        ) {
            var candleListPrevOrder = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                    order.getPurchaseDateTime(), 1, strategy.getFactorialInterval());
            var factorialOrder = findBestFactorialInPast(strategy, candleListPrevOrder.get(0));
            var orderAvg = (factorialOrder.getLoss() + (factorialOrder.getProfit() - factorialOrder.getLoss()) / 2);
            annotation += " orderAvg=" + orderAvg;
            var isOrdeCrossTunel = orderAvg > order.getPurchasePrice().doubleValue();
            if (factorial.getLoss() > candle.getClosingPrice().doubleValue()) {
                var candleListPrevProfit = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                        candle.getDateTime().plusHours(1), sellCriteria.getSellUnderLossLength(), strategy.getFactorialInterval());
                annotation += " time=" + candleListPrevProfit.get(0).getDateTime() + "(" + sellCriteria.getSellUnderLossLength() + ")";
                isOrdeCrossTunel = candleListPrevProfit.get(0).getDateTime().isAfter(order.getPurchaseDateTime());
            } else {
                if (!isOrdeCrossTunel) {
                    annotation += " time=" + curBeginHour;
                    isOrdeCrossTunel = curBeginHour.isAfter(order.getPurchaseDateTime());
                }
            }
            annotation += " isOrdeCrossTunel = " + isOrdeCrossTunel;
            if (isOrdeCrossTunel
                    //|| candleListPrev.get(0).getDateTime().isAfter(order.getPurchaseDateTime())
            ) {
                if (factorial.getProfit() < candle.getClosingPrice().doubleValue()) {
                    annotation += " profit < close";
                } else {
                    annotation += " loss > close";
                }
                res = true;
            }
        }
        if (!res && sellCriteria.getStopLossSoftPercent() != null
                && profitPercent.floatValue() < -1 * sellCriteria.getStopLossSoftPercent()
                && factorial.getLoss() < candle.getClosingPrice().doubleValue()
                && candleListPrev.get(0).getDateTime().isAfter(order.getPurchaseDateTime())
        ) {
            if (sellCriteria.getStopLossSoftLength() > 1) {
                var candleList = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                        candle.getDateTime(), sellCriteria.getStopLossSoftLength(), strategy.getInterval());
                var profitPercentPrev = candleList.get(0).getClosingPrice().subtract(purchaseRate)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(purchaseRate, 4, RoundingMode.HALF_DOWN);
                annotation += " profitPercentPrev(" + sellCriteria.getStopLossSoftLength()+ ")=" + profitPercentPrev;
                if (sellCriteria.getStopLossSoftPercent() != null && profitPercentPrev.floatValue() < -1 * sellCriteria.getStopLossSoftPercent()) {
                    res = true;
                }
            } else {
                res = true;
            }
        }

        if (!res && sellCriteria.getStopLossPercent() != null
                && profitPercent.floatValue() < -1 * sellCriteria.getStopLossPercent()
                && factorial.getLoss() < candle.getClosingPrice().doubleValue()
                && candleListPrev.get(0).getDateTime().isAfter(order.getPurchaseDateTime())
        ) {
            if (sellCriteria.getStopLossLength() > 1) {
                var candleList = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                        candle.getDateTime(), sellCriteria.getStopLossLength(), strategy.getInterval());
                var profitPercentPrev = candleList.get(0).getClosingPrice().subtract(purchaseRate)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(purchaseRate, 4, RoundingMode.HALF_DOWN);
                annotation += " profitPercentPrev(" + sellCriteria.getStopLossLength()+ ")=" + profitPercentPrev;
                if (sellCriteria.getStopLossPercent() != null && profitPercentPrev.floatValue() < -1 * sellCriteria.getStopLossPercent()) {
                    res = true;
                }
            } else {
                res = true;
            }
        }
        if (!res && sellCriteria.getExitLossPercent() != null
                && profitPercent.floatValue() < -1 * sellCriteria.getExitLossPercent()
                //&& factorial.getLoss() < candle.getClosingPrice().doubleValue()
        ) {
            annotation += "ok < ExitLossPercent " + sellCriteria.getExitLossPercent();
            res = true;
        }

        if (!res && sellCriteria.getExitProfitInPercentMax() != null
                //&& profitPercent.floatValue() > 0
        ) {
            var purchasePrice = order.getPurchasePrice().doubleValue();
            var startDate = order.getPurchaseDateTime();
            var profitPercentSell = profitPercent.floatValue();
            var profitPercentSell2 = profitPercent.floatValue();
            var exitProfitInPercentMax = sellCriteria.getExitProfitInPercentMax();
            var takeProfitPercent = sellCriteria.getTakeProfitPercent();
            var takeProfitPercent2 = takeProfitPercent;
            String keySell = "sell" + candle.getFigi() + printDateTime(order.getPurchaseDateTime());
            var sellData = getCashedIsBuyValue(keySell);
            if (sellData != null) {
                purchasePrice = sellData.price;
                startDate = sellData.dateTime;
                profitPercentSell2 = (float) (100f * (candle.getClosingPrice().floatValue() - purchasePrice) / Math.abs(purchasePrice));
                annotation += " purchasePrice=" + purchasePrice;
                annotation += " profitPercentSell2=" + profitPercentSell2;
                //exitProfitInPercentMax = 100f;
                if (sellCriteria.getTakeProfitPercentForLoss() != null) {
                    takeProfitPercent2 = sellCriteria.getTakeProfitPercentForLoss();
                }
                if (sellCriteria.getExitProfitInPercentMaxForLoss2() != null) {
                    exitProfitInPercentMax = sellCriteria.getExitProfitInPercentMaxForLoss2();
                }
            }
            var candleList = candleHistoryService.getCandlesByFigiBetweenDateTimes(candle.getFigi(),
                    startDate, candle.getDateTime(), strategy.getInterval());
            var maxPrice = candleList.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).max().orElse(-1);
            var minPrice = candleList.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).min().orElse(-1);
            if (sellCriteria.getIsExitProfitInPercentMaxMax()) {
                maxPrice = candleList.stream().mapToDouble(value -> value.getHighestPrice().doubleValue()).max().orElse(-1);
                minPrice = candleList.stream().mapToDouble(value -> value.getLowestPrice().doubleValue()).min().orElse(-1);
            }
            var minPercent = 100f * (purchasePrice - minPrice) / Math.abs(purchasePrice);
            var isLoss = false;
            if (sellCriteria.getExitProfitInPercentMaxForLoss() != null
                    && minPercent > sellCriteria.getExitProfitInPercentMaxForLoss()
                    //&& profitPercent.floatValue() < 0
                    && startDate.isBefore(curBeginHour)
            ) {
                isLoss = true;
                annotation += " minHighestPrice=" + minPrice;
                purchasePrice = minPrice;
                if (sellCriteria.getTakeProfitPercentForLoss() != null) {
                    takeProfitPercent2 = sellCriteria.getTakeProfitPercentForLoss();
                }
            }
            var percent = maxPrice - purchasePrice;
            Double percent2 = 0.0;
            if (percent > 0) {
                percent2 = 100f * (percent - (maxPrice - candle.getClosingPrice().doubleValue())) / percent;
            }
            annotation += " maxHighestPrice=" + maxPrice + "(" + candleList.size() + ")" + " ClosingPrice=" + candle.getClosingPrice()
                    + " percent2=" + percent2;
            annotation += " exitProfitInPercentMax=" + exitProfitInPercentMax;
            annotation += " takeProfitPercent=" + takeProfitPercent;
            annotation += " takeProfitPercent2=" + takeProfitPercent2;
            var isGoodPrice = (profitPercentSell > takeProfitPercent2
                    || profitPercentSell2 > takeProfitPercent);
            if (percent2 >= sellCriteria.getExitProfitInPercentMin()
                    && (percent2 <= exitProfitInPercentMax || isLoss)
                    && (isGoodPrice || isLoss)
            ) {
                if (!isGoodPrice
                        //&& candle.getClosingPrice().floatValue() > factorial.getLoss()
                ) {
                    var isLoopEnable = true;
                    if (sellCriteria.getExitProfitInPercentMaxLoopIgnoreSize() > 0) {
                        var candleListPrevLoopIgnore = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                                candle.getDateTime(), sellCriteria.getExitProfitInPercentMaxLoopIgnoreSize(), strategy.getFactorialInterval());
                        isLoopEnable = candleListPrevLoopIgnore.get(0).getDateTime().isAfter(order.getPurchaseDateTime());
                    }
                    if (isLoopEnable) {
                        annotation += " save key " + keySell;
                        addCashedIsBuyValue(keySell, BuyData.builder()
                                .price(candle.getClosingPrice().doubleValue())
                                .dateTime(candle.getDateTime())
                                .build());
                    } else {
                        annotation += " loop ignore";
                    }
                } else {
                    annotation += "ok ExitProfitInPercentMax";
                    res = true;
                }
            }
        } else if (sellCriteria.getTakeProfitPercent() != null
                && profitPercent.floatValue() > sellCriteria.getTakeProfitPercent()
        ) {
            if (sellCriteria.getExitProfitLossPercent() != null) {
                var candleList = candleHistoryService.getCandlesByFigiBetweenDateTimes(candle.getFigi(),
                        order.getPurchaseDateTime(), candle.getDateTime(), strategy.getInterval());
                var maxPrice = candleList.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).max().orElse(-1);
                var percent = 100f * (maxPrice - candle.getClosingPrice().doubleValue()) / Math.abs(maxPrice);
                annotation += " maxPrice=" + maxPrice + "(" + candleList.size() + ")" + " ClosingPrice=" + candle.getClosingPrice()
                        + " percent=" + percent;
                if (percent > sellCriteria.getExitProfitLossPercent()) {
                    res = true;
                }
            } else {
                res = true;
            }
        }

        if (sellCriteria.getSellDownLength() != null
                || sellCriteria.getSellUpLength() != null
        ) {
            String keySell = "sellDown" + candle.getFigi() + printDateTime(order.getPurchaseDateTime());
            var sellData = getCashedIsBuyValue(keySell);
            if (res || sellData != null) {
                var length = 0;
                if (sellCriteria.getSellDownLength() != null) {
                    length = sellCriteria.getSellDownLength();
                }
                if (sellCriteria.getSellUpLength() != null) {
                    length = Math.max(length, sellCriteria.getSellUpLength());
                }
                var candleList = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                        candle.getDateTime(), length + 1, strategy.getInterval());
                Boolean isAllDownCandle = false;
                Boolean isAllUpCandle = false;
                if (sellCriteria.getSellDownLength() != null) {
                    isAllDownCandle = true;
                    for (var i = length - sellCriteria.getSellDownLength(); i < length; i++) {
                        var c = candleList.get(i);
                        //if (i == (length - sellCriteria.getSellDownLength())) {
                        //    annotation += " down from " + c.getDateTime();
                        //}
                        if (c.getClosingPrice().doubleValue() > c.getOpenPrice().doubleValue()) {
                            annotation += " up " + i + " " + c.getClosingPrice() + " > " + c.getOpenPrice().doubleValue();
                            isAllDownCandle = false;
                            break;
                        }
                        //annotation += " downOk " + i + " " + c.getClosingPrice() + " <= " + c.getOpenPrice().doubleValue();
                    }
                }
                if (sellCriteria.getSellUpLength() != null) {
                    isAllUpCandle = true;
                    for (var i = length - sellCriteria.getSellUpLength(); i < length; i++) {
                        var c = candleList.get(i);
                        //if (i == (length - sellCriteria.getSellUpLength())) {
                        //    annotation += " up from " + c.getDateTime();
                        //}
                        if (c.getClosingPrice().doubleValue() < c.getOpenPrice().doubleValue()) {
                            annotation += " down " + i + " " + c.getClosingPrice() + " < " + c.getOpenPrice().doubleValue();
                            isAllUpCandle = false;
                            break;
                        }
                        //annotation += " upOk " + i + " " + c.getClosingPrice() + " >= " + c.getOpenPrice().doubleValue();
                    }
                }
                if (isAllDownCandle || isAllUpCandle) {
                    annotation += " AllUpDownCandle OK";
                    res = true;
                } else {
                    if (res && sellData == null) {
                        addCashedIsBuyValue(keySell, BuyData.builder()
                                .price(candle.getClosingPrice().doubleValue())
                                .dateTime(candle.getDateTime())
                                .build());
                    }
                    annotation += " AllDownCandle FALSE";
                    res = false;
                }
            }
        }

        var candleIntervalBuy = false;
        var candleIntervalSell = false;
        Boolean isOrderUpCandle = false;
        CandleIntervalBuyResult candleBuyRes = null;
        clearCandleInterval(newStrategy, candle);
        var candleIntervalUpDownData = getCurCandleIntervalUpDownData(newStrategy, candle);
        if (candleIntervalUpDownData.beginDownFirst == null) {
            annotation += " somwthing wrong: " + candleIntervalUpDownData.annotation;
        }

        List<Double> ema = null;
        List<Double> emaPrev = null;
        BigDecimal takeProfitPrice = order.getDetails().getCurrentPrices().getOrDefault("takeProfitPrice", BigDecimal.ZERO);
        BigDecimal takeProfitPriceOrig = takeProfitPrice;
        BigDecimal stopLossPrice = null;
        BigDecimal stopLossPriceBottomA = null;
        BigDecimal maxPriceProfitStep = null;
        if (null != buyCriteria.getEmaLength() && candleIntervalUpDownData.beginDownFirst != null) {
            annotation += " ema " + candleIntervalUpDownData.beginDownFirst.candle.getDateTime() + " - " + candleIntervalUpDownData.endPost.candle.getDateTime();
            //var candles = candleHistoryService.getCandlesByFigiBetweenDateTimes(candle.getFigi(), candleIntervalUpDownData.beginDownFirst.candle.getDateTime(), candleIntervalUpDownData.endPost.candle.getDateTime(), strategy.getInterval());
            //if (candles != null && candles.size() > 0) {
            //var emaLength = candles.size() * 20;
            var emaLength = buyCriteria.getEmaLength();
            annotation += " size=" + emaLength;
            //ema = getEma(candle.getFigi(), candle.getDateTime(), emaLength, strategy.getInterval(), CandleDomainEntity::getClosingPrice, 1);
            //emaPrev = getSma(candle.getFigi(), candle.getDateTime(), emaLength, strategy.getInterval(), CandleDomainEntity::getClosingPrice, 1);
        }

        String keyCandles = buildKeyCandleIntervals(strategy, candle);
        if (!res) {
            var upRes = isOrderCandleUp(newStrategy, candle, order, buyCriteria, sellCriteria);
            isOrderUpCandle = upRes.res;
            annotation += upRes.annotation;
        }
        if (
                (!res || profitPercent.floatValue() > 0 || isOrderUpCandle)
                && buyCriteria.getCandleIntervalMinPercent() != null
        ) {
            var candleIntervalRes = checkCandleInterval(candle, sellCriteria);
            annotation += candleIntervalRes.annotation;
            res = candleIntervalRes.res;
            annotation += " res candleInterval=" + res;
            if (!candleIntervalRes.res
                    //&& !isOrderUpCandle
                    && sellCriteria.getCandleUpLength() > 1
                    && null != sellCriteria.getCandleTrySimple()
            ) {
                var sellCriteriaSimple = sellCriteria.clone();
                sellCriteriaSimple.setCandleUpLength(sellCriteria.getCandleUpLength() / sellCriteria.getCandleTrySimple());
                sellCriteriaSimple.setCandleIntervalMinPercent(sellCriteria.getCandleIntervalMinPercent() * sellCriteria.getCandleTrySimple());
                candleIntervalRes = checkCandleInterval(candle, sellCriteriaSimple);
                annotation += " res candleIntervalSimple=" + candleIntervalRes.res;
                res = candleIntervalRes.res;
                if (res) {
                    annotation += candleIntervalRes.annotation;
                }
            }
            String keySell = "sellUp" + strategy.getExtName() + candle.getFigi() + printDateTime(order.getPurchaseDateTime());
            var isSkipOnlyUp = false;
            if (
                    true
                    // && candleIntervalRes.res
                    && null != buyCriteria.getCandleOnlyUpLength()
                    && null != sellCriteria.getCandleOnlyUpProfitMinPercent()
                    && isOrderUpCandle
            ) {
                res = false;
                var sellData = getCashedIsBuyValue(keySell);
                if (sellData == null) {
                    annotation += " CandleOnlyUpProfitMinPercent = " + sellCriteria.getCandleOnlyUpProfitMinPercent();
                    if (profitPercent.floatValue() < sellCriteria.getCandleOnlyUpProfitMinPercent()) {
                        annotation += " SKIP only up candleInterval";
                        isSkipOnlyUp = true;
                        res = false;
                    }
                }
            }
            var isSkip = false;
            if (
                    true
                    && !isOrderUpCandle
                    && sellCriteria.getCandleProfitMinPercent() != null
                    && candleIntervalUpDownData.beginDownFirst != null
                //&& profitPercent.floatValue() > 0
            ) {
                annotation += " CandleProfitMinPercent = " + sellCriteria.getCandleProfitMinPercent();
                var factorPrice = (candle.getClosingPrice().floatValue() - candleIntervalUpDownData.minClose)
                        / (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose);
                var factorPriceOrder = (order.getPurchasePrice().floatValue() - candleIntervalUpDownData.minClose)
                        / (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose);
                annotation += " factorPrice = " + factorPrice + " < " + sellCriteria.getCandlePriceMinFactor();
                annotation += " factorPriceOrder = " + factorPriceOrder;
                var isFactorPrice = false;
                if (sellCriteria.getCandlePriceMinFactor() == null
                        || factorPrice < sellCriteria.getCandlePriceMinFactor()
                        || factorPriceOrder > sellCriteria.getCandlePriceMinFactor()
                ) {
                    isFactorPrice = true;
                    if (sellCriteria.getCandlePriceMinFactor() != null && factorPriceOrder > sellCriteria.getCandlePriceMinFactor()) {
                        var intervalCandles = getCandleIntervals(newStrategy, candle);
                        var upFirst = intervalCandles.stream().filter(ic ->
                                !ic.isDown
                                        && order.getPurchaseDateTime().isBefore(ic.getCandle().getDateTime())).findFirst().orElse(null);
                        if (upFirst != null) {
                            annotation += " upFirst: " + printDateTime(upFirst.candle.getDateTime());
                            var downAfterUpFirst = intervalCandles.stream().filter(ic ->
                                    ic.isDown
                                            && upFirst.candle.getDateTime().isBefore(ic.getCandle().getDateTime())).findFirst().orElse(null);
                            if (downAfterUpFirst != null
                                    && candle.getClosingPrice().floatValue() > candleIntervalUpDownData.maxClose
                            ) {
                                annotation += " FIND DOWN AFTER UP (skip factorPriceOrder): " + printDateTime(downAfterUpFirst.candle.getDateTime());
                                isFactorPrice = false;
                            }
                        }
                    }
                }
                if (
                        // в коридоре минимального процента около цены покупки
                        (profitPercent.floatValue() < sellCriteria.getCandleProfitMinPercent()
                                && profitPercent.floatValue() > -sellCriteria.getCandleProfitMinPercent()
                        )
                        // текущая цена близка к дну или цена покупки около потолка
                        && isFactorPrice
                        // текущая цена выше дна или ?? цена покупки меньше дна
                        && (candle.getClosingPrice().floatValue() > Math.min(candleIntervalUpDownData.minClose, candleIntervalUpDownData.priceEnd)
                                || order.getPurchasePrice().floatValue() < Math.min(candleIntervalUpDownData.minClose, candleIntervalUpDownData.priceEnd)
                        )
                ) {
                    var skip = true;
                    if (null != sellCriteria.getCandleUpSkipLength()) {
                        List<CandleIntervalResultData> sellPoints = getIntervalSellPoints(
                                newStrategy,
                                candle,
                                candleIntervalRes,
                                sellCriteria.getCandleUpPointLength(),
                                sellCriteria.getCandleUpSkipLength()
                        );
                        annotation += " try SKIP MIN SKIP by size: " + sellPoints.size() + " > " + sellCriteria.getCandleUpSkipLength();
                        if (null != sellCriteria.getCandleUpSkipLength()
                                && sellPoints.size() > sellCriteria.getCandleUpSkipLength()
                        ) {
                            annotation += " OK";
                            skip = false;
                        }
                    }
                    if (skip) {
                        annotation += " SKIP MIN CANDLE INTERVAL";
                        annotation += candleIntervalUpDownData.annotation;
                        //res = false;
                        isSkip = true;
                    }
                }
            }
            var isSkipDown = false;
            var isSkipUp = false;
            var isSkipUpBottom = false;
            Double middleCandlePrice = null;
            if (null != candleIntervalUpDownData.minClose) {
                middleCandlePrice = (candleIntervalUpDownData.minClose + (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) * 0.4);
            }
            var isIntervalUp = order.getDetails().getBooleanDataMap().getOrDefault("isIntervalUp", false);
            var isDownWithLimits = order.getDetails().getBooleanDataMap().getOrDefault("isDownWithLimits", false);
            var isMinMin = order.getDetails().getBooleanDataMap().getOrDefault("isMinMin", false);
            annotation += " isDownWithLimits=" + isDownWithLimits;
            annotation += " isIntervalUp=" + isIntervalUp;
            annotation += " isMinMin=" + isMinMin;
            if ((isIntervalUp || isDownWithLimits)  && null != candleIntervalUpDownData.maxCandle) {
                var maxBuyIntervalPrice = order.getDetails().getCurrentPrices().getOrDefault("maxBuyIntervalPrice", BigDecimal.ZERO);
                annotation += " maxBuyIntervalPrice=" + printPrice(maxBuyIntervalPrice);
                var candleIntervalUpDownDataPrev = getPrevCandleIntervalUpDownData(newStrategy, candleIntervalUpDownData);
                annotation += " maxCandle: " + printDateTime(candleIntervalUpDownData.beginDownFirst.candle.getDateTime());
                if (candleIntervalUpDownDataPrev.minClose == null) {
                    annotation += " something wrong: " + candleIntervalUpDownDataPrev.annotation;
                } else {
                    annotation += " Prev.min-maxCandle=" + printPrice(candleIntervalUpDownDataPrev.minClose) + "-" +printPrice(candleIntervalUpDownDataPrev.maxClose);
                    var maxDiffP = 100f * Math.abs((candleIntervalUpDownDataPrev.maxClose - candleIntervalUpDownData.maxClose) / (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose));
                    annotation += " maxDiffP=" + printPrice(maxDiffP);
                    if (
                            candleIntervalUpDownDataPrev.maxClose > candleIntervalUpDownData.maxClose
                            && maxDiffP > 5f
                            && candleIntervalUpDownDataPrev.minClose > candleIntervalUpDownData.minClose
                    ) {
                        //if (
                        //        (maxBuyIntervalPrice.equals(BigDecimal.ZERO)
                        //        || candleIntervalUpDownData.minClose < maxBuyIntervalPrice.floatValue())
                        //) {
                            var percent = 100f * (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) / Math.abs(candleIntervalUpDownData.minClose);
                            if (
                                    candleIntervalUpDownDataPrev.minClose < candleIntervalUpDownData.minClose
                                    && percent < buyCriteria.getCandlePriceMinFactor()
                            ) {
                                percent = buyCriteria.getCandlePriceMinFactor();
                            }
                            annotation += " tPPercent=" + printPrice(percent);
                            percent = percent * 0.66f;
                            takeProfitPriceOrig = takeProfitPrice = BigDecimal.valueOf(candleIntervalUpDownData.endPost.candle.getClosingPrice().doubleValue()
                                    + candleIntervalUpDownData.endPost.candle.getClosingPrice().abs().doubleValue() * percent / 100f);
                            annotation += " takeProfitPrice=" + printPrice(takeProfitPrice);
                        //}
                    } else if (candleIntervalUpDownDataPrev.maxClose < candleIntervalUpDownData.maxClose) {
                        annotation += " PmaxCandle: " + printDateTime(candleIntervalUpDownDataPrev.beginDownFirst.candle.getDateTime());
                        var candleIntervalUpDownDataPrevPrev = getPrevCandleIntervalUpDownData(newStrategy, candleIntervalUpDownDataPrev);
                        if (candleIntervalUpDownDataPrevPrev.minClose == null) {
                            annotation += " something wrong: " + candleIntervalUpDownDataPrev.annotation;
                        } else {
                            annotation += " PPrev.min-maxCandle=" + printPrice(candleIntervalUpDownDataPrevPrev.minClose) + "-" +printPrice(candleIntervalUpDownDataPrevPrev.maxClose);
                            annotation += " PPmaxCandle: " + printDateTime(candleIntervalUpDownDataPrevPrev.maxCandle.getDateTime());
                            if (
                                    (candleIntervalUpDownDataPrevPrev.maxClose < candleIntervalUpDownDataPrev.maxClose
                                    && candleIntervalUpDownDataPrevPrev.minClose < candleIntervalUpDownData.minClose)
                                    || candleIntervalUpDownDataPrev.minClose > candleIntervalUpDownData.minClose
                            ) {
                                //limitPrice = BigDecimal.valueOf(candleIntervalUpDownData.maxClose + (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose));
                            }
                        }
                    }
                }
            }
            annotation += " middlePrice " + printPrice(middleCandlePrice) + " < " + printPrice(order.getPurchasePrice());
            if (
                    (isIntervalUp || isMinMin)
                    && profitPercent.doubleValue() < (sellCriteria.getCandleProfitMinPercent() == null ? 0.0f: sellCriteria.getCandleProfitMinPercent())
                    && candleIntervalUpDownData.beginDownFirst != null
            ) {
                if (
                        !order.getPurchaseDateTime().isBefore(candleIntervalUpDownData.endPost.candle.getDateTime())
                        && null != middleCandlePrice
                        && candle.getClosingPrice().doubleValue() > middleCandlePrice
                        && profitPercent.doubleValue() < (sellCriteria.getCandleProfitMinPercent() == null ? 0.0f: sellCriteria.getCandleProfitMinPercent())
                ) {
                    annotation += " SKIP UP ";
                    isSkipUp = true;
                    isSkipUpBottom = true;
                } else {
                    var priceBottom = order.getPurchasePrice().floatValue() - (candleIntervalUpDownData.maxClose.floatValue() - candleIntervalUpDownData.minClose.floatValue()) / 2;
                    annotation += " priceBottom: " + printPrice(priceBottom);
                    if (
                            order.getPurchasePrice().floatValue() < candleIntervalUpDownData.maxClose.floatValue()
                            && priceBottom < candle.getClosingPrice().floatValue()
                    ) {
                        annotation += " SKIP UP BOTTOM";
                        isSkipUp = true;
                        isSkipUpBottom = true;
                    }
                }
            }
            if (
                    !isSkipUp
                    && isIntervalUp
            ) {
                annotation += " isIntervalUp " + printDateTime(candleIntervalUpDownData.endPost.candle.getDateTime());
                if (!order.getPurchaseDateTime().isBefore(candleIntervalUpDownData.endPost.candle.getDateTime())) {
                    annotation += " SKIP UP IN BUY INTERVAL";
                    isSkipUp = true;
                    isSkipUpBottom = true;
                } else {
                    stopLossPrice = order.getDetails().getCurrentPrices().getOrDefault("stopLossPrice", BigDecimal.ZERO);
                    if (!stopLossPrice.equals(BigDecimal.ZERO)) {
                        annotation += " stopLossPrice " + printPrice(stopLossPrice);
                        if (
                                candle.getClosingPrice().compareTo(stopLossPrice) > 0
                                && profitPercent.doubleValue() < (sellCriteria.getCandleProfitMinPercent() == null ? 0.0f: sellCriteria.getCandleProfitMinPercent())
                        ) {
                            annotation += " SKIP UP UNDER STOP LOSS";
                            isSkipUp = true;
                            isSkipUpBottom = true;
                        }
                    }
                }
            }
            if (
                    null != sellCriteria.getCandleUpSkipDownBetweenFactor()
                    && !isSkipUpBottom
                    && candleIntervalUpDownData.beginDownFirst != null
            ) {
                List<CandleIntervalResultData> sellPoints = getIntervalSellPoints(
                        newStrategy,
                        candle,
                        candleIntervalRes,
                        sellCriteria.getCandleUpPointLength(),
                        sellCriteria.getCandleUpSkipLength()
                );
                if (null != sellCriteria.getCandleUpSkipDownBetweenFactor()
                        && sellPoints.size() > 1
                ) {
                    var candlesBetween = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                            candle.getFigi(),
                            sellPoints.get(1).getCandle().getDateTime(),
                            sellPoints.get(0).getCandle().getDateTime(),
                            strategy.getInterval()
                    );
                    Double maxPrice = candlesBetween.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).max().orElse(-1);
                    Double minPrice = candlesBetween.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).min().orElse(-1);
                    Double maxPrice1 = candlesBetween.subList(0, candlesBetween.size() / 3 + 1).stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).max().orElse(-1);
                    Double maxPrice2 = candlesBetween.subList(candlesBetween.size() * 2 / 3, candlesBetween.size() - 1).stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).max().orElse(-1);
                    maxPrice1 = Math.min(maxPrice1, sellPoints.get(1).getCandle().getClosingPrice().doubleValue());
                    var downFactor = (maxPrice - minPrice) / (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose);
                    var candleDownFactor = (maxPrice1 - maxPrice2)
                            / (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose);
                    var candleDownFactorUnder = (Math.min(maxPrice1, maxPrice2) - candleIntervalUpDownData.maxClose)
                            / (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose);
                    annotation += " downFactor = " + printPrice(downFactor) + "(" + printPrice(maxPrice1) + ", " + printPrice(maxPrice2) + ") > " + sellCriteria.getCandleUpSkipDownBetweenFactor();
                    annotation += " candleDownFactor = " + candleDownFactor;
                    annotation += " under = " + candleDownFactorUnder;
                    if (candleDownFactorUnder > 0) {
                        candleDownFactor = candleDownFactor / (1f + candleDownFactorUnder);
                        candleDownFactor = candleDownFactor * candleDownFactor;
                        annotation += " new candleDownFactor = " + candleDownFactor;
                    }
                    if (downFactor > sellCriteria.getCandleUpSkipDownBetweenFactor()) {
                        if (
                                maxPrice1 > maxPrice2
                                && candleDownFactor > (sellCriteria.getCandleUpSkipDownBetweenFactor() / 10f)
                        ) {
                            annotation += " SKIP MIN SKIP by down";
                            isSkip = false;
                            isSkipDown = true;
                        } else {
                            isSkipUp = true;
                        }
                    }
                }
            }
            if (res && isSkip) {
                res = false;
            }
            var isMiddleOk = isSkip;
            if (
                    !isOrderUpCandle
                    && !isSkip
                    && !isSkipDown
                    && null != sellCriteria.getCandleUpPointLength()
                    && null != sellCriteria.getCandleUpMiddleFactor()
                    && candleIntervalUpDownData.beginDownFirst != null
            ) {
                List<CandleIntervalResultData> sellPoints = getIntervalSellPoints(
                        newStrategy,
                        candle,
                        candleIntervalRes,
                        //CandleIntervalResultData.builder().res(false).build(),
                        sellCriteria.getCandleUpPointLength(),
                        20
                );
                annotation += " sellPoints.size(): " + sellPoints.size();
                var prevPoint = order.getPurchasePrice().doubleValue();
                Double curPoint = null;
                if (sellCriteria.getCandleUpMiddleFactorMinBegin() > 0) {
                    var candlesBetween = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                            candle.getFigi(),
                            order.getPurchaseDateTime(),
                            candle.getDateTime(),
                            strategy.getInterval()
                    );
                    if (candlesBetween.size() <= sellCriteria.getCandleUpMiddleFactorMinBegin()) {
                        //sellPoints.clear();
                    } else if (sellPoints.size() == 0) {
                        var maxCandle = candlesBetween
                                .stream().reduce((first, second) ->
                                        first.getClosingPrice().compareTo(second.getClosingPrice()) > 0 ? first : second).orElse(null);
                        annotation += " maxCandle: " + printDateTime(maxCandle.getDateTime());
                        var priceBottom = order.getPurchasePrice().floatValue() - (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) * 0.66f;
                        annotation += " priceBottom: " + printPrice(priceBottom);
                        if (
                                priceBottom > candle.getClosingPrice().floatValue()
                                && candle.getClosingPrice().floatValue() < candleIntervalUpDownData.minClose
                                && candle.getOpenPrice().floatValue() < candleIntervalUpDownData.minClose
                        ) {
                            sellPoints.add(CandleIntervalResultData.builder()
                                    .candle(maxCandle)
                                    .build());
                        }
                    }
                }
                if (sellPoints.size() > 0 || (sellPoints.size() == 1 && res)) {
                    curPoint = Math.max(sellPoints.get(0).candle.getClosingPrice().doubleValue(), sellPoints.get(0).candle.getOpenPrice().doubleValue());
                    var candleResDown = getCandleIntervals(newStrategy, candle).stream().filter(
                            c -> c.isDown
                                    && !c.candle.getDateTime().isAfter(candle.getDateTime())
                    ).reduce((first, second) -> second).orElse(null);
                    if (null != candleResDown) {
                        prevPoint = Math.min(
                                prevPoint,
                                Math.min(candleResDown.candle.getClosingPrice().doubleValue(), candleResDown.candle.getOpenPrice().doubleValue())
                        );
                    }
                }
                if (sellPoints.size() > 0 && null != curPoint) {
                    var prevPointInit = prevPoint;
                    annotation += " prevPoint: " + printPrice(prevPoint);
                    var num = 1;
                    for(var i = sellPoints.size() - 1; i > 0; i--) {
                        var pp = Math.min(sellPoints.get(i).candle.getClosingPrice().doubleValue(), sellPoints.get(i).candle.getOpenPrice().doubleValue());
                        var numS = Math.sqrt(num);
                        if (
                                (curPoint - pp) > numS * (pp - prevPoint)
                                && numS * (pp - prevPointInit) <= (curPoint - prevPoint)
                        ) {
                            annotation += " new prevPoint: " + i + " " + printPrice(pp) + ": "
                                    + printPrice(curPoint - pp) + ">" + printPrice(pp - prevPoint)
                                    + ": " + printPrice(pp - prevPointInit) + "<=" + printPrice(curPoint - prevPoint);
                            prevPoint = Math.max(
                                    prevPoint,
                                    pp
                            );
                            num++;
                        }
                    }
                }
                if (null != curPoint) {
                    var middlePrice = prevPoint + (curPoint - prevPoint) * sellCriteria.getCandleUpMiddleFactor();
                    var candlesPrevArray = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(), candle.getDateTime(), 2, strategy.getInterval());
                    var candlePrev = candlesPrevArray.get(0);
                    var candlePrevMaxPrice = candlePrev.getClosingPrice().doubleValue();
                    annotation += " middlePrice: " + printPrice(candlePrevMaxPrice) + " < " + printPrice(middlePrice) + "(" + printPrice(prevPoint) + " - " + printPrice(curPoint) + ")";
                    if (
                            true
                            && !res
                            && !isSkipUp
                    ) {
                        if (!isIntervalUp) { // || (emaPrev != null && emaPrev.get(emaPrev.size() - 1) < candle.getClosingPrice().doubleValue())) {
                            annotation += "emaPrev ";
                            res = candlePrevMaxPrice < middlePrice;
                        } else {
                            res = candlePrevMaxPrice < middlePrice && candle.getClosingPrice().doubleValue() < middlePrice;
                        }
                        if (res) {
                            annotation += " MIDDLE CANDLE OK";
                            isMiddleOk = true;
                        }
                    } else {
                        annotation += " SKIP MIDDLE";
                        res = false;
                    }
                }
            }
            if (candleIntervalRes.res) {
                candleIntervalSell = true;
                addCandleInterval(keyCandles, candleIntervalRes);
            } else {
                var candleIntervalBuyRes = checkCandleInterval(candle, buyCriteria);
                candleIntervalBuy = candleIntervalBuyRes.res;
                if (candleIntervalBuyRes.res) {
                    annotation += candleIntervalBuyRes.annotation;
                }
                if (
                        candleIntervalBuyRes.res
                        && !isMiddleOk
                        && !isSkipUpBottom
                        && sellCriteria.getDownAfterUpSize() > 0
                ) {
                    if (candleIntervalUpDownData.minClose != null
                            //&& order.getPurchasePrice().doubleValue() < candle.getClosingPrice().doubleValue()
                            //&& candle.getClosingPrice().floatValue() < candleIntervalUpDownData.minClose
                    ) {
                        annotation += " res BUY OK candleInterval: " + candleIntervalBuyRes.annotation;
                        var intervalCandles = getCandleIntervals(newStrategy, candle);
                        var upCount = intervalCandles.stream().filter(ic -> !ic.isDown && order.getPurchaseDateTime().isBefore(ic.getCandle().getDateTime())).collect(Collectors.toList());
                        if (
                                upCount.size() > 0
                                && null != sellCriteria.getCandleUpPointLength()
                        ) {
                            List<CandleIntervalResultData> points = getIntervalPoints(
                                    newStrategy,
                                    candle,
                                    candleIntervalBuyRes,
                                    sellCriteria.getCandleUpPointLength(),
                                    2 + sellCriteria.getDownAfterUpSize()
                            );
                            if (
                                    points.size() > 0
                                    && points.get(0).isDown
                                    && points.get(0).getCandle().getDateTime().isBefore(upCount.get(upCount.size() - 1).getCandle().getDateTime())
                            ) {
                                annotation += " DOWN AFTER UP SKIP: " + upCount.size() + ": " + points.size() + ": " + printDateTime(points.get(0).getCandle().getDateTime()) + " < " + printDateTime(upCount.get(upCount.size() - 1).getCandle().getDateTime());
                            } else if (points.size() > 2
                                    && points.get(0).isDown
                                    && !points.get(1).isDown
                                    && points.get(2).isDown
                            ) {
                                annotation += " DOWN AFTER UP SKIP UP-DOWN 2";
                            } else if (points.size() > 1
                                    && !points.get(0).isDown
                                    && points.get(1).isDown
                            ) {
                                annotation += " DOWN AFTER UP SKIP UP-DOWN 1";
                            } else {
                                var countDown = 0;
                                for(var i = 0; i < points.size(); i++) {
                                    if (points.get(i).isDown) {
                                        countDown++;
                                    } else {
                                        break;
                                    }
                                }

                                annotation += " candleInterval TRY DOWN AFTER UP: countDown=" + countDown + " size=" + upCount.size() + ": " + printDateTime(upCount.get(0).candle.getDateTime());
                                if (countDown >= sellCriteria.getDownAfterUpSize()) {
                                    annotation += " OK";
                                    res = true;
                                }
                            }
                        } else {
                            candleBuyRes = getCandleBuyRes(newStrategy, candle);
                            annotation += " candleInterval BUY: " + candleBuyRes.annotation;
                            if (!candleBuyRes.res) {
                                annotation += " SELL OK";
                                //res = true;
                            }
                        }
                    } else if (candleIntervalUpDownData.minClose == null) {
                        annotation += " minClose NULL";
                        annotation += candleIntervalUpDownData.annotation;
                    }
                }
                if (candleIntervalBuyRes.res) {
                    addCandleInterval(keyCandles, candleIntervalBuyRes);
                }
            }
            if (sellCriteria.getProfitPercentFromSellMinPrice() != null && !isMiddleOk) {
                var sellData = getCashedIsBuyValue(keySell);
                if (res || null != sellData) {
                    annotation += " sellUpKEY=" + keySell;
                    if (res) {
                        sellData = addCashedIsBuyValue(keySell, BuyData.builder()
                                .price(candle.getClosingPrice().doubleValue())
                                .dateTime(candle.getDateTime())
                                .build());
                    }
                    annotation += " sellPrice=" + printPrice(sellData.getPrice());
                    var curProfitPercentFromSellMinPrice = 100f * (candle.getClosingPrice().floatValue() - sellData.getPrice()) / Math.abs(sellData.getPrice());
                    annotation += " curProfPerFromSell=" + curProfitPercentFromSellMinPrice
                            + " > " + sellCriteria.getProfitPercentFromSellMinPrice()
                            + " < " + sellCriteria.getProfitPercentFromSellMaxPrice()
                    ;
                    var candlesBetween = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                            candle.getFigi(),
                            sellData.getDateTime(),
                            candle.getDateTime(),
                            strategy.getInterval()
                    );
                    annotation += " between: " + candlesBetween.size() + ">" + sellCriteria.getProfitPercentFromSellMinPriceLength();
                    if (curProfitPercentFromSellMinPrice > sellCriteria.getProfitPercentFromSellMinPrice()
                            || candlesBetween.size() > sellCriteria.getProfitPercentFromSellMinPriceLength()
                            //|| (sellCriteria.getProfitPercentFromSellMaxPrice() != null
                            //&& !res
                            //&& candle.getClosingPrice().floatValue() < Math.max(candleIntervalUpDownData.minClose, candleIntervalUpDownData.priceEnd)
                            //&& curProfitPercentFromSellMinPrice < -sellCriteria.getProfitPercentFromSellMaxPrice())
                    ) {
                        annotation += " sell OK";
                        res = true;
                    } else {
                        res = false;
                    }
                }
            }
            if (
                    null != buyCriteria.getCandleOnlyUpLength()
                    && null != sellCriteria.getCandleOnlyUpStopLossPercent()
                    && isOrderUpCandle
                    && candleIntervalUpDownData.beginDownFirst != null
            ) {
                var intervalCandles = getCandleIntervals(newStrategy, candle);
                if (null != intervalCandles) {
                    var lastCandleInterval = intervalCandles.get(intervalCandles.size() - 1);
                    if (
                            !res
                            && !lastCandleInterval.isDown
                            && order.getPurchasePrice().compareTo(candle.getClosingPrice()) > 0
                    ) {
                        if (sellCriteria.getCandleOnlyUpStopLossPercent() != null) {
                            var curProfitPercent = profitPercent;
                            if (lastCandleInterval.getCandle().getClosingPrice().compareTo(purchaseRate) > 0) {
                                curProfitPercent = candle.getClosingPrice().subtract(lastCandleInterval.getCandle().getClosingPrice())
                                        .multiply(BigDecimal.valueOf(100))
                                        .divide(purchaseRate, 4, RoundingMode.HALF_DOWN);
                                annotation += " curProfitPercent = " + printPrice(curProfitPercent);
                            }
                            annotation += " CandleOnlyUpStopLossPercent = " + sellCriteria.getCandleOnlyUpStopLossPercent();
                            if (
                                    curProfitPercent.floatValue() < -sellCriteria.getCandleOnlyUpStopLossPercent()
                                    && candle.getClosingPrice().floatValue() < candleIntervalUpDownData.maxClose
                            ) {
                                annotation += " OK only up candleInterval";
                                res = true;
                            }
                        }
                    } else if (
                            !isSkipOnlyUp
                            && null != sellCriteria.getCandleExitProfitInPercentMax()
                    ) {
                        var candles = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                                candle.getFigi(),
                                order.getPurchaseDateTime(),
                                candle.getDateTime(),
                                strategy.getInterval()
                        );
                        Double maxPrice = intervalCandles.stream().filter(i -> i.getCandle().getDateTime().isAfter(order.getPurchaseDateTime()))
                                .mapToDouble(i -> i.getCandle().getClosingPrice().doubleValue()).max().orElse(-1);
                        Double minPrice = intervalCandles.stream().filter(i -> i.getCandle().getDateTime().isAfter(order.getPurchaseDateTime()))
                                .mapToDouble(i -> i.getCandle().getClosingPrice().doubleValue()).max().orElse(-1);
                        minPrice = Math.min(minPrice, order.getPurchasePrice().doubleValue());
                        //Double maxPrice = candles.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).max().orElse(-1);
                        //Double minPrice = candles.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).min().orElse(-1);
                        var percent = 100f * (candle.getClosingPrice().doubleValue() - minPrice) / (maxPrice - minPrice);
                        annotation += " minPrice = " + printPrice(minPrice);
                        annotation += " maxPrice = " + printPrice(maxPrice);
                        annotation += " CandleExitProfitInPercentMax: " + percent + " < " + sellCriteria.getCandleExitProfitInPercentMax();
                        res = percent < sellCriteria.getCandleExitProfitInPercentMax();
                        annotation += " = " + res;
                    }
                }
            }
        }

        var isIntervalUp = order.getDetails().getBooleanDataMap().getOrDefault("isIntervalUp", false);
        var isDownWithLimits = order.getDetails().getBooleanDataMap().getOrDefault("isDownWithLimits", false);
        var takeProfitPriceStart = order.getDetails().getCurrentPrices().getOrDefault("takeProfitPriceStart", BigDecimal.ZERO);
        var intervalPercentStep = order.getDetails().getCurrentPrices().getOrDefault("intervalPercentStep", BigDecimal.ZERO);
        var errorD =  BigDecimal.valueOf(0.00001);
        var isIntervalDown = true;
        if (
                sellCriteria.getIsOnlyStopLoss()
                || isIntervalUp
                || isDownWithLimits
        ) {
            res = false;
            stopLossPrice = order.getDetails().getCurrentPrices().getOrDefault("stopLossPrice", BigDecimal.ZERO);
            annotation += " stopLossPrice: " + printPrice(stopLossPrice);
            if (stopLossPrice.abs().compareTo(BigDecimal.valueOf(0.000001f)) < 0) {
                stopLossPrice = BigDecimal.ZERO;
            }
            stopLossPriceBottomA = order.getDetails().getCurrentPrices().getOrDefault("stopLossPriceBottom", BigDecimal.ZERO);
            var stopLossPriceBeginDate = order.getDetails().getDateTimes().getOrDefault("stopLossPriceBeginDate", order.getPurchaseDateTime());
            var stopLossPriceOld = stopLossPrice;
            annotation += " OnlyStopLoss: " + printPrice(stopLossPrice);
            var candleIntervalUpDownDataPrev = getPrevCandleIntervalUpDownData(newStrategy, candleIntervalUpDownData);
            annotation += " beginDownFirst: " + printDateTime(candleIntervalUpDownData.beginDownFirst.candle.getDateTime());
            if (candleIntervalUpDownDataPrev.minClose == null) {
                annotation += " something wrong: " + candleIntervalUpDownDataPrev.annotation;
            }
            annotation += " minClose=" + printPrice(candleIntervalUpDownData.minClose) + "-" + printPrice(candleIntervalUpDownData.maxClose);
            annotation += " PminClose=" + printPrice(candleIntervalUpDownDataPrev.minClose) + "-" + printPrice(candleIntervalUpDownDataPrev.maxClose);
            if (
                    order.getPurchaseDateTime().isBefore(candleIntervalUpDownData.endPost.candle.getDateTime())
                    && candleIntervalUpDownDataPrev.minClose != null
            ) {
                var maxBuyIntervalPrice = order.getDetails().getCurrentPrices().getOrDefault("maxBuyIntervalPrice", BigDecimal.ZERO);
                annotation += " maxBuyIntervalPrice=" + printPrice(maxBuyIntervalPrice);
                var percentCur = (100f * (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) / Math.abs(candleIntervalUpDownData.minClose));
                var percentPrev = (100f * (candleIntervalUpDownDataPrev.maxClose - candleIntervalUpDownDataPrev.minClose) / Math.abs(candleIntervalUpDownDataPrev.minClose));
                var percent = Math.max((percentCur + percentPrev) / 2f, percentCur);
                if (percent < buyCriteria.getCandlePriceMinFactor()) {
                    annotation += " percent=" + printPrice(percent) + " < " + printPrice(buyCriteria.getCandlePriceMinFactor());
                    percent = buyCriteria.getCandlePriceMinFactor();
                }

                var mPrice = candleIntervalUpDownData.minClose + Math.abs(candleIntervalUpDownData.minClose) * percent / 100f;
                //var mPrice = candleIntervalUpDownData.minClose + (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) * 2.f;
                var lossPresent = 0.33f;
                annotation += " mPrice=" + printPrice(mPrice);
                if (!takeProfitPriceStart.equals(BigDecimal.ZERO)) {
                    mPrice = takeProfitPriceStart.floatValue() + takeProfitPriceStart.abs().floatValue() * intervalPercentStep.floatValue() / 100f;
                    lossPresent = intervalPercentStep.floatValue() *  0.33f;
                }
                var newValue1 = BigDecimal.valueOf(
                        candleIntervalUpDownData.minClose - (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) * lossPresent
                );
                annotation += " newValue1=" + printPrice(newValue1);
                if (
                        candleIntervalUpDownDataPrev.minClose != null
                        && ((candleIntervalUpDownDataPrev.minClose > candleIntervalUpDownData.minClose
                                || Objects.equals(candleIntervalUpDownDataPrev.minClose, candleIntervalUpDownData.minClose))
                                && candleIntervalUpDownDataPrev.maxClose > candleIntervalUpDownData.maxClose)
                        && stopLossPrice.doubleValue() < candleIntervalUpDownData.minClose
                        && (maxBuyIntervalPrice.equals(BigDecimal.ZERO)
                                || mPrice > maxBuyIntervalPrice.floatValue())
                        && newValue1.compareTo(stopLossPrice) > 0
                        && newValue1.compareTo(order.getPurchasePrice()) > 0
                ) {
                    stopLossPrice = newValue1;
                    stopLossPriceBottomA = stopLossPriceBottomA.max(stopLossPrice.subtract(BigDecimal.valueOf((candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) * 2f * lossPresent)));
                    annotation += " new stopLossPrice by DOWN DOWN=" + printPrice(stopLossPrice) + "-" + printPrice(stopLossPriceBottomA);
                } else if (
                    candleIntervalUpDownDataPrev.minClose != null
                    //&& order.getPurchaseDateTime().isBefore(candleIntervalUpDownDataPrev.endPost.candle.getDateTime())
                ) {
                    if (
                            candleIntervalUpDownDataPrev.minClose != null
                            && candleIntervalUpDownDataPrev.minClose > candleIntervalUpDownData.minClose
                            && stopLossPrice.doubleValue() < candleIntervalUpDownData.minClose
                            && (maxBuyIntervalPrice.equals(BigDecimal.ZERO) || candleIntervalUpDownData.minClose > maxBuyIntervalPrice.floatValue())
                            && order.getPurchaseDateTime().isBefore(candleIntervalUpDownDataPrev.endPost.candle.getDateTime())
                    ) {
                        stopLossPrice = BigDecimal.valueOf(candleIntervalUpDownData.minClose);
                        stopLossPriceBottomA = stopLossPriceBottomA.max(BigDecimal.valueOf(candleIntervalUpDownData.minClose - (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose)));
                        annotation += " new stopLossPrice by DOWN=" + printPrice(stopLossPrice) + "-" + printPrice(stopLossPriceBottomA);
                    } else {
                        var candlesList = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                                candle.getFigi(),
                                order.getPurchaseDateTime(),
                                candle.getDateTime(),
                                strategy.getInterval()
                        );
                        var maxCandle = candlesList
                                .stream().reduce((first, second) ->
                                        first.getClosingPrice().compareTo(second.getClosingPrice()) > 0 ? first : second).orElse(null);
                        if (maxCandle != null) {
                            annotation += " maxCandle: " + printDateTime(maxCandle.getDateTime()) + ":" + printPrice(maxCandle.getClosingPrice());
                            var candleIntervalUpDownDataPrevMax = getCurCandleIntervalUpDownData(newStrategy, maxCandle);
                            if (candleIntervalUpDownDataPrevMax.minClose == null) {
                                annotation += " something wrong: " + candleIntervalUpDownDataPrev.annotation;
                            } else {
                                annotation += " PMaxMinClose=" + printPrice(candleIntervalUpDownDataPrevMax.minClose) + "-" + printPrice(candleIntervalUpDownDataPrevMax.maxClose);
                                var percentMax = (100f * (candleIntervalUpDownDataPrevMax.maxClose - candleIntervalUpDownDataPrevMax.minClose) / Math.abs(candleIntervalUpDownDataPrevMax.minClose));
                                if (percentMax < buyCriteria.getCandlePriceMinFactor()) {
                                    annotation += " percentMax=" + printPrice(percentMax) + " < " + printPrice(buyCriteria.getCandlePriceMinFactor());
                                    percentMax = buyCriteria.getCandlePriceMinFactor();
                                }
                                var newValue = candleIntervalUpDownDataPrevMax.minClose - Math.abs(candleIntervalUpDownDataPrevMax.minClose) * percentMax * 2f / 100f;
                                var newStopLossPriceBottomA = newValue - Math.abs(candleIntervalUpDownDataPrevMax.minClose) * percentMax * 1f / 100f;
                                if (
                                        candleIntervalUpDownDataPrevMax != null
                                        && stopLossPrice.doubleValue() < newValue
                                ) {
                                    stopLossPrice = BigDecimal.valueOf(newValue);
                                    stopLossPriceBottomA = stopLossPriceBottomA.max(BigDecimal.valueOf(newStopLossPriceBottomA));
                                    annotation += "new stopLossPrice by MAX interval=" + printPrice(stopLossPrice) + "-" + printPrice(stopLossPriceBottomA);
                                }
                            }
                        }
                    }
                }

                if (
                        candleIntervalUpDownDataPrev.maxClose != null
                        && candleIntervalUpDownDataPrev.maxClose < candleIntervalUpDownData.maxClose
                        && candleIntervalUpDownData.maxCandle.getClosingPrice().floatValue() > candleIntervalUpDownDataPrev.maxClose
                        && order.getPurchasePrice().floatValue() < candleIntervalUpDownData.minClose - (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) * 0.5f
                ) {
                    var candleIntervalUpDownDataPrevPrev = getPrevCandleIntervalUpDownData(newStrategy, candleIntervalUpDownDataPrev);
                    annotation += " beginDownFirst: " + printDateTime(candleIntervalUpDownDataPrev.beginDownFirst.candle.getDateTime());
                    var newStopLossPriceByMax = Math.min(
                            candleIntervalUpDownData.minClose,
                            candleIntervalUpDownData.maxClose - Math.abs(candleIntervalUpDownData.minClose) * percent / 100f
                    );
                    var newStopLossPriceBottomA = newStopLossPriceByMax - Math.abs(candleIntervalUpDownData.minClose) * percent / 100f;
                    annotation += " newStopLossPriceByMax=" + printPrice(newStopLossPriceByMax);
                    if (candleIntervalUpDownDataPrevPrev.minClose == null) {
                        annotation += " something wrong: " + candleIntervalUpDownDataPrev.annotation;
                    } else if (
                            candleIntervalUpDownDataPrevPrev.maxClose < candleIntervalUpDownDataPrev.maxClose
                            && candleIntervalUpDownDataPrev.maxCandle.getClosingPrice().floatValue() > candleIntervalUpDownDataPrevPrev.maxClose
                            && stopLossPrice.doubleValue() < newStopLossPriceByMax
                    ) {
                        stopLossPrice = BigDecimal.valueOf(newStopLossPriceByMax);
                        stopLossPriceBottomA = stopLossPriceBottomA.max(BigDecimal.valueOf(newStopLossPriceBottomA));
                        annotation += "new stopLossPrice by MAX MAX=" + printPrice(stopLossPrice) + "-" + printPrice(stopLossPriceBottomA);
                    }
                }

                if (
                        candleIntervalUpDownData.maxClose > order.getPurchasePrice().floatValue()
                        && sellCriteria.getIsMaxPriceByFib()
                ) {
                    BigDecimal stopLossPriceByMaxInterval = order.getDetails().getCurrentPrices().getOrDefault("stopLossPriceByMaxInterval", BigDecimal.ZERO);
                    annotation += " stopLossPriceByMaxInterval=" + printPrice(stopLossPriceByMaxInterval);
                    if (
                            stopLossPriceByMaxInterval.equals(BigDecimal.ZERO)
                            || stopLossPriceByMaxInterval.add(errorD).compareTo(BigDecimal.valueOf(candleIntervalUpDownData.maxClose)) < 0
                    ) {
                        var minCur = Math.min(stopLossPrice.floatValue(), candleIntervalUpDownData.minClose);
                        var newStopLossPriceByMax = minCur + Math.abs(candleIntervalUpDownData.maxClose - minCur) * (1 - 0.382f);
                        var newStopLossPriceBottomA = minCur + Math.abs(candleIntervalUpDownData.maxClose - minCur) * 0.382f;
                        annotation += " newStopLossPriceBottomA=" + printPrice(newStopLossPriceBottomA);
                        if (
                                newStopLossPriceBottomA > order.getPurchasePrice().floatValue()
                                        && stopLossPrice.doubleValue() < newStopLossPriceByMax
                        ) {
                            stopLossPrice = BigDecimal.valueOf(newStopLossPriceByMax);
                            stopLossPriceBottomA = stopLossPriceBottomA.max(BigDecimal.valueOf(newStopLossPriceBottomA));
                            annotation += " new stopLossPrice by MAX Interval=" + printPrice(stopLossPrice) + "-" + printPrice(stopLossPriceBottomA);
                            order.getDetails().getCurrentPrices().put("stopLossPriceByMaxInterval", BigDecimal.valueOf(candleIntervalUpDownData.maxClose));
                        }
                    }
                }

                //limitPrice
                var maxDiffP = 100f * Math.abs((candleIntervalUpDownDataPrev.maxClose - candleIntervalUpDownData.maxClose) / (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose));
                var minDiffP = 100f * Math.abs((candleIntervalUpDownDataPrev.minClose - candleIntervalUpDownData.minClose) / (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose));
                annotation += " maxDiffP=" + printPrice(maxDiffP);
                annotation += " minDiffP=" + printPrice(minDiffP);
                if (
                        (candleIntervalUpDownData.maxClose > candleIntervalUpDownDataPrev.maxClose
                        && candleIntervalUpDownData.minClose < candleIntervalUpDownDataPrev.minClose)
                        || (maxDiffP < 5f && minDiffP < 5f)
                ) {
                    var size = candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose;
                    var sizePercent = size / Math.abs(candleIntervalUpDownData.minClose) * 100f;
                    annotation += " limitPrice sizePercent" + printPrice(sizePercent);
                    if (sizePercent < strategy.getBuyCriteria().getCandlePriceMinFactor()) {
                        annotation += " sizePercent=" + printPrice(sizePercent);
                        size = Math.abs(candleIntervalUpDownData.minClose) * strategy.getBuyCriteria().getCandlePriceMinFactor() / 100f;
                    }
                    var cList = candleHistoryService.getCandlesByFigiBetweenDateTimes(candle.getFigi(), candleIntervalUpDownData.endPost.candle.getDateTime(), candle.getDateTime(), strategy.getInterval());
                    var minClose = cList.stream().mapToDouble(v -> v.getClosingPrice().min(v.getOpenPrice()).doubleValue()).min().orElse(-1);

                    var minDiff = candleIntervalUpDownData.minClose - minClose;
                    var minDiffPercent = minDiff / size * 100f;

                    annotation += " minDiff=" + printPrice(minDiff);
                    annotation += " minDiffPercent=" + printPrice(minDiffPercent);
                    if (minDiffPercent > 25) {
                        limitPrice = BigDecimal.valueOf(candleIntervalUpDownData.maxClose);
                    }
                }
            } else if (!isDownWithLimits) {
                var newValue = candleIntervalUpDownData.minClose
                        - (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) * 2f;
                annotation += " newValue=" + printPrice(newValue);
                if (stopLossPrice.equals(BigDecimal.ZERO)) {
                    annotation += candleIntervalUpDownData.annotation;
                    stopLossPrice = BigDecimal.valueOf(newValue);
                    stopLossPriceBottomA = BigDecimal.valueOf(newValue - (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose));
                    annotation += " new stopLossPrice first interval=" + printPrice(stopLossPrice) + "-" + printPrice(stopLossPriceBottomA);
                }
            }

            var candlesList = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                    candle.getFigi(),
                    candleIntervalUpDownData.endPost.candle.getDateTime().isAfter(order.getPurchaseDateTime()) ? candleIntervalUpDownData.endPost.candle.getDateTime() : order.getPurchaseDateTime(),
                    candle.getDateTime(),
                    strategy.getInterval()
            );
            var maxPriceInInterval = candlesList.stream().mapToDouble(value -> value.getHighestPrice().doubleValue()).max().orElse(-1);
            var lossPresent = 0.33f;
            var stopLossMaxPrice = order.getDetails().getCurrentPrices().getOrDefault("stopLossMaxPrice", BigDecimal.ZERO);
            annotation += " stopLossMaxPrice=" + printPrice(stopLossMaxPrice);
            var minPrice = candleIntervalUpDownData.maxClose;
            if (
                    order.getPurchaseDateTime().isAfter(candleIntervalUpDownData.endPost.candle.getDateTime())
                    && order.getPurchasePrice().floatValue() > minPrice
            ) {
                minPrice = order.getPurchasePrice().floatValue() + Math.abs(candleIntervalUpDownData.minClose) * lossPresent / 100f;
                annotation += " up minPrice=" + printPrice(minPrice);
            }
            annotation += " minPrice=" + printPrice(minPrice);

            var stopLossMaxPriceDown = order.getDetails().getCurrentPrices().getOrDefault("stopLossMaxPriceDown", BigDecimal.ZERO);
            if (
                    stopLossPriceBeginDate.isAfter(candleIntervalUpDownData.endPost.candle.getDateTime())
                    && !stopLossMaxPriceDown.equals(BigDecimal.ZERO)
            ) {
                minPrice = stopLossMaxPriceDown.floatValue();
                annotation += " down minPrice=" + printPrice(minPrice);
            }

            //if (stopLossPrice.abs().compareTo(BigDecimal.valueOf(0.000001f)) > 0) {
            if (!stopLossPrice.equals(BigDecimal.ZERO)) {
                minPrice = Math.max(minPrice, stopLossPrice.floatValue());
                annotation += " minPrice2=" + printPrice(minPrice);
            }
            if (!stopLossMaxPrice.equals(BigDecimal.ZERO)) {
                minPrice = Math.max(minPrice, stopLossMaxPrice.floatValue());
                annotation += " minPrice3=" + printPrice(minPrice);
            }

            var percentCur = (100f * (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) / Math.abs(candleIntervalUpDownData.minClose));
            var percentPrev = percentCur;
            var percentPrevPrev = percentCur;
            if (candleIntervalUpDownDataPrev.minClose != null) {
                percentPrev = (100f * (candleIntervalUpDownDataPrev.maxClose - candleIntervalUpDownDataPrev.minClose) / Math.abs(candleIntervalUpDownDataPrev.minClose));
                var candleIntervalUpDownDataPrevPrev = getPrevCandleIntervalUpDownData(newStrategy, candleIntervalUpDownDataPrev);
                if (candleIntervalUpDownDataPrevPrev.minClose != null) {
                    percentPrevPrev = (100f * (candleIntervalUpDownDataPrevPrev.maxClose - candleIntervalUpDownDataPrevPrev.minClose) / Math.abs(candleIntervalUpDownDataPrevPrev.minClose));
                }
            }
            var percent = Math.max((5 * percentCur + 2 * percentPrev + percentPrevPrev) / 8f, percentCur);
            annotation += " percent=" + printPrice(percent) + " < " + printPrice(buyCriteria.getCandlePriceMinFactor());
            if (
                    true
                    //candleIntervalUpDownDataPrev.minClose < candleIntervalUpDownData.minClose
                    && percent < buyCriteria.getCandlePriceMinFactor()
            ) {
                percent = buyCriteria.getCandlePriceMinFactor();
            }

            BigDecimal maxPrice = BigDecimal.valueOf(minPrice + Math.abs(candleIntervalUpDownData.minClose) * percent / 100f);

            if (sellCriteria.getIsMaxPriceByFib()) {
                var minPriceF = candleIntervalUpDownData.minClose;
                var fibPercent = 1.618;
                var diffPrice = (Math.abs(candleIntervalUpDownData.minClose) * percent / 100f);

                annotation += " diffPrice=" + printPrice(diffPrice);

                maxPrice = BigDecimal.valueOf(minPriceF + diffPrice * fibPercent);

                annotation += " maxPriceF=" + printPrice(maxPrice);
                var stopLossMaxPriceF = stopLossMaxPrice;

                if (
                        order.getPurchaseDateTime().isAfter(candleIntervalUpDownData.endPost.candle.getDateTime())
                        && order.getPurchasePrice().floatValue() > candleIntervalUpDownData.maxClose
                ) {
                    var v = BigDecimal.valueOf(order.getPurchasePrice().floatValue() + diffPrice * 0.236f);
                    annotation += " v=" + printPrice(v);
                    if (stopLossMaxPrice.equals(BigDecimal.ZERO) || stopLossMaxPrice.compareTo(v) < 0) {
                        stopLossMaxPriceF = v;
                        annotation += " stopLossMaxPriceF=" + printPrice(stopLossMaxPriceF);
                    }
                }
                if (!stopLossMaxPriceF.equals(BigDecimal.ZERO)) {
                    while (maxPrice.subtract(errorD).compareTo(stopLossMaxPriceF) < 0) {
                        fibPercent = fibPercent * 1.618;
                        maxPrice = BigDecimal.valueOf(minPriceF + diffPrice * fibPercent);
                        annotation += " maxPriceF=" + printPrice(maxPrice);
                    }
                }
            }

            var isDownStopLoss = false;
            var isDownStopLossCur = false;
            BigDecimal stopLossPriceBottom = BigDecimal.valueOf(minPrice);
            annotation += " takeProfitPrice=" + printPrice(takeProfitPrice);
            annotation += " takeProfitPriceStart=" + printPrice(takeProfitPriceStart);
            annotation += " intervalPercentStep=" + printPrice(intervalPercentStep);
            var isIntervalUpResMaybe = calcIsIntervalUpMaybe(
                    candle,
                    null,
                    buyCriteria,
                    newStrategy,
                    candleIntervalUpDownData,
                    candleIntervalUpDownDataPrev
            );
            annotation += " resMaybe=" + isIntervalUpResMaybe.isIntervalUp + ": " + isIntervalUpResMaybe.annotation;
            setTrendUp(strategy, candle, isIntervalUpResMaybe.isIntervalUp);

            var isTrendSell = isTrendSellCalc(newStrategy, candle);
            isIntervalDown = isTrendSell.isIntervalDown;
            annotation += " isTrendSell=" + isTrendSell.isIntervalDown + ": " + isTrendSell.annotation;
            setTrendDown(strategy, candle, isTrendSell.isIntervalDown);
            if (
                    isTrendSell.isIntervalDown
                    && order.getPurchaseDateTime().isBefore(candleIntervalUpDownData.endPost.candle.getDateTime())
                    && isTrendSell.minDiffPercentPrev.compareTo(BigDecimal.valueOf(50)) > 0
                    && (stopLossPriceBottomA == null || stopLossPriceBottomA.equals(BigDecimal.ZERO) || stopLossPriceBottomA.compareTo(order.getPurchasePrice()) < 0)
            ) {
                limitPrice = BigDecimal.valueOf(candleIntervalUpDownData.maxClose);
                annotation += " limitPrice=" + printPrice(limitPrice);
            }
            if (isTrendSell.isIntervalDown) {
                setTrendUp(strategy, candle, false);
            }

            if (
                    true//!res
                    && takeProfitPrice != null
                    && takeProfitPrice.abs().compareTo(errorD) > 0
                    //&& stopLossMaxPrice.equals(BigDecimal.ZERO)
                    && (takeProfitPriceStart.abs().compareTo(errorD) < 0 || takeProfitPriceStart.compareTo(takeProfitPrice) > 0)
            ) {
                if (order.getPurchaseDateTime().isBefore(candleIntervalUpDownData.endPost.candle.getDateTime())) {
                    annotation += " stopLossMaxPriceDown=" + printPrice(stopLossMaxPriceDown);
                    takeProfitPriceStart = takeProfitPrice;
                    var minPriceStart = Math.min(order.getPurchasePrice().floatValue(), candleIntervalUpDownData.endPost.candle.getClosingPrice().floatValue());
                    if (candleIntervalUpDownData.endPost.candle.getDateTime().isBefore(order.getPurchaseDateTime())) {
                        minPriceStart = order.getPurchasePrice().floatValue();
                    }
                    intervalPercentStep = BigDecimal.valueOf(100f * (takeProfitPriceStart.floatValue() - minPriceStart) / Math.abs(minPriceStart));
                    if (
                            (!stopLossMaxPriceDown.equals(BigDecimal.ZERO)
                            && takeProfitPrice.compareTo(stopLossMaxPriceDown.add(errorD)) <= 0)
                            || takeProfitPrice.compareTo(stopLossPrice.add(errorD)) <= 0
                    ) {
                        //minPriceStart = stopLossMaxPrice.floatValue();
                        //takeProfitPriceStart = BigDecimal.valueOf(minPriceStart * (100f + intervalPercentStep.floatValue()) / 100f);
                    } else {
                        if (isIntervalUpResMaybe != null && isIntervalUpResMaybe.isIntervalUp) {
                            annotation += " isIntervalUpMaybe=true";
                        } else {
                            //order.getDetails().getBooleanDataMap().put("isTakeProfitPriceStopLoss", true);
                            annotation += " takeProfitPriceStart=" + printPrice(minPriceStart);
                            annotation += " intervalPercentStep=" + printPrice(intervalPercentStep);
                            minPrice = minPriceStart;
                            maxPrice = takeProfitPriceStart;
                            var intervalPercentStepBottom = Math.max(intervalPercentStep.floatValue(), buyCriteria.getCandlePriceMinFactor());
                            stopLossPriceBottom = maxPrice.subtract(BigDecimal.valueOf(Math.abs(minPrice) * intervalPercentStepBottom / 100.));
                            annotation += " stopLossPriceBottom=" + printPrice(stopLossPriceBottom);
                            var minPriceInInterval = candlesList.stream().mapToDouble(value -> value.getLowestPrice().doubleValue()).min().orElse(-1);
                            annotation += " minPriceInInterval=" + printPrice(minPriceInInterval);
                            if (minPriceInInterval < stopLossPriceBottom.floatValue()) {
                                stopLossPriceBottom = BigDecimal.valueOf(minPriceInInterval);
                                annotation += " new min stopLossPriceBottom=" + printPrice(stopLossPriceBottom);
                            }
                            isDownStopLossCur = true;
                        }
                    }
                }
                takeProfitPrice = BigDecimal.ZERO;
                annotation += " new takeProfitPrice=" + printPrice(takeProfitPrice);
            } else {
                //order.getDetails().getBooleanDataMap().put("isTakeProfitPriceStopLoss", false);
                if (!takeProfitPriceStart.equals(BigDecimal.ZERO)) {
                    annotation += " maxPriceOld=" + printPrice(maxPrice);
                    var takeProfitPriceStep = order.getDetails().getCurrentPrices().getOrDefault("takeProfitPriceStep", BigDecimal.ZERO);
                    if (takeProfitPriceStep.equals(BigDecimal.ZERO)) {
                        var minPriceNew = Math.max(order.getPurchasePrice().floatValue(), takeProfitPriceStart.floatValue());
                        annotation += " minPriceNew=" + printPrice(minPriceNew);
                        if (!stopLossMaxPrice.equals(BigDecimal.ZERO)) {
                            minPriceNew = Math.max(minPriceNew, stopLossMaxPrice.floatValue());
                        }
                        var maxPriceNew = BigDecimal.valueOf(minPriceNew + Math.abs(minPriceNew) * intervalPercentStep.floatValue() / 100f);
                        if (maxPriceNew.compareTo(maxPrice) < 0) {
                            minPrice = minPriceNew;
                            maxPrice = maxPriceNew;
                            lossPresent = intervalPercentStep.floatValue() * lossPresent;
                            annotation += " lossPresent=" + printPrice(lossPresent);
                            stopLossPriceBottom = BigDecimal.valueOf(minPrice);
                        }
                    } else {
                        annotation += " minPriceFF=" + printPrice(takeProfitPriceStart);
                        var minPriceF = takeProfitPriceStart.floatValue();
                        var diffPrice = takeProfitPriceStep.floatValue();
                        annotation += " diffPrice=" + printPrice(diffPrice);

                        var maxPriceF = BigDecimal.valueOf(minPriceF + diffPrice * 1.0);

                        annotation += " maxPriceF=" + printPrice(maxPriceF);
                        var stopLossMaxPriceF = stopLossMaxPrice;

                        if (
                                order.getPurchaseDateTime().isAfter(candleIntervalUpDownData.endPost.candle.getDateTime())
                                && order.getPurchasePrice().floatValue() >= maxPriceF.floatValue()
                        ) {
                            var v = BigDecimal.valueOf(order.getPurchasePrice().floatValue() + diffPrice * 0.236f);
                            annotation += " v=" + printPrice(v);
                            if (stopLossMaxPrice.equals(BigDecimal.ZERO) || stopLossMaxPrice.compareTo(v) < 0) {
                                stopLossMaxPriceF = v;
                                annotation += " stopLossMaxPriceF=" + printPrice(stopLossMaxPriceF);
                            }
                        }
                        var fibPercent = 1.618;
                        if (!stopLossMaxPriceF.equals(BigDecimal.ZERO)) {
                            while (maxPriceF.subtract(errorD).compareTo(stopLossMaxPriceF) < 0) {
                                maxPriceF = BigDecimal.valueOf(minPriceF + diffPrice * fibPercent);
                                annotation += " maxPriceF=" + printPrice(maxPriceF);
                                fibPercent = fibPercent * 1.618;
                            }
                        }
                        if (maxPriceF.compareTo(maxPrice) < 0) {
                            minPrice = minPriceF;
                            maxPrice = maxPriceF;
                            annotation += " newF";
                        }
                    }
                }
            }
            annotation += " maxPrice=" + printPrice(maxPrice);
            maxPriceProfitStep = maxPrice;
            var stopLossMaxPriceNew = BigDecimal.ZERO;
            annotation += " maxPriceInInterval=" + printPrice(maxPriceInInterval);
            if (maxPriceInInterval > maxPrice.doubleValue()) {
                var newStopLossPrice = BigDecimal.valueOf(maxPrice.doubleValue() - (maxPrice.doubleValue() - minPrice) * lossPresent);
                if (sellCriteria.getIsMaxPriceByFib()) {
                    minPrice = Math.min(candleIntervalUpDownData.minClose, minPrice);
                    //minPrice = Math.max(order.getPurchasePrice().floatValue(), minPrice);
                    minPrice = Math.max(stopLossPrice.floatValue(), minPrice);
                    lossPresent = 0.382f;
                    var lossPresentBottom = 0.618f;
                    newStopLossPrice = BigDecimal.valueOf(maxPrice.doubleValue() - Math.abs(maxPrice.doubleValue() - minPrice) * lossPresent);
                    stopLossPriceBottom = BigDecimal.valueOf(maxPrice.doubleValue() - Math.abs(maxPrice.doubleValue() - minPrice) * lossPresentBottom);
                }
                if (newStopLossPrice.compareTo(stopLossPrice) > 0) {
                    stopLossMaxPriceNew = maxPrice;
                    stopLossPrice = newStopLossPrice;
                    stopLossPriceBottomA = stopLossPriceBottomA.max(stopLossPriceBottom);
                    annotation += " new stopLossPrice BY MAX=" + printPrice(stopLossPrice) + "-" + printPrice(stopLossPriceBottomA);
                    isDownStopLoss = isDownStopLossCur;
                }
            }

            var stopLossPricePrev = order.getDetails().getCurrentPrices().getOrDefault("stopLossPricePrev", BigDecimal.ZERO);
            var stopLossPriceBottomPrev = order.getDetails().getCurrentPrices().getOrDefault("stopLossPriceBottomPrev", BigDecimal.ZERO);
            if (stopLossPricePrev.equals(BigDecimal.ZERO) && isDownStopLoss) {
                stopLossPricePrev = stopLossPriceOld;
                stopLossPriceBottomPrev = order.getDetails().getCurrentPrices().getOrDefault("stopLossPriceBottom", BigDecimal.ZERO);
            }
            annotation += " stopLossPricePrev=" + printPrice(stopLossPricePrev);
            if (
                    !stopLossPricePrev.equals(BigDecimal.ZERO) && !stopLossPricePrev.equals(stopLossPrice)
                    && candleIntervalUpDownDataPrev.minClose != null
            ) {
                if (isIntervalUpResMaybe != null && isIntervalUpResMaybe.isIntervalUp) {
                    stopLossPrice = stopLossPricePrev;
                    stopLossPriceBottomA = stopLossPriceBottomPrev;
                    stopLossMaxPriceNew = order.getDetails().getCurrentPrices().getOrDefault("maxPricePrev", BigDecimal.ZERO);
                    annotation += " new stopLossPrice BY PREV=" + printPrice(stopLossPrice) + "-" + printPrice(stopLossPriceBottomA);
                }
            }

            if (!stopLossPrice.equals(stopLossPriceOld)) {
                annotation += " new stopLossPrice: " + printPrice(stopLossPrice);
                if (isDownStopLoss && stopLossPriceOld.compareTo(stopLossPrice) < 0) {
                    if (stopLossMaxPriceDown.equals(BigDecimal.ZERO)) {
                        order.getDetails().getCurrentPrices().put("stopLossPricePrev", stopLossPriceOld);
                        order.getDetails().getCurrentPrices().put("stopLossPriceBottomPrev", order.getDetails().getCurrentPrices().getOrDefault("stopLossPriceBottom", BigDecimal.ZERO));
                        order.getDetails().getCurrentPrices().put("maxPricePrev", order.getDetails().getCurrentPrices().getOrDefault("stopLossMaxPrice", BigDecimal.ZERO));
                    }
                    order.getDetails().getCurrentPrices().put("stopLossMaxPriceDown", maxPrice);

                    //order.getDetails().getCurrentPrices().put("stopLossMaxPrice", BigDecimal.ZERO);
                } else {
                    order.getDetails().getCurrentPrices().put("stopLossPricePrev", BigDecimal.ZERO);
                    order.getDetails().getCurrentPrices().put("stopLossPriceBottomPrev", BigDecimal.ZERO);
                    order.getDetails().getCurrentPrices().put("maxPricePrev", BigDecimal.ZERO);
                    order.getDetails().getCurrentPrices().put("stopLossMaxPriceDown", BigDecimal.ZERO);

                    if (!stopLossMaxPriceNew.equals(BigDecimal.ZERO)) {
                        order.getDetails().getCurrentPrices().put("stopLossMaxPrice", stopLossMaxPriceNew);
                    }
                }
                stopLossPriceBeginDate = candle.getDateTime();
                order.getDetails().getDateTimes().put("stopLossPriceBeginDate", stopLossPriceBeginDate);
                order.getDetails().getCurrentPrices().put("stopLossPriceBottom", stopLossPriceBottomA);


                if (
                        order.getPurchasePrice().compareTo(stopLossPriceBottomA) > 0
                        && stopLossPrice.compareTo(stopLossPriceOld) > 0
                ) {
                    var intervalPercentNearDown = order.getDetails().getCurrentPrices().getOrDefault("intervalPercentNearDown", BigDecimal.ZERO);
                    var intervalPercentNearDownNew = order.getPurchasePrice().subtract(stopLossPriceBottomA).divide(stopLossPriceBottomA.abs(), 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100f));
                    annotation += " nearNew < near: " + printPrice(intervalPercentNearDownNew) + " < " + printPrice(intervalPercentNearDown);
                    if (
                            intervalPercentNearDown.equals(BigDecimal.ZERO)
                            || intervalPercentNearDownNew.compareTo(intervalPercentNearDown) > 0
                    ) {
                        order.getDetails().getCurrentPrices().put("intervalPercentNearDown", intervalPercentNearDownNew);
                    }
                }

                orderService.updateDetailsCurrentPrice(order, "stopLossPrice", stopLossPrice);
            }

            annotation += " stopLossPriceBeginDate=" + printDateTime(stopLossPriceBeginDate);
            annotation += " stopLossPriceBottomA=" + printPrice(stopLossPriceBottomA);
            if (
                    stopLossPriceBottomA != null
                    && !stopLossPriceBottomA.equals(BigDecimal.ZERO)
                    //&& stopLossPriceBeginDate.isAfter(order.getPurchaseDateTime())
                    //candle.getHighestPrice().compareTo(stopLossPrice) < 0
            ) {
                annotation += " stopLossPrice TRY";
                if (candle.getHighestPrice().compareTo(stopLossPriceBottomA) < 0) {
                    res = true;
                    annotation += " OK stopLossPriceBottom";
                } else if (
                        (candleIntervalSell && candle.getClosingPrice().compareTo(stopLossPrice) > 0)
                        || candle.getHighestPrice().compareTo(stopLossPrice) < 0
                ) {
                    var candlesPrevAllArray = candleHistoryService.getCandlesByFigiBetweenDateTimes(candle.getFigi(), stopLossPriceBeginDate, candle.getDateTime(), strategy.getInterval());
                    annotation += " size=" + candlesPrevAllArray.size();
                    var curLength = 0;
                    var isUnder = false;
                    for (var i = 0; i < candlesPrevAllArray.size(); i++) {
                        if (candlesPrevAllArray.get(i).getClosingPrice().max(candlesPrevAllArray.get(i).getOpenPrice()).compareTo(stopLossPrice) < 0) {
                            curLength++;
                        }
                        if (
                                candlesPrevAllArray.get(i).getHighestPrice().compareTo(stopLossPrice) > 0
                                && curLength > 2 * sellCriteria.getStopLossSoftLength()
                        ) {
                                isUnder = true;
                                break;
                        }
                    }
                    annotation += " curLength=" + curLength + " > " + sellCriteria.getStopLossSoftLength();
                    annotation += " isUnder=" + isUnder;
                    var middlePrice = stopLossPriceBottomA.add(stopLossPriceBottomA.subtract(stopLossPrice).abs().divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_DOWN));
                    var isUnderMiddle = candle.getClosingPrice().max(candle.getOpenPrice()).compareTo(middlePrice) < 0;
                    annotation += " middlePrice=" + printPrice(middlePrice);
                    annotation += " isUnderMiddle=" + isUnderMiddle;
                    if (
                            (curLength > sellCriteria.getStopLossSoftLength() && candleIntervalSell && !sellCriteria.getIsMaxPriceByFib())
                            || (isUnder && !sellCriteria.getIsMaxPriceByFib())
                            || (curLength > sellCriteria.getStopLossSoftLength() * 3 && isUnderMiddle)
                            || (curLength > sellCriteria.getStopLossSoftLength() * 6)
                    ) {
                        res = true;
                        annotation += " OK";
                    }
                    /*
                    var candlesPrevArray = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(), candle.getDateTime(), 10, strategy.getInterval());
                    var candlePrev = candlesPrevArray.get(0);
                    annotation += " stopLossPriceBeginDate=" + printDateTime(stopLossPriceBeginDate) + " < candlePrev=" + printDateTime(candlePrev.getDateTime());
                    if (
                            candlePrev.getHighestPrice().compareTo(stopLossPrice) < 0
                                    && candlePrev.getDateTime().isAfter(stopLossPriceBeginDate)
                    ) {
                        var maxPriceDown = candlesPrevArray.stream().mapToDouble(value -> value.getHighestPrice().doubleValue()).max().orElse(-1);
                        annotation += " maxPriceDown=" + printPrice(maxPriceDown);
                        if (null == candleBuyRes) {
                            candleBuyRes = getCandleBuyRes(newStrategy, candle);
                        }
                        if (
                                maxPriceDown < stopLossPrice.floatValue()
                                        && !candleBuyRes.isIntervalUp
                        ) {
                            res = true;
                            annotation += " OK";
                        }
                    }*/
                }
            } else if (candle.getHighestPrice().compareTo(stopLossPrice) < 0) {
                res = true;
                annotation += " stopLossPrice OK";
            }
        }

        if (
                res
                && candleIntervalUpDownData.minClose != null
                && candle.getClosingPrice().max(candle.getOpenPrice()).floatValue() > candleIntervalUpDownData.minClose
        ) {
            if (null == candleBuyRes) {
                candleBuyRes = getCandleBuyRes(newStrategy, candle);
            }
            if (
                    candleBuyRes.isIntervalUp
                    || isTrendBuy(newStrategy, candle)
            ) {
                res = false;
                annotation += " SKIP by UP";
            }
        }

        if (
                !res
                && takeProfitPrice != null
                && takeProfitPrice.abs().compareTo(errorD) > 0
                && takeProfitPrice.compareTo(candle.getClosingPrice()) < 0
                && isIntervalDown
                && order.getPurchaseDateTime().isBefore(candleIntervalUpDownData.endPost.candle.getDateTime())
        ) {
            res = true;
            annotation += " takeProfitPrice OK";
        }

        if (limitPrice != null) {
            annotation += " limitPrice=" + printPrice(limitPrice);
            var sellLimitCriteria = strategy.getSellLimitCriteria(candle.getFigi());
            sellLimitCriteria.setExitProfitPercent((float) ((100.f * limitPrice.floatValue() / purchaseRate.floatValue())
                    - (limitPrice.floatValue()/Math.abs(limitPrice.floatValue())) *  100.));
            strategy.setSellLimitCriteria(candle.getFigi(), sellLimitCriteria);
            annotation += " sell limit=" + strategy.getSellLimitCriteria(candle.getFigi()).getExitProfitPercent();
        }
        if (strategy.getSellLimitCriteria() != null) {
            var limitPriceByStrategy = purchaseRate.add(purchaseRate.abs().multiply(
                    BigDecimal.valueOf(strategy.getSellLimitCriteria(candle.getFigi()).getExitProfitPercent() / 100.)
            ));
            annotation += " limit=" + strategy.getSellLimitCriteria(candle.getFigi()).getExitProfitPercent();
            if (limitPrice == null) {
                limitPrice = limitPriceByStrategy;
            } else {
                limitPrice = limitPriceByStrategy.min(limitPrice);
            }
        }
        if (
            limitPrice != null
            && candle.getClosingPrice().compareTo(limitPrice) > 0
        ) {
            annotation += " limit OK";
            res = true;
        }
        var candleIntervalMinPercent = buyCriteria.getCandleIntervalMinPercent();
        if (null != sellCriteria.getProfitPercentFromSellMinPrice()) {
            candleIntervalMinPercent = sellCriteria.getProfitPercentFromSellMinPrice();
        }
        var profitPercentFromBuyMinPrice = sellCriteria.getCandleIntervalMinPercent();
        if (null != buyCriteria.getProfitPercentFromBuyMinPrice()) {
            profitPercentFromBuyMinPrice = (float) -buyCriteria.getProfitPercentFromBuyMinPrice();
        }

        annotation = " res=" + res + " open = " + printPrice(candle.getOpenPrice()) + " close=" + printPrice(candle.getClosingPrice()) + " " + annotation;

        notificationService.reportStrategyExt(
                res,
                strategy,
                candle,
                "Date|open|high|low|close|ema2|profit|loss|limitPrice|lossAvg|deadLineTop|investBottom|investTop|smaTube|strategy|average|averageBottom" +
                        "|averageTop|candleBuySell|maxClose|minClose|priceBegin|priceEnd|ema|emaPrev|MinFactor|MaxFactor|underPrice|takeProfit|stopLossPrice|borderClose|takeProfitPriceStart" +
                        "|stopLossPriceBottom|maxPriceProfitStep|trendUp|trendDown|buy|sell",
                "{} | {} | {} | {} | {} | | {} | {} | {} |  |  |  |  |  |sell {}||||{}|{}|{}|{}|{}|{}|{}|{}|{}||{}|{}|{}|{}|{}|{}|{}|{}||{}",
                printDateTime(candle.getDateTime()),
                candle.getOpenPrice(),
                candle.getHighestPrice(),
                candle.getLowestPrice(),
                candle.getClosingPrice(),
                factorial.getProfit(),
                factorial.getLoss(),
                limitPrice,
                annotation,
                candleIntervalSell ? candle.getClosingPrice().multiply(BigDecimal.valueOf(1. + candleIntervalMinPercent / 100))
                        : (candleIntervalBuy ? candle.getClosingPrice().subtract(candle.getClosingPrice().abs().multiply(BigDecimal.valueOf(profitPercentFromBuyMinPrice / 100))) : ""),
                candleIntervalUpDownData.maxClose,
                candleIntervalUpDownData.minClose,
                candleIntervalUpDownData.priceBegin,
                candleIntervalUpDownData.priceEnd,
                ema == null ? "" : ema.get(ema.size() - 1),
                emaPrev == null ? "" : emaPrev.get(emaPrev.size() - 1),
                candleBuyRes == null || candleBuyRes.candlePriceMinFactor == null ? "" : candleIntervalUpDownData.maxClose - (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) * candleBuyRes.candlePriceMinFactor,
                candleBuyRes == null || candleBuyRes.candlePriceMaxFactor == null ? "" : candleIntervalUpDownData.maxClose - (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) * candleBuyRes.candlePriceMaxFactor,
                takeProfitPriceOrig == null || takeProfitPriceOrig.abs().compareTo(errorD) < 0 ? "" : takeProfitPriceOrig,
                stopLossPrice == null || stopLossPrice.equals(BigDecimal.ZERO) ? "" : stopLossPrice,
                candleIntervalUpDownData.endPost == null ? ""
                        : (candleIntervalUpDownData.endPost.candle.getDateTime().equals(candle.getDateTime()) ? candleIntervalUpDownData.minClose : candleIntervalUpDownData.maxClose),
                takeProfitPriceStart.equals(BigDecimal.ZERO) ? "" : takeProfitPriceStart,
                stopLossPriceBottomA == null || stopLossPriceBottomA.equals(BigDecimal.ZERO) ? "" : stopLossPriceBottomA,
                maxPriceProfitStep == null || maxPriceProfitStep.equals(BigDecimal.ZERO) ? "" : maxPriceProfitStep,
                getTrendUp(strategy, candle) ? candle.getClosingPrice().add(candle.getClosingPrice().abs().multiply(BigDecimal.valueOf(2 * profitPercentFromBuyMinPrice / 100))) : "",
                getTrendDown(strategy, candle) ? candle.getClosingPrice().subtract(candle.getClosingPrice().abs().multiply(BigDecimal.valueOf(2 * profitPercentFromBuyMinPrice / 100))) : "",
                res ? candle.getClosingPrice().subtract(candle.getClosingPrice().abs().multiply(BigDecimal.valueOf(4 * profitPercentFromBuyMinPrice / 100))) : ""
        );
        return res;
    }

    @Override
    public AStrategy.Type getStrategyType() {
        return AStrategy.Type.instrumentFactorialByFiat;
    }

    private FactorialData findBestFactorialInPast(AInstrumentByFiatFactorialStrategy strategy, CandleDomainEntity candle) {
        var curDateTime = candle.getDateTime();
        if (strategy.getFactorialInterval().equals("1hour")) {
            curDateTime = curDateTime.plusHours(1);
        }
        var candleListCash = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                curDateTime, 1, strategy.getFactorialInterval());
        String key = strategy.getExtName() + candle.getFigi() + candleListCash.get(0).getDateTime();
        var ret = getCashedValue(key);
        if (ret != null) {
            return ret;
        }
        if (strategy.isFactorialSimple()) {
            ret = findBestFactorialInPastSimple(strategy, candle, curDateTime);
        } else {
            ret = findBestFactorialInPastOrig(strategy, candle, curDateTime);
        }
        if (ret == null) {
            return null;
        }
        ret.setDateTime(candleListCash.get(0).getDateTime());
        addCashedValue(key, ret);
        return ret;
    }

    private FactorialData findBestFactorialInPastSimple(AInstrumentByFiatFactorialStrategy strategy, CandleDomainEntity candle, OffsetDateTime curDateTime) {
        var pastDateTime = curDateTime;
        if (strategy.getFactorialInterval().equals("1hour")) {
            pastDateTime = pastDateTime.plusHours(1);
        }
        List<Double> expectProfitList = new ArrayList<>();
        List<Double> expectLossList = new ArrayList<>();
        String bestInfo = " bestInfo:" + printDateTime(candle.getDateTime());
        for (var i = 0; i < strategy.getFactorialSimpleLength(); i++) {
            pastDateTime = pastDateTime.minusDays(7);
            bestInfo += "i=" + i + " pastDateTime=" + printDateTime(pastDateTime);
            var size = 5;
            var list = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                    pastDateTime, 1 + size, strategy.getFactorialInterval());
            if (list == null) {
                break;
            }
            double expectProfitSum = 0;
            double expectLossSum = 0;
            var k = 1;
            var kPrev = 1;
            var sizeK = 0;
            for(var j = 0; j < size; j++) {
                var candlePrev = list.get((size - 1) - j);
                var candleCur = list.get((size - 1) - j + 1);
                bestInfo += " candleCur=" + printDateTime(candleCur.getDateTime()) + ": " + printPrice(candleCur.getLowestPrice()) + "-" + printPrice(candleCur.getHighestPrice());
                bestInfo += " candlePrev=" + printDateTime(candlePrev.getDateTime()) + ": " + printPrice(candlePrev.getClosingPrice());
                var expectProfit = 100f * (candleCur.getHighestPrice().doubleValue() - candlePrev.getClosingPrice().doubleValue()) / Math.abs(candleCur.getHighestPrice().doubleValue());
                var expectLoss = 100f * (candlePrev.getClosingPrice().doubleValue() - candleCur.getLowestPrice().doubleValue()) / Math.abs(candleCur.getLowestPrice().doubleValue());
                bestInfo += " expectProfit=" + printPrice(expectProfit);
                bestInfo += " expectLoss=" + printPrice(expectLoss);
                expectProfitSum += expectProfit * k;
                expectLossSum += expectLoss * k;
                sizeK += 1 * k;
                var kSave = k;
                k = k + kPrev;
                kPrev = kSave;
            }
            expectProfitList.add(expectProfitSum / sizeK);
            expectLossList.add(expectLossSum / sizeK);
        }

        var expectProfit = expectProfitList.stream().mapToDouble(i -> i).average().orElse(-1);
        var expectLoss = expectLossList.stream().mapToDouble(i -> i).average().orElse(-1);


        var res = FactorialData.builder()
                /*.size(bestSize)
                .length(strategy.getFactorialLength())
                .diffPrice(bestDiff)
                .candleList(candleList.subList(startCandleI, startCandleI + strategy.getFactorialLength() * bestSize))
                .candleListFeature(candleList.subList(startCandleI + strategy.getFactorialLength() * bestSize, startCandleI + strategy.getFactorialLength() * bestSize + strategy.getFactorialLengthFuture() * bestSize))
                .candleListPast(candleList.subList(candleList.size() - strategy.getFactorialLength(), candleList.size()))*/
                .info(bestInfo)
                .expectProfit((float) expectProfit)
                .expectLoss((float) expectLoss)
                .profit(candle.getClosingPrice().doubleValue() * (1f + expectProfit / 100f))
                .loss(candle.getClosingPrice().doubleValue() * (1f - expectLoss / 100f))
                .build();
        return res;
    }

    private FactorialData findBestFactorialInPastOrig(AInstrumentByFiatFactorialStrategy strategy, CandleDomainEntity candle, OffsetDateTime curDateTime) {
        var candleList = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                curDateTime, strategy.getFactorialHistoryLength(), strategy.getFactorialInterval());
        if (null == candleList) {
            return null;
        }
        Integer startCandleI = 0;
        Integer bestSize = 1;
        Float bestDiff = null;
        String bestInfo = "";
        List<FactorialData> factorialDataList = new ArrayList<>();
        for (var iSize = 0; iSize < strategy.getFactorialSizes().size(); iSize++) {
            Integer size = strategy.getFactorialSizes().get(iSize);
            CandleDomainEntity modelStartCandle = candleList.get(candleList.size() - strategy.getFactorialLength());
            for (var i = 0; i < candleList.size() - Math.max(strategy.getFactorialLength(), strategy.getFactorialLengthFuture()) * 2 * size; i++) {
                CandleDomainEntity testStartCandle = candleList.get(i);
                if (size > 1) {
                    testStartCandle = testStartCandle.clone();
                    testStartCandle.setVolume(testStartCandle.getVolume() / size);
                    for (var jSize = 1; jSize < size; jSize++) {
                        testStartCandle.setHighestPrice(testStartCandle.getHighestPrice().max(candleList.get(i + jSize).getHighestPrice()));
                        testStartCandle.setLowestPrice(testStartCandle.getLowestPrice().min(candleList.get(i + jSize).getLowestPrice()));
                        testStartCandle.setClosingPrice(candleList.get(i + jSize).getClosingPrice());
                        testStartCandle.setVolume(testStartCandle.getVolume() +  candleList.get(i + jSize).getVolume() / size);
                    }
                }
                Float diff = 0f;
                Float diffCandle = 0f;
                Float diffCandleMax = 0f;
                Float diffClose = 0f;
                Float diffOpen = 0f;
                Float diffValue = 0f;
                Float diffTime = 0f;
                String info = "" + testStartCandle.getDateTime() + " price=" + testStartCandle.getClosingPrice() + " mPrice=" + modelStartCandle.getClosingPrice();
                var testCandlePrev = testStartCandle;
                var modelCandlePrev = modelStartCandle;
                for (var j = 1; j < strategy.getFactorialLength(); j++) {
                    if ((i + j * size) >= candleList.size()) {
                        log.info("i + j * size = {} + {} * {} = {}, {}", i, j, size, i + j * size, candleList.size());
                    }
                    CandleDomainEntity modelCandle = candleList.get(candleList.size() - strategy.getFactorialLength() + j);
                    CandleDomainEntity testCandle = candleList.get(i + j * size);
                    if (size > 1) {
                        testCandle = testCandle.clone();
                        testCandle.setVolume(testCandle.getVolume() / size);
                        for (var jSize = 1; jSize < size; jSize++) {
                            testCandle.setHighestPrice(testCandle.getHighestPrice().max(candleList.get(i + j * size + jSize).getHighestPrice()));
                            testCandle.setLowestPrice(testCandle.getLowestPrice().min(candleList.get(i + j * size + jSize).getLowestPrice()));
                            testCandle.setClosingPrice(candleList.get(i + j * size + jSize).getClosingPrice());
                            testCandle.setVolume(testCandle.getVolume() +  candleList.get(i + j * size + jSize).getVolume() / size);
                        }
                    }
                    Float curDiff = 0f;
                    Float curDiffValue = 0f;
                    diffCandleMax +=
                            (float)Math.pow(
                                    Math.abs(((modelCandle.getHighestPrice().floatValue() - modelCandle.getLowestPrice().floatValue())
                                    / modelCandle.getLowestPrice().floatValue()
                                    //- (modelCandlePrev.getHighestPrice().floatValue() - modelCandlePrev.getLowestPrice().floatValue())
                            )
                            - ((testCandle.getHighestPrice().floatValue() - testCandle.getLowestPrice().floatValue())
                                    / testCandle.getLowestPrice().floatValue()
                                    //- (testCandlePrev.getHighestPrice().floatValue() - testCandlePrev.getLowestPrice().floatValue())
                            ))
                                    , 2)
                    ;
                    diffClose +=
                            (float)Math.pow(
                                    Math.abs(((modelCandle.getClosingPrice().floatValue() - modelCandlePrev.getClosingPrice().floatValue())/modelCandle.getClosingPrice().floatValue())
                            - (testCandle.getClosingPrice().floatValue() - testCandlePrev.getClosingPrice().floatValue())/testCandle.getClosingPrice().floatValue())
                            , 2)
                    ;
                    diffOpen +=
                            (float)Math.pow(
                                    Math.abs(((modelCandle.getOpenPrice().floatValue() - modelCandlePrev.getClosingPrice().floatValue())/modelCandle.getOpenPrice().floatValue())
                                    - (testCandle.getOpenPrice().floatValue() - testCandlePrev.getClosingPrice().floatValue())/testCandle.getOpenPrice().floatValue())
                            , 2)
                    ;

                    diffCandle +=
                            (float)Math.pow(
                                    Math.abs(((modelCandle.getOpenPrice().floatValue() - modelCandle.getClosingPrice().floatValue())/modelCandle.getOpenPrice().floatValue())
                                    - (testCandle.getOpenPrice().floatValue() - testCandle.getClosingPrice().floatValue())/testCandle.getOpenPrice().floatValue())
                            , 2)
                    ;
                    //curDiff +=
                    //        Math.abs(((modelCandle.getHighestPrice().floatValue() - modelCandle.getLowestPrice().floatValue())/modelCandle.getHighestPrice().floatValue())
                    //                - (testCandle.getHighestPrice().floatValue() - testCandle.getLowestPrice().floatValue())/testCandle.getHighestPrice().floatValue());
                    //curDiffValue += Math.abs(modelCandle.getVolume() - testCandle.getVolume());
                    curDiffValue += (float)Math.pow(Math.abs(modelCandle.getVolume()/(modelCandlePrev.getVolume() + 1) - testCandle.getVolume()/(testCandlePrev.getVolume() + 1))
                    , 2);
                    curDiff = curDiff * curDiff;
                    curDiffValue = curDiffValue;// * curDiffValue;
                    if (strategy.getFactorialRatioI() > 0) {
                        curDiff *= (strategy.getFactorialLength() + j * strategy.getFactorialRatioI())
                                / ((strategy.getFactorialRatioI() + 1) * strategy.getFactorialLength());
                        curDiffValue *= (strategy.getFactorialLength() + j * strategy.getFactorialRatioI())
                                / ((strategy.getFactorialRatioI() + 1) * strategy.getFactorialLength());
                    }
                    diff += curDiff;
                    diffValue += curDiffValue;
                    if (false
                            || j == 1
                            || j == (strategy.getFactorialLength() - 1)
                    ) {
                        var modelCandleDate = modelCandle.getDateTime().atZoneSimilarLocal(ZoneId.systemDefault());
                        var testCandleDate = testCandle.getDateTime().atZoneSimilarLocal(ZoneId.systemDefault());
                        diffTime += (float)Math.pow(
                                Math.abs((modelCandleDate.getHour() * 60 + modelCandleDate.getSecond())
                                - (testCandleDate.getHour() * 60 + testCandleDate.getSecond()))
                                , 2)
                        ;
                    }
                    if (j == 1 || j == strategy.getFactorialLength() - 1) {
                        info += " + " + curDiff + "(" + testCandle.getDateTime() + " with " + modelCandle.getDateTime() + ")";
                    }
                    testCandlePrev = testCandle;
                    modelCandlePrev = modelCandle;
                }
                factorialDataList.add(FactorialData.builder()
                        .i(i)
                        .size(size)
                        .length(strategy.getFactorialLength())
                        .diffPrice(diffClose)
                        .diffPriceOpen(diffOpen)
                        .diffPriceCandle(diffCandle)
                        .diffPriceCandleMax(diffCandleMax)
                        .diffValue(diffValue)
                        .diffTime(diffTime)
                        .candleList(candleList.subList(i, i + 1))
                        .candleListFeature(candleList.subList(i, i + 1))
                        .candleListPast(candleList.subList(i, i + 1))
                        .info(info)
                        .build());
            }
        }
        Float maxDiff = (float) factorialDataList.stream().mapToDouble(value -> value.getDiffPrice().doubleValue()).max().orElse(-1);
        Float maxDiffOpen = (float) factorialDataList.stream().mapToDouble(value -> value.getDiffPriceOpen().doubleValue()).max().orElse(-1);
        Float maxDiffCandle = (float) factorialDataList.stream().mapToDouble(value -> value.getDiffPriceCandle().doubleValue()).max().orElse(-1);
        Float maxDiffCandleMax = (float) factorialDataList.stream().mapToDouble(value -> value.getDiffPriceCandleMax().doubleValue()).max().orElse(-1);
        Float maxDiffValue = (float) factorialDataList.stream().mapToDouble(value -> value.getDiffValue().doubleValue()).max().orElse(-1);
        Float maxDiffTime = (float) factorialDataList.stream().mapToDouble(value -> value.getDiffTime().doubleValue()).max().orElse(-1);
        factorialDataList.forEach(v -> v.setDiff(
                (1f - strategy.getFactorialRatioOpen() - strategy.getFactorialRatioCandle() - strategy.getFactorialRatioCandleMax()
                        - strategy.getFactorialRatioValue() - strategy.getFactorialRatioTime()) * v.getDiffPrice()/maxDiff
                        + strategy.getFactorialRatioOpen() * v.getDiffPriceOpen()/maxDiffOpen
                        + strategy.getFactorialRatioCandle() * v.getDiffPriceCandle()/maxDiffCandle
                        + strategy.getFactorialRatioCandleMax() * v.getDiffPriceCandle()/maxDiffCandleMax
                        + strategy.getFactorialRatioValue() * v.getDiffValue()/maxDiffValue
                        + strategy.getFactorialRatioTime() * v.getDiffTime()/maxDiffTime
        ));
        Collections.sort(factorialDataList, (o1, o2) -> o1.getDiff() - o2.getDiff() > 0 ? 1 : (o1.getDiff() - o2.getDiff() < 0 ? -1: 0));
        var iBest = 0;

        startCandleI = factorialDataList.get(iBest).getI();
        bestSize = factorialDataList.get(iBest).getSize();
        bestDiff = factorialDataList.get(iBest).getDiffPrice();
        bestInfo = factorialDataList.get(iBest).getInfo();

        List<Double> expectProfitList = new ArrayList<>();
        List<Double> expectLossList = new ArrayList<>();

        var avSize = strategy.getFactorialBestSize();
        for(var i = 0; i < avSize; i++) {
            var candleI = factorialDataList.get(i).getI();
            var candleListFactorial = candleList.subList(candleI, candleI + strategy.getFactorialLength() * bestSize);
            var candleListFeature = candleList.subList(candleI + strategy.getFactorialLength() * bestSize, candleI + + strategy.getFactorialLength() * bestSize + strategy.getFactorialLengthFuture() * bestSize);

            Double maxPrice = (candleListFeature.stream().mapToDouble(value -> value.getHighestPrice().doubleValue()).max().orElse(-1));
            Double minPrice = candleListFeature.stream().mapToDouble(value -> value.getLowestPrice().doubleValue()).min().orElse(-1);
            var expectProfit = 100f * (maxPrice - candleListFactorial.get(candleListFactorial.size() - 1).getClosingPrice().doubleValue()) / Math.abs(maxPrice);
            var expectLoss = 100f * (candleListFactorial.get(candleListFactorial.size() - 1).getClosingPrice().doubleValue() - minPrice) / Math.abs(minPrice);
            expectProfitList.add(expectProfit);
            expectLossList.add(expectLoss);
            bestInfo += " expectLoss = " + candleListFactorial.get(candleListFactorial.size() - 1).getDateTime() + ":" + candleListFactorial.get(candleListFactorial.size() - 1).getClosingPrice().doubleValue() + "-"
                    +  minPrice + "=" + expectLoss;
            bestInfo += " expectProfit = " + printPrice(maxPrice) + "-" + printPrice(candleListFactorial.get(candleListFactorial.size() - 1).getClosingPrice())
                    + "=" + expectProfit;
        }

        var expectProfit = expectProfitList.stream().mapToDouble(i -> i).average().orElse(-1);
        var expectLoss = expectLossList.stream().mapToDouble(i -> i).average().orElse(-1);
        if (strategy.isFactorialAvgMaxMin()) {
            expectProfit = expectProfitList.stream().mapToDouble(i -> i).max().orElse(-1);
            expectLoss = expectLossList.stream().mapToDouble(i -> i).max().orElse(-1);
        }

        //expectProfit -= expectProfitList.stream().mapToDouble(i -> i).max().orElse(-1) / avSize;
        //expectLoss -= expectLossList.stream().mapToDouble(i -> i).max().orElse(-1) / avSize;

        //expectProfit = Math.abs(expectProfit);
        //expectLoss = Math.abs(expectLoss);

        bestInfo += " diffAverage=" + (factorialDataList.stream().mapToDouble(value -> value.getDiffPrice().doubleValue()).average().orElse(-1));

        bestInfo += " Select from " + candleList.get(0).getDateTime() + ", candleList.size=" + candleList.size();
        bestInfo += " getFactorialHistoryLength=" + strategy.getFactorialHistoryLength();
        var res = FactorialData.builder()
                .size(bestSize)
                .length(strategy.getFactorialLength())
                .diffPrice(bestDiff)
                .candleList(candleList.subList(startCandleI, startCandleI + strategy.getFactorialLength() * bestSize))
                .candleListFeature(candleList.subList(startCandleI + strategy.getFactorialLength() * bestSize, startCandleI + strategy.getFactorialLength() * bestSize + strategy.getFactorialLengthFuture() * bestSize))
                .candleListPast(candleList.subList(candleList.size() - strategy.getFactorialLength(), candleList.size()))
                .info(bestInfo)
                .expectProfit((float) expectProfit)
                .expectLoss((float) expectLoss)
                .profit(candle.getHighestPrice().doubleValue() * (1f + expectProfit / 100f))
                .loss(candle.getLowestPrice().doubleValue() * (1f - expectLoss / 100f))
                .build();
        return res;
    }

    @Builder
    @Data
    public static class CandleIntervalResultData {
        Boolean res;
        String annotation;
        CandleDomainEntity candle;
        Boolean isDown;
    }

    @Builder
    @Data
    public static class CandleIntervalUpDownData {
        CandleIntervalResultData beginDownFirst;
        CandleIntervalResultData beginPre;
        CandleIntervalResultData begin;
        CandleIntervalResultData end;
        CandleIntervalResultData endPost;
        Float maxClose;
        CandleDomainEntity maxCandle;
        CandleDomainEntity minCandle;
        Float minClose;
        Float priceBegin;
        Float priceEnd;
        Boolean isDown;
        String annotation;
    }
    private CandleIntervalResultData checkCandleInterval(CandleDomainEntity candle, AInstrumentByFiatFactorialStrategy.CandleIntervalInterface buyCriteria) {
        var retNull = CandleIntervalResultData.builder()
                .res(false)
                .annotation("")
                .candle(candle)
                .build();
        if (buyCriteria.getCandleIntervalMinPercent() == null) {
            return retNull;
        }
        var res = false;
        var annotation = "";
        var candleIPrev = candleHistoryService.getCandlesByFigiByLength(
                candle.getFigi(),
                candle.getDateTime(),
                buyCriteria.getCandleMaxInterval(),
                buyCriteria.getCandleInterval()
        );
        if (null == candleIPrev) {
            return retNull;
        }
        Collections.reverse(candleIPrev);
        var isOk = true;
        var iBegin = 0;
        if (candleIPrev.get(0).getDateTime().isEqual(candle.getDateTime())) {
            iBegin = 1;
        }
        var i = iBegin;
        var iEndDown = i;
        var candleUpLength = 0;
        var isDown = true;
        for (; i < candleIPrev.size(); i++) {
            if (buyCriteria.isCandleIntervalReverseDirection(candleIPrev.get(i).getOpenPrice(), candleIPrev.get(i).getClosingPrice())) {
                annotation += " i=" + i + " not target " + printPrice(candleIPrev.get(i).getOpenPrice()) + " - " + printPrice(candleIPrev.get(i).getClosingPrice())
                        + "(" + printDateTime(candleIPrev.get(i).getDateTime()) +")";
                if (candleUpLength < buyCriteria.getCandleUpLength()) {
                    annotation += " CandleUpLength false: " + candleUpLength + " < " + buyCriteria.getCandleUpLength();
                    isOk = false;
                }
                if (candleIPrev.get(i).getOpenPrice().compareTo(candleIPrev.get(i).getClosingPrice()) <= 0) {
                    isDown = false;
                }
                break;
            }
            candleUpLength++;
        }

        var iUpMiddle = -1;
        var iUpMiddleBegin = -1;
        var iUpMiddleEnd = -1;
        var upMiddleUpLength = 0;
        var iUpMiddleLength = 0;
        var iBeginDown = i;
        var iDownMiddleBegin = -1;
        var curDownLength = 0;
        for (; isOk && i < candleIPrev.size(); i++) {
            var priceForTargetDirection = candleIPrev.get(i).getOpenPrice().max(candleIPrev.get(i).getClosingPrice());
            if (!buyCriteria.isCandleIntervalTargetDirection(candleIPrev.get(i).getOpenPrice(), candleIPrev.get(i).getClosingPrice())) {
                priceForTargetDirection = candleIPrev.get(i).getOpenPrice().min(candleIPrev.get(i).getClosingPrice());
            }
            var priceForReverseDirection = candleIPrev.get(i).getOpenPrice().max(candleIPrev.get(i).getClosingPrice());
            if (!buyCriteria.isCandleIntervalReverseDirection(candleIPrev.get(i).getOpenPrice(), candleIPrev.get(i).getClosingPrice())) {
                priceForReverseDirection = candleIPrev.get(i).getOpenPrice().min(candleIPrev.get(i).getClosingPrice());
            }

            if (iDownMiddleBegin == -1 &&
                    (buyCriteria.isCandleIntervalTargetDirection(candleIPrev.get(i).getOpenPrice(), candleIPrev.get(i).getClosingPrice())
                    || (iUpMiddleBegin != -1
                    && buyCriteria.isCandleIntervalTargetDirection(priceForTargetDirection, candleIPrev.get(iUpMiddleBegin).getClosingPrice()))
            )) {
                if (iUpMiddle == -1) {
                    iUpMiddle = i;
                    iUpMiddleBegin = i;
                } else if (iUpMiddle == (i - 1)) {
                    iUpMiddle++;
                } else {
                    break;
                }
                if (buyCriteria.isCandleIntervalTargetDirection(candleIPrev.get(i).getOpenPrice(), candleIPrev.get(i).getClosingPrice())
                        && (iUpMiddleEnd == -1 || buyCriteria.isCandleIntervalTargetDirection(candleIPrev.get(i).getOpenPrice(), candleIPrev.get(iUpMiddleBegin).getClosingPrice())
                )) {
                    iUpMiddleEnd = i;
                }
                if (buyCriteria.isCandleIntervalTargetDirection(candleIPrev.get(i).getOpenPrice(), candleIPrev.get(i).getClosingPrice())) {
                    upMiddleUpLength++;
                } else {
                    iUpMiddleLength = iUpMiddleEnd - iUpMiddleBegin + 1;
                    if ((iUpMiddleLength - upMiddleUpLength) > upMiddleUpLength) {
                        iDownMiddleBegin = i;
                    }
                }
            } else if (buyCriteria.isCandleIntervalReverseDirection(candleIPrev.get(i).getOpenPrice(), candleIPrev.get(i).getClosingPrice())
                    || (iDownMiddleBegin != -1
                    && buyCriteria.isCandleIntervalReverseDirection(priceForReverseDirection, candleIPrev.get(iDownMiddleBegin).getClosingPrice()))
            ) {
                if (iUpMiddleEnd != -1 && iDownMiddleBegin == -1) {
                    iDownMiddleBegin = iUpMiddleEnd + 1;
                    iUpMiddleLength = iUpMiddleEnd - iUpMiddleBegin + 1;
                }
                if (buyCriteria.isCandleIntervalReverseDirection(candleIPrev.get(i).getOpenPrice(), candleIPrev.get(i).getClosingPrice())
                        && (iBeginDown == -1
                        || buyCriteria.isCandleIntervalReverseDirection(candleIPrev.get(i).getOpenPrice(), candleIPrev.get(iBeginDown).getOpenPrice())
                )) {
                    iBeginDown = i;
                }
                if (buyCriteria.isCandleIntervalReverseDirection(candleIPrev.get(i).getOpenPrice(), candleIPrev.get(i).getClosingPrice())) {
                    curDownLength = 0;
                } else {
                    curDownLength++;
                }
                //if (curDownLength > 0 && iUpMiddleLength > 0 && curDownLength >= iUpMiddleLength) {
                //    break;
                //}
            } else if (iUpMiddleEnd != -1 && iDownMiddleBegin != -1) {
                break;
            }
        }
        if (iDownMiddleBegin != iBeginDown) {
            annotation += " candleUpLength=" + candleUpLength;
            annotation += " iUpMiddleBegin=" + iUpMiddleBegin;
            annotation += " iUpMiddleEnd=" + iUpMiddleEnd;
            annotation += " iDownMiddleBegin=" + iDownMiddleBegin;
            annotation += " iBeginDown=" + iBeginDown;
        }
        iUpMiddleLength = iUpMiddleEnd - iUpMiddleBegin + 1;

        if (isOk && iUpMiddleLength/candleUpLength > buyCriteria.getCandleMaxLength()/buyCriteria.getCandleUpLength()) {
            annotation += " iUpMiddleLength/candleUpLength > k:" + iUpMiddleLength + " / " + candleUpLength + " > " + buyCriteria.getCandleMaxLength()/buyCriteria.getCandleUpLength();
            isOk = false;
        }
        if (isOk && candleUpLength/iUpMiddleLength > buyCriteria.getCandleUpLength()/buyCriteria.getCandleUpMiddleLength()) {
            annotation += " candleUpLength/iUpMiddleLength > k:" + candleUpLength + " / " + iUpMiddleLength + " > " + buyCriteria.getCandleUpLength()/buyCriteria.getCandleUpMiddleLength();
            isOk = false;
        }

        var candleMinLength = buyCriteria.getCandleMinLength();
        if (iUpMiddleLength > 1) {
            annotation += " iUpMiddleLength=" + iUpMiddleLength;
            candleMinLength = (int) (buyCriteria.getCandleUpLength() + Math.pow(candleMinLength - buyCriteria.getCandleUpLength(), 0.7) * iUpMiddleLength);
        }
        if (isOk && (iBeginDown - iEndDown) < candleMinLength) {
            annotation += " iEndReverse - iBeginTarget < MinLength:" + iBeginDown + " - " + iEndDown + " < " + candleMinLength;
            isOk = false;
        }
        if (isOk && iUpMiddleEnd >= iBeginDown) {
            annotation += " iUpMiddleEnd >= iBeginReverse:" + iUpMiddleEnd + " <= " + iBeginDown;
            isOk = false;
        }
        //if (isOk && iBeginDown/iUpMiddleLength > buyCriteria.getCandleMaxLength()/buyCriteria.getCandleUpMiddleLength()) {
        //    annotation += " iBeginDown/iUpMiddleLength > k:" + iBeginDown + " / " + iUpMiddleLength + " > " + buyCriteria.getCandleMaxLength()/buyCriteria.getCandleUpMiddleLength();
        //    isOk = false;
        //}
        var intervalPercent = 100f * (candleIPrev.get(iBeginDown).getOpenPrice().floatValue() - candle.getClosingPrice().floatValue())
                / candleIPrev.get(iBeginDown).getOpenPrice().abs().floatValue();
        if (!isDown) {
            intervalPercent = -intervalPercent;
        }
        if (isOk) {
            annotation += " intervalPercent = (" + candleIPrev.get(iBeginDown).getOpenPrice() + "(" + iBeginDown + ": " + candle.getDateTime() + ") - "
                    + candle.getClosingPrice() + ") = " + intervalPercent;
            if (intervalPercent < buyCriteria.getCandleIntervalMinPercent()) {
                annotation += " < " + buyCriteria.getCandleIntervalMinPercent();
                isOk = false;
            } else if (buyCriteria.isCandleIntervalTargetDirection(candleIPrev.get(iBeginDown).getOpenPrice(), candle.getClosingPrice())) {
                annotation += " total direction false";
                isOk = false;
            }
        }
        if (isOk && buyCriteria.isCandleIntervalTargetDirection(candleIPrev.get(iUpMiddle).getClosingPrice(), candleIPrev.get(iEndDown).getOpenPrice())) {
            annotation += " target: iUpMiddleEnd - iEndDown:" + candleIPrev.get(iUpMiddle).getClosingPrice() + "(" + iUpMiddle + ") - " + candleIPrev.get(iEndDown).getOpenPrice() + "(" + iEndDown + ")";
            annotation += " = false";
            isOk = false;
        }
        if (false && isOk && iUpMiddleEnd != iUpMiddleBegin
                && buyCriteria.isCandleIntervalTargetDirection(candleIPrev.get(iUpMiddleBegin).getClosingPrice(), candleIPrev.get(iEndDown).getOpenPrice())
        ) {
            annotation += " target: iUpMiddleEnd - iEndDown:" + candleIPrev.get(iUpMiddleBegin).getClosingPrice() + "(" + iUpMiddleBegin + ") - " + candleIPrev.get(iEndDown).getOpenPrice() + "(" + iEndDown + ")";
            annotation += " = false";
            isOk = false;
        }
        if (isOk) {
            annotation += " iReverseMiddleClose > iReverseOpen:" + candleIPrev.get(iUpMiddle).getClosingPrice() + "(" + iUpMiddle + ") - " + candleIPrev.get(iEndDown).getOpenPrice() + "(" + iEndDown + ")";
            annotation += " per > " + buyCriteria.getCandleIntervalMinPercent();
            annotation += " ok CandleInterval";
            res = true;
        }
        return CandleIntervalResultData.builder()
                .res(res)
                .annotation(annotation)
                .candle(candle)
                .isDown(isDown)
                .build();
    }

    private CandleDomainEntity getCandleHour(AInstrumentByFiatFactorialStrategy strategy, CandleDomainEntity candle)
    {
        var candleList = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                candle.getDateTime(), 2, strategy.getFactorialInterval());
        return candleList.get(1);
    }
    private FactorialDiffAvgAdapterStrategy buildAvgStrategy(AInstrumentByFiatFactorialStrategy strategy, CandleDomainEntity candle)
    {
        var newStrategy = new FactorialDiffAvgAdapterStrategy();
        newStrategy.setStrategy(strategy);
        if (strategy.getPriceDiffAvgLength() == null) {
            return newStrategy;
        }
        var curHourCandleForFactorial = getCandleHour(strategy, candle);
        var factorial = findBestFactorialInPast(strategy, curHourCandleForFactorial);

        var priceDiffAvgReal = factorial.getExpectLoss() + factorial.getExpectProfit();
        if (strategy.getPriceDiffAvgLength() > 0) {
            var candleListForAvg = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                    candle.getDateTime(), strategy.getPriceDiffAvgLength() + 1, strategy.getFactorialInterval());
            for (var i = 0; i < (candleListForAvg.size() - 1); i++) {
                var factorialForAvg = findBestFactorialInPast(strategy, candleListForAvg.get(i));
                if (null == factorialForAvg) {
                    return null;
                }
                priceDiffAvgReal += factorialForAvg.getExpectLoss() + factorialForAvg.getExpectProfit();
            }
            priceDiffAvgReal = priceDiffAvgReal / candleListForAvg.size();
        }
        newStrategy.setPriceDiffAvgReal(candle.getFigi(), priceDiffAvgReal);
        return newStrategy;
    }

    private CandleIntervalResultData isOrderCandleUp(
            FactorialDiffAvgAdapterStrategy strategy,
            CandleDomainEntity candle,
            Order order,
            AInstrumentByFiatFactorialStrategy.BuyCriteria buyCriteria,
            AInstrumentByFiatFactorialStrategy.SellCriteria sellCriteria
    ) {
        Boolean isOrderUpCandle = false;
        String annotation = "";
        String keyCandles = buildKeyCandleIntervals(strategy, candle);
        if (
                null != order
                && buyCriteria.getCandleIntervalMinPercent() != null
                && null != buyCriteria.getCandleOnlyUpLength()
                && null != sellCriteria.getCandleOnlyUpProfitMinPercent()
        ) {
            var intervalCandles = getCandleIntervals(strategy, candle);
            if (null != intervalCandles) {
                for (var i = intervalCandles.size() - 1; i >= 0; i--) {
                    var candleRes = intervalCandles.get(i);
                    if (!order.getPurchaseDateTime().isBefore(candleRes.candle.getDateTime())) {
                        annotation += " upOrDown: i=" + i + ":" + printDateTime(candleRes.candle.getDateTime());
                        if (!intervalCandles.get(i).isDown) {
                            annotation += " UP";
                            isOrderUpCandle = true;
                        }
                        break;
                    }
                }
            }
        }
        return CandleIntervalResultData.builder()
                .res(isOrderUpCandle)
                .annotation(annotation)
                .build();
    }

    private LinkedHashMap<String, List<CandleIntervalUpDownData>> candleIntervalUpDownDataMap = new LinkedHashMap<>();

    private CandleIntervalUpDownData getPrevCandleIntervalUpDownData(
            FactorialDiffAvgAdapterStrategy strategy,
            CandleIntervalUpDownData candleIntervalUpDownData
    ) {
        if (null == candleIntervalUpDownData.beginDownFirst) {
            return CandleIntervalUpDownData.builder()
                    .annotation(" null UpDownData")
                    .isDown(true)
                    .build();
        }
        /*
        var candleResUpFirstPrevs = candleHistoryService.getCandlesByFigiByLength(
                candleIntervalUpDownData.endPost.candle.getFigi(),
                candleIntervalUpDownData.endPost.candle.getDateTime(),
                2,
                strategy.getInterval()
        );
        return getCurCandleIntervalUpDownData(strategy, candleResUpFirstPrevs.get(0));
         */
        return getCurCandleIntervalUpDownData(strategy, candleIntervalUpDownData.beginDownFirst.candle);
    }

    private CandleIntervalUpDownData getCurCandleIntervalUpDownData(
            FactorialDiffAvgAdapterStrategy strategy,
            CandleDomainEntity candle
    ) {
        return getCurCandleIntervalUpDownData(strategy, candle, null);
    }

    private CandleIntervalUpDownData getCurCandleIntervalUpDownData(
            FactorialDiffAvgAdapterStrategy strategy,
            CandleDomainEntity candle,
            CandleIntervalResultData candleDownStart
    ) {
        String key = strategy.getExtName() + candle.getFigi();
        List<CandleIntervalUpDownData> listRes;
        synchronized (candleIntervalUpDownDataMap) {
            if (!candleIntervalUpDownDataMap.containsKey(key)) {
                listRes = new ArrayList<>();
                candleIntervalUpDownDataMap.put(key, listRes);
            } else {
                listRes = candleIntervalUpDownDataMap.get(key);
            }
        }
        var findI = -1;
        if (listRes.size() > 0 && candleDownStart == null) {
            for (var i = listRes.size() - 1; i >=0; i--) {
                var curCandleIntervalUpDownData = listRes.get(i);
                if (
                        candle.getDateTime().isBefore(curCandleIntervalUpDownData.endPost.getCandle().getDateTime())
                        && !candle.getDateTime().isBefore(curCandleIntervalUpDownData.beginDownFirst.getCandle().getDateTime())
                        && i > 0
                ) {
                    findI = i - 1;
                } else {
                    if (findI >= 0) {
                        break;
                    }
                }
            }
            if (findI >= 0) {
                return listRes.get(findI);
            }
        }
        var intervalCandles = getCandleIntervals(strategy, candle);

        CandleIntervalResultData candleResDown;
        CandleIntervalResultData candleResDownFirst;
        CandleIntervalResultData candleResDownPrev = null;
        CandleIntervalResultData candleResUpFirst = null;
        CandleIntervalResultData candleResUpFirstPrev = null;
        CandleIntervalResultData candleResUp = null;
        CandleIntervalResultData candleResDownPrevFirst = null;
        Float lastBottomPrice = null;
        Float lastTopPrice = null;
        Float lastBetweenPrice = null;
        CandleDomainEntity lastMaxCandle = null;
        CandleDomainEntity lastMinCandle = null;

        CandleIntervalResultData downPrevFirst = null;
        CandleIntervalResultData beginPre = null;
        CandleIntervalResultData begin = null;
        CandleIntervalResultData end = null;
        CandleIntervalResultData endPost = null;

        Boolean isFind = false;

        String annotation = "";
        if (null != intervalCandles) {
            Integer upCount = 0;
            Integer upDown = 0;
            Double maxPricePrev = null;
            Double minPricePrev = null;
            for (var upDownCount = 0; upDownCount < 200; upDownCount ++) {
                if (null == candleResUpFirst) {
                    if (candleDownStart != null) {
                        candleResDown = candleDownStart;
                    } else {
                        candleResDown = intervalCandles.stream().filter(
                                c -> c.isDown
                                        && !c.candle.getDateTime().isAfter(candle.getDateTime())
                        ).reduce((first, second) -> second).orElse(null);
                    }
                } else {
                    CandleIntervalResultData finalCandleResUpFirst1 = candleResUpFirst;
                    candleResDown = intervalCandles.stream().filter(c ->
                            c.isDown
                            && finalCandleResUpFirst1.getCandle().getDateTime().compareTo(c.getCandle().getDateTime()) > 0
                    ).reduce((first, second) -> second).orElse(null);
                }
                if (null != candleResDown) {
                    annotation += " down = " + printDateTime(candleResDown.getCandle().getDateTime());
                    CandleIntervalResultData finalCandleResDown = candleResDown;
                    candleResUp = intervalCandles.stream().filter(c ->
                            !c.isDown
                                    && finalCandleResDown.getCandle().getDateTime().compareTo(c.getCandle().getDateTime()) > 0
                    ).reduce((first, second) -> second).orElse(null);
                } else {
                    candleResUp = null;
                    break;
                }
                if (null != candleResUp) {
                    annotation += " uP = " + printDateTime(candleResUp.getCandle().getDateTime());
                    //lastTopPrice = candleResUp.getCandle().getClosingPrice().floatValue();
                    //lastBottomPrice = candleResDown.getCandle().getClosingPrice().floatValue();
                    //annotation += " lastTopPrice = " + printPrice(lastTopPrice);
                    //lastBetweenPrice = lastTopPrice - lastBottomPrice;
                    //annotation += " lastBetweenPrice = " + printPrice(lastBetweenPrice);
                    CandleIntervalResultData finalCandleResUp = candleResUp;
                    candleResDownPrev = intervalCandles.stream().filter(c ->
                            c.isDown
                                    && finalCandleResUp.getCandle().getDateTime().compareTo(c.getCandle().getDateTime()) > 0
                    ).reduce((first, second) -> second).orElse(null);
                    candleResDownFirst = intervalCandles.stream().filter(c ->
                            c.isDown
                                    && finalCandleResUp.getCandle().getDateTime().compareTo(c.getCandle().getDateTime()) < 0
                    ).findFirst().orElse(null);
                    if (candleResDownFirst == null && upDownCount == 0 && candleDownStart != null) {
                        candleResDownFirst = candleDownStart;
                    }
                } else {
                    candleResDownPrev = null;
                    break;
                }
                if (null != candleResDownPrev) {
                    annotation += " downPrev = " + printDateTime(candleResDownPrev.getCandle().getDateTime());
                    annotation += " downFirst = " + printDateTime(candleResDownFirst.getCandle().getDateTime());
                    CandleIntervalResultData finalCandleResDownPrev = candleResDownPrev;
                    CandleIntervalResultData finalCandleResDown1 = candleResDownFirst;
                    candleResUpFirstPrev = candleResUpFirst;
                    candleResUpFirst = intervalCandles.stream().filter(c ->
                            !c.isDown
                                    && finalCandleResDownPrev.getCandle().getDateTime().compareTo(c.getCandle().getDateTime()) < 0
                                    && finalCandleResDown1.getCandle().getDateTime().compareTo(c.getCandle().getDateTime()) > 0
                    ).findFirst().orElse(null);
                } else {
                    candleResUpFirst = null;
                    CandleIntervalResultData finalCandleResUp2 = candleResUp;
                    var candleResDownPrevList = intervalCandles.stream().filter(c ->
                                    finalCandleResUp2.getCandle().getDateTime().compareTo(c.getCandle().getDateTime()) > 0
                    ).collect(Collectors.toList());
                    annotation += " downPrevSize = " + candleResDownPrevList.size();
                    for (var i = candleResDownPrevList.size() - 1; i >= 0; i--) {
                        annotation += " i = " + i + " isDown=" + candleResDownPrevList.get(i).isDown + " : " + candleResDownPrevList.get(i).getCandle().getDateTime();
                    }
                    break;
                }

                if (null != candleResUpFirst) {
                    annotation += " upFirst = " + printDateTime(candleResUpFirst.getCandle().getDateTime());
                    CandleIntervalResultData finalCandleResUpFirst = candleResUpFirst;
                    CandleIntervalResultData finalCandleResUp1 = candleResUp;
                    var intervalsBetweenLast = intervalCandles.stream().filter(c ->
                            finalCandleResUpFirst.getCandle().getDateTime().compareTo(c.getCandle().getDateTime()) <= 0
                                && finalCandleResUp1.getCandle().getDateTime().compareTo(c.getCandle().getDateTime()) >= 0
                    ).collect(Collectors.toList());

                    CandleIntervalResultData finalCandleResDownPrev1 = candleResDownPrev;
                    var candleResUpPrev = intervalCandles.stream().filter(c ->
                            !c.isDown
                                    && finalCandleResDownPrev1.getCandle().getDateTime().compareTo(c.getCandle().getDateTime()) > 0
                    ).reduce((first, second) -> second).orElse(null);
                    if (candleResUpPrev != null) {
                        annotation += " upPrev = " + printDateTime(candleResUpPrev.getCandle().getDateTime());
                    }
                    var candleResDownPrevList = intervalCandles.stream().filter(c ->
                            c.isDown
                            && finalCandleResDownPrev1.getCandle().getDateTime().compareTo(c.getCandle().getDateTime()) >= 0
                            && (candleResUpPrev == null || candleResUpPrev.getCandle().getDateTime().compareTo(c.getCandle().getDateTime()) < 0)
                    ).collect(Collectors.toList());
                    candleResDownPrevFirst = candleResDownPrevList.get(0);
                    annotation += " prevFirst = " + printDateTime(candleResDownPrevFirst.getCandle().getDateTime())
                        + "(" + candleResDownPrevList.size() + "+" + upDown + ")";

                    annotation += " intervalsBetweenLast.size=" + intervalsBetweenLast.size() + "+" + upCount;
                    var candlesBetweenLast = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                            candle.getFigi(),
                            candleResUpPrev == null ? candleResDownPrevFirst.getCandle().getDateTime() : candleResUpPrev.getCandle().getDateTime(),
                            candleResUp.getCandle().getDateTime(),
                            strategy.getInterval()
                    );
                    var candleResUpFirstPrevs = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(), candleResUpFirst.getCandle().getDateTime(), 2, strategy.getInterval());
                    var candlesBetweenFirst = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                            candle.getFigi(),
                            //candleResUpFirstPrevs.get(0).getDateTime(),
                            candleResUpPrev == null ? candleResDownPrevFirst.getCandle().getDateTime() : candleResUpPrev.getCandle().getDateTime(),
                            candleResDownFirst.getCandle().getDateTime(),
                            strategy.getInterval()
                    );

                    /*
                    CandleIntervalResultData finalCandleResDown2 = candleResDown;
                    var candles = intervalCandles.stream().filter(c ->
                                    candleResUpPrev.getCandle().getDateTime().compareTo(c.getCandle().getDateTime()) >= 0
                                    && finalCandleResDown2.getCandle().getDateTime().compareTo(c.getCandle().getDateTime()) <= 0
                    ).collect(Collectors.toList());

                    annotation += " size=" + candles.size();
                    for (var i = 0; i < candles.size(); i++) {
                        var c = candles.get(i);
                        annotation += " " + i + (c.isDown ? " DOWN ": " UP ") + printDateTime(c.getCandle().getDateTime());
                    }
                     */

                    var minPrice = candlesBetweenLast.stream().mapToDouble(value -> value.getClosingPrice().min(value.getOpenPrice()).doubleValue()).min().orElse(-1);
                    var minCandle = candlesBetweenLast.stream().reduce((first, second) ->
                            first.getClosingPrice().min(first.getOpenPrice()).compareTo(second.getClosingPrice().min(second.getOpenPrice())) < 0 ? first : second
                    ).orElse(null);
                    minPrice = minCandle.getClosingPrice().min(minCandle.getOpenPrice()).doubleValue();

                    var maxPrice = candlesBetweenFirst.stream().mapToDouble(value -> value.getClosingPrice().max(value.getOpenPrice()).doubleValue()).max().orElse(-1);
                    //var averagePrice = candlesBetweenFirst.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).average().orElse(-1);
                    var maxCandle = candlesBetweenFirst.stream().reduce((first, second) ->
                            first.getClosingPrice().max(first.getOpenPrice()).compareTo(second.getClosingPrice().max(second.getOpenPrice())) > 0 && first.getDateTime().isAfter(minCandle.getDateTime()) ? first : second).orElse(null);

                    //var minPrice = candlesBetweenLast.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).min().orElse(-1);
                    //var minCandle = candlesBetweenLast.stream().reduce((first, second) ->
                    //        first.getClosingPrice().compareTo(second.getClosingPrice()) < 0 || second.getDateTime().isAfter(maxCandle.getDateTime()) ? first : second
                    //).orElse(null);
                    //minPrice = minCandle.getClosingPrice().doubleValue();

                    maxPrice = maxCandle.getClosingPrice().max(maxCandle.getOpenPrice()).doubleValue();
                    annotation += " maxPrice = " + printPrice(maxPrice);
                    annotation += " minPrice = " + printPrice(minPrice);
                    lastBetweenPrice = (float) (maxPrice - minPrice);
                    annotation += " between = " + printPrice(lastBetweenPrice);
                    var isOk = true;
                    var isNewTop = false;
                    var isNewBottom = false;
                    var isAnother = false;
                    var isSkip = false;
                    //var downPrevPriceMin = candleResDownPrevList.stream().mapToDouble(v -> v.getCandle().getClosingPrice().min(v.getCandle().getOpenPrice()).doubleValue())
                    //        .min().orElseThrow();
                    //var upPriceMax = intervalsBetweenLast.stream().mapToDouble(v -> v.getCandle().getClosingPrice().max(v.getCandle().getOpenPrice()).doubleValue())
                    //        .max().orElseThrow();
                    //var percentUp = 100f * (upPriceMax - downPrevPriceMin) / Math.abs(downPrevPriceMin);
                    var percentUp = 100f * (maxPrice - minPrice) / Math.abs(minPrice);
                    annotation += " percentUp=" + printPrice(percentUp) + " < " + strategy.getBuyCriteria().getCandlePriceMinUpDownPercent();
                    if (
                            //downPrevPriceMin >= upPriceMax
                            minPrice >= maxPrice
                            || percentUp < strategy.getBuyCriteria().getCandlePriceMinUpDownPercent()
                    ) {
                        annotation += " skip down >= up: " + minPrice + " >= " + maxPrice;
                        isSkip = true;
                        candleResUpFirst = candleResUpFirstPrev;
                        for (var i = 0; i < intervalsBetweenLast.size(); i++) {
                            var delRes = intervalCandles.remove(intervalsBetweenLast.get(i));
                            annotation += " del i=" + i + ": " + delRes;
                        }
                    }
                    if (
                            null != maxPricePrev
                            && null != strategy.getBuyCriteria().getCandleUpDownSkipDeviationPercent()
                            && !isSkip
                    ) {
                        var maxLength = Math.max(maxPricePrev - minPricePrev, maxPrice - minPrice);
                        var maxLengthAbs = Math.max(maxPricePrev, maxPrice) - Math.min(minPricePrev, minPrice);
                        var deviationPercentSize = 100f * Math.abs((maxPricePrev - minPricePrev) - (maxPrice - minPrice)) / maxLength;
                        var deviationPercentPosition = 100f * Math.abs(maxPricePrev - maxPrice) / maxLength;
                        var deviationPercentAbs = 100f - 100f * maxLength / maxLengthAbs;
                        var deviationPercentTogether = 100f * maxLengthAbs / ((maxPricePrev - minPricePrev) + (maxPrice - minPrice));
                        annotation += " deviationPercentSize = " + deviationPercentSize + " < " + strategy.getBuyCriteria().getCandleUpDownSkipDeviationPercent();
                        annotation += " deviationPercentPosition = " + deviationPercentPosition;
                        annotation += " deviationPercentAbs = " + deviationPercentAbs;
                        annotation += " deviationPercentTogether = " + printPrice(deviationPercentTogether);
                        annotation += " " + printPrice(maxPricePrev - minPricePrev) + " > " + printPrice(maxPrice - minPrice);
                        isAnother = deviationPercentTogether > 100f;
                        if (
                                lastTopPrice != null && lastTopPrice < maxPrice
                                && deviationPercentTogether > 50f
                                //&& (strategy.getBuyCriteria().getCandleUpDownSkipCount() == null || (upCount + upDown) > strategy.getBuyCriteria().getCandleUpDownSkipCount())
                        ) {
                            annotation += " upCount : upDown: " + upCount + " : " + upDown + " > " + strategy.getBuyCriteria().getCandleUpOrDownMinCount()
                                    + " >> " + strategy.getBuyCriteria().getCandleUpDownMinCount();
                            if (
                                    strategy.getBuyCriteria().getCandleUpOrDownMinCount() == null
                                    || (upCount > strategy.getBuyCriteria().getCandleUpOrDownMinCount()
                                            && upDown > strategy.getBuyCriteria().getCandleUpOrDownMinCount()
                                            && upCount + upDown > strategy.getBuyCriteria().getCandleUpDownMinCount()
                                    )
                            ) {
                                isAnother = true;
                            } else {
                                annotation += " isAnother new ";
                                lastTopPrice = null;
                                lastMaxCandle = null;
                                lastBottomPrice = null;
                                lastMinCandle = null;
                            }
                        }
                        annotation += " isAnother = " + isAnother;
                        if (
                                isAnother
                                //deviationPercentAbs < (strategy.getBuyCriteria().getCandleUpDownSkipDeviationPercent() / 2)
                                || (
                                        (deviationPercentSize < strategy.getBuyCriteria().getCandleUpDownSkipDeviationPercent()
                                && deviationPercentPosition < strategy.getBuyCriteria().getCandleUpDownSkipDeviationPercent())
                                //|| (maxPricePrev - minPricePrev) > (maxPrice - minPrice)
                                )
                        ) {
                            isOk = false;
                        } else if (maxPrice < maxPricePrev) {
                            //isOk = false;
                        }
                    }
                    if (
                            (isOk || lastTopPrice == null)
                            && !isSkip
                    ) {
                        annotation += " isOk";
                        if (lastTopPrice == null || lastTopPrice < maxPrice) {
                            lastTopPrice = (float) (maxPrice);
                            lastMaxCandle = maxCandle;
                            isNewTop = true;
                        }
                        if (lastBottomPrice == null
                                || lastBottomPrice > minPrice
                                || isNewTop
                        ) {
                            lastBottomPrice = (float) minPrice;
                            lastMinCandle = minCandle;
                            isNewBottom = true;
                        }

                        if (end == null || isNewTop || isNewBottom) {
                            beginPre = candleResDownPrev;
                            downPrevFirst = candleResDownPrevFirst;
                            begin = candleResUpFirst;
                            annotation += " isNewSize";
                            if (end == null || isNewTop) {
                                end = candleResUp;
                                endPost = candleResDownFirst;
                            }
                            if (maxPricePrev != null) {
                                maxPricePrev = Math.max(maxPrice, maxPricePrev);
                            } else {
                                maxPricePrev = maxPrice;
                            }
                            if (minPricePrev != null) {
                                minPricePrev = Math.min(minPrice, minPricePrev);
                            } else {
                                minPricePrev = minPrice;
                            }
                            if (
                                    isNewTop
                                    //&& isNewBottom
                            ) {
                                upCount = 0;
                                upDown = 0;
                            }
                        }
                    }
                    if (null != strategy.getBuyCriteria().getCandleUpDownSkipLength()
                            && (intervalsBetweenLast.size() + upCount) < strategy.getBuyCriteria().getCandleUpDownSkipLength()
                            && (null == strategy.getBuyCriteria().getCandleUpDownSkipCount() || (upDown + candleResDownPrevList.size())  < strategy.getBuyCriteria().getCandleUpDownSkipCount())
                            && !isAnother
                            && (isNewTop || isNewBottom)
                    ) {
                        annotation += " < " + strategy.getBuyCriteria().getCandleUpDownSkipLength();
                        annotation += " < " + strategy.getBuyCriteria().getCandleUpDownSkipCount();
                        upCount += intervalsBetweenLast.size();
                        upDown += candleResDownPrevList.size();
                        if (maxPricePrev == null) {
                            maxPricePrev = maxPrice;
                        }
                        if (minPricePrev == null) {
                            minPricePrev = minPrice;
                        }
                        continue;
                    }
                    if (!isSkip) {
                        isFind = true;
                        break;
                    }
                }
            }
        } else {
            candleResDownPrev = null;
            candleResUp = null;
            candleResDown = null;
        }

        var res = CandleIntervalUpDownData.builder()
                .annotation(annotation)
                .isDown(true)
                .build();

        if (isFind) {
            res.setMinClose(lastBottomPrice);
            res.setMaxClose(lastTopPrice);
            res.setMinCandle(lastMinCandle);
            res.setMaxCandle(lastMaxCandle);
            res.setPriceBegin(end == null ? null : end.getCandle().getClosingPrice().floatValue());
            res.setPriceEnd(endPost == null ? null : endPost.getCandle().getClosingPrice().floatValue());
            res.setBeginDownFirst(downPrevFirst);
            res.setBeginPre(beginPre);
            res.setBegin(begin);
            res.setEnd(end);
            res.setEndPost(endPost);
        }

        if (
                res.minClose != null
                && candleDownStart == null
                && (listRes.size() == 0
                        || res.endPost.candle.getDateTime().isAfter(listRes.get(listRes.size() - 1).endPost.candle.getDateTime())
                )
        ) {
            synchronized (candleIntervalUpDownDataMap) {
                candleIntervalUpDownDataMap.get(key).add(res);
            }
        }
        return res;
    }

    @Builder
    @Data
    public static class CandleIntervalBuyResult {
        Boolean candleIntervalBuy;
        Boolean candleIntervalSell;
        Boolean res;
        String annotation;
        CandleIntervalUpDownData candleIntervalUpDownData;
        Float candlePriceMinFactor;
        Float candlePriceMaxFactor;
        Float notLossBuyUnderPrice;
        Boolean isIntervalUp;
    }

    private CandleIntervalBuyResult getCandleBuyRes(
            FactorialDiffAvgAdapterStrategy newStrategy,
            CandleDomainEntity candle)
    {
        var candleIntervalBuy = false;
        var candleIntervalSell = false;
        var buyCriteria = newStrategy.getBuyCriteria();
        var sellCriteria = newStrategy.getSellCriteria();
        var strategy = newStrategy;
        var annotation = "";
        var res = false;
        var isIntervalDown = false;
        var isIntervalUp = false;
        var candleIntervalUpDownData = getCurCandleIntervalUpDownData(newStrategy, candle);
        String keyCandles = buildKeyCandleIntervals(strategy, candle);
        var candleIntervalRes = checkCandleInterval(candle, buyCriteria);
        CandleIntervalResultData candleIntervalResSell = null;
        annotation += candleIntervalRes.annotation;
        Float candlePriceMinFactor = null;
        Float candlePriceMaxFactor = null;
        Float notLossBuyUnderPrice = null;
        if (buyCriteria.getCandleIntervalMinPercent() != null) {
            if (candleIntervalRes.res) {
                candleIntervalBuy = true;
            } else {
                candleIntervalResSell = checkCandleInterval(candle, sellCriteria);
                if (!candleIntervalResSell.res
                        && sellCriteria.getCandleUpLength() > 1
                        && null != sellCriteria.getCandleTrySimple()
                ) {
                    var sellCriteriaSimple = sellCriteria.clone();
                    sellCriteriaSimple.setCandleUpLength(sellCriteria.getCandleUpLength() / sellCriteria.getCandleTrySimple());
                    sellCriteriaSimple.setCandleIntervalMinPercent(sellCriteria.getCandleIntervalMinPercent() * sellCriteria.getCandleTrySimple());
                    candleIntervalResSell = checkCandleInterval(candle, sellCriteriaSimple);
                    annotation += " res candleIntervalSimple=" + candleIntervalResSell.res;
                }
                if (candleIntervalResSell.res) {
                    candleIntervalSell = true;
                    annotation += " SELL ok: " + candleIntervalResSell.annotation;
                }
            }
            if (candleIntervalRes.res
                    || (candleIntervalResSell.res
                        && buyCriteria.getCandleUpSellPointLength() != null
                        && candleIntervalUpDownData.maxClose != null
                        && candle.getClosingPrice().floatValue() > candleIntervalUpDownData.maxClose
                )
            ) {
                var intervalCandles = getCandleIntervals(newStrategy, candle);

                if (candleIntervalRes.res) {
                    addCandleInterval(keyCandles, candleIntervalRes);
                    candleIntervalUpDownData = getCurCandleIntervalUpDownData(newStrategy, candle);
                }

                var isOrderFind = false;
                var order = orderService.findLastByFigiAndStrategy(candle.getFigi(), strategy);
                var upRes = isOrderCandleUp(newStrategy, candle, order, buyCriteria, sellCriteria);
                if (upRes.res) {
                    annotation += upRes.annotation;
                    order = null;
                }
                if (null != intervalCandles) {
                    CandleDomainEntity candleIntervalResBuyLast = null;
                    CandleDomainEntity candleIntervalResSellLast = null;
                    CandleIntervalResultData candleIntervalResBuyOk = null;
                    CandleIntervalResultData candleIntervalResSellOk = null;
                    CandleIntervalResultData candleIntervalResSellOk2 = null;
                    annotation += " intervalCandles.size() = " + intervalCandles.size() + ": " + intervalCandles.get(0).getCandle().getDateTime()
                            + " - " + intervalCandles.get(intervalCandles.size() - 1).getCandle().getDateTime();
                    for (var i = intervalCandles.size() - 1; i >= 0 && !isOrderFind && null == buyCriteria.getCandleMinFactor() && candleIntervalRes.res; i--) {
                        var candleRes = intervalCandles.get(i);
                        if (null == candleIntervalResSellOk) {
                            if (!candleRes.isDown) {
                                annotation += " SELL i = " + printPrice(candleRes.getCandle().getClosingPrice()) + "(" + candleRes.getCandle().getDateTime() + ")";
                                candleIntervalResSellOk = candleRes;
                                candleIntervalResSellLast = candleRes.candle;
                                if (
                                        !isOrderFind
                                                && null != order
                                                && order.getSellDateTime().isAfter(candleIntervalResSellOk.candle.getDateTime())
                                ) {
                                    annotation += " ORDER buy < sell: " + printPrice(order.getPurchasePrice()) + " (" + order.getPurchaseDateTime() +
                                            ") < " + printPrice(order.getSellPrice()) + "(" + order.getSellProfit() + ")";
                                    if (order.getPurchasePrice().compareTo(order.getSellPrice()) < 0) {
                                        annotation += " CandleInterval ?";
                                        //res = true;
                                    /*candleIntervalResBuyLast = candleHistoryService.getCandlesByFigiByLength(
                                            candle.getFigi(),
                                            order.getPurchaseDateTime(),
                                            1,
                                            strategy.getInterval()
                                    ).get(0);*/
                                    }
                                    candleIntervalResSellLast = candleHistoryService.getCandlesByFigiByLength(
                                            candle.getFigi(),
                                            order.getSellDateTime(),
                                            1,
                                            strategy.getInterval()
                                    ).get(0);
                                    isOrderFind = true;
                                }
                            }
                        } else {
                            if (!candleRes.isDown) {
                                annotation += " SELL i = " + printPrice(candleRes.getCandle().getClosingPrice()) + "(" + candleRes.getCandle().getDateTime() + ")";
                                if (null != candleIntervalResSellOk2) {
                                    candleIntervalResSellOk = candleIntervalResSellOk2;
                                }
                                candleIntervalResSellOk2 = candleRes;
                                if (null != candleIntervalResBuyOk) {
                                    annotation += " buy < sell: " + printPrice(candleIntervalResBuyOk.candle.getClosingPrice()) + " (" + candleIntervalResBuyOk.candle.getDateTime() +
                                            ") < " + printPrice(candleIntervalResSellOk.candle.getClosingPrice()) + "(" + candleIntervalResSellOk.candle.getDateTime() + ")";
                                    if (candleIntervalResBuyOk.candle.getClosingPrice().compareTo(candleIntervalResSellOk.candle.getClosingPrice()) < 0) {
                                        annotation += " CandleInterval OK";
                                        res = true;
                                    }
                                    isOrderFind = true;
                                    break;
                                }
                            }
                            if (candleRes.isDown) {
                                if (null == candleIntervalResBuyLast) {
                                    candleIntervalResBuyLast = candleRes.candle;
                                }
                                candleIntervalResBuyOk = candleRes;
                                annotation += " BUY i = " + printPrice(candleRes.getCandle().getClosingPrice()) + "(" + candleRes.getCandle().getDateTime() + ")";
                            }
                        }
                    }
                    if (
                            isOrderFind
                                    && candleIntervalRes.res
                                    && null == buyCriteria.getCandleMinFactor()
                                    && !res
                                    && null != order
                                    && null != candleIntervalResBuyOk
                                    && order.getSellDateTime().isAfter(candleIntervalResBuyOk.candle.getDateTime())
                    ) {
                        isOrderFind = true;
                        if (order.getPurchasePrice().compareTo(order.getSellPrice()) < 0) {
                            annotation += " ORDER buy < sell: " + printPrice(order.getPurchasePrice()) + " (" + order.getPurchaseDateTime() +
                                    ") < " + printPrice(order.getSellPrice()) + "(" + order.getSellProfit() + ")";
                            annotation += " CandleInterval OK";
                            res = true;
                        }
                    }
                    if (
                            true
                                    //res
                                    && null == buyCriteria.getCandleMinFactor()
                                    && isOrderFind
                                    && candleIntervalRes.res
                                    && null != order
                                    && (candleIntervalResBuyOk == null
                                    //|| candleIntervalResBuyOk.candle.getDateTime().isBefore(candleIPrev.get(0).getDateTime())
                            )
                                    //&& order.getSellDateTime().isBefore(candleIPrev.get(0).getDateTime())
                                    && order.getPurchasePrice().compareTo(order.getSellPrice()) < 0
                    ) {
                        annotation += " ORDER after buy < sell: " + printPrice(order.getPurchasePrice()) + " (" + order.getPurchaseDateTime() +
                                ") < " + printPrice(order.getSellPrice()) + "(" + order.getSellProfit() + ")";
                        annotation += " CandleInterval OK";
                        res = true;
                    }

                    //CandleDomainEntity candleIntervalResBuyLast = null;
                    //CandleDomainEntity candleIntervalResDownFirst = null;
                    CandleIntervalUpDownData candleIntervalUpDownDataPrev = null;
                    CandleIntervalUpDownData candleIntervalUpDownDataPrevPrev = null;
                    Boolean isPrevPrev = false;
                    if (null != buyCriteria.getCandleMinFactor()) {
                        annotation += candleIntervalUpDownData.annotation;
                        if (null != candleIntervalUpDownData.beginPre) {
                            candleIntervalResBuyLast = candleIntervalUpDownData.beginPre.candle;
                            candleIntervalResSellLast = candleIntervalUpDownData.end.candle;
                            if (candleIntervalUpDownData.maxCandle.getDateTime().isAfter(candleIntervalResBuyLast.getDateTime())) {
                                candleIntervalResSellLast = candleIntervalUpDownData.maxCandle;
                            }
                            //candleIntervalResBuyLast = candleIntervalUpDownData.minCandle;
                            //candleIntervalResSellLast = candleIntervalUpDownData.maxCandle;
                            // maxCandle
                            annotation += " endPost: " + printDateTime(candleIntervalUpDownData.beginDownFirst.candle.getDateTime());
                            candleIntervalUpDownDataPrevPrev = candleIntervalUpDownDataPrev = getPrevCandleIntervalUpDownData(newStrategy, candleIntervalUpDownData);
                            if (candleIntervalUpDownDataPrev != null && candleIntervalUpDownDataPrev.maxCandle != null) {
                                annotation += " " + printPrice(candleIntervalUpDownDataPrev.maxClose) + "-" + printPrice(candleIntervalUpDownDataPrev.minClose);
                                annotation += " PendPost: " + printDateTime(candleIntervalUpDownDataPrev.beginDownFirst.candle.getDateTime());
                                candleIntervalUpDownDataPrevPrev = getPrevCandleIntervalUpDownData(newStrategy, candleIntervalUpDownDataPrev);
                                if (null != candleIntervalUpDownDataPrevPrev
                                        && candleIntervalUpDownDataPrevPrev.minClose != null
                                ) {
                                    isPrevPrev = true;
                                    annotation += " " + printPrice(candleIntervalUpDownDataPrevPrev.maxClose) + "-" + printPrice(candleIntervalUpDownDataPrevPrev.minClose);
                                    annotation += " PPendPost: " + printDateTime(candleIntervalUpDownDataPrevPrev.beginDownFirst.candle.getDateTime());
                                    //candleIntervalUpDownDataPrevPrev.minClose = Math.min(candleIntervalUpDownDataPrev.minClose, candleIntervalUpDownDataPrevPrev.minClose);
                                    //candleIntervalUpDownDataPrevPrev.maxClose = Math.max(candleIntervalUpDownDataPrev.maxClose, candleIntervalUpDownDataPrevPrev.maxClose);
                                } else {
                                    candleIntervalUpDownDataPrevPrev = candleIntervalUpDownDataPrev;
                                    annotation += " something wrong" + candleIntervalUpDownDataPrevPrev.annotation;
                                }
                            } else if (candleIntervalUpDownDataPrev != null) {
                                annotation += " candleIntervalUpDownDataPrev: " + candleIntervalUpDownDataPrev.annotation;
                            }
                            //annotation += " candleIntervalUpDownDataPrev: " + candleIntervalUpDownDataPrev.annotation;
                        }
                        //if (null != candleIntervalUpDownData.endPost) {
                        //    candleIntervalResDownFirst = candleIntervalUpDownData.endPost.candle;
                        //}
                    }
                    if (
                            null != buyCriteria.getCandleMinFactor()
                                    && null != candleIntervalResSellLast
                                    && null != candleIntervalResBuyLast
                                    && null != candleIntervalUpDownData.minClose
                            && candleIntervalUpDownDataPrev != null && candleIntervalUpDownDataPrev.beginDownFirst != null
                        //&& null != candleIntervalResDownFirst
                    ) {
                        annotation += " factor: " + printDateTime(candleIntervalResBuyLast.getDateTime())
                                + " - " + printDateTime(candleIntervalResSellLast.getDateTime());
                        var downCandles = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                                candle.getFigi(),
                                candleIntervalResSellLast.getDateTime(),
                                candle.getDateTime(),
                                strategy.getInterval()
                        );
                        var upCandles = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                                candle.getFigi(),
                                candleIntervalResBuyLast.getDateTime(),
                                candleIntervalResSellLast.getDateTime(),
                                strategy.getInterval()
                        );
                    /*var minPrice = Math.min(
                            upCandles.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).min().orElse(-1),
                            downCandles.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).min().orElse(-1)
                            );
                        var minPrice = upCandles.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).min().orElse(-1);
                        var maxPrice = Math.max(
                                upCandles.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).max().orElse(-1),
                                downCandles.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).max().orElse(-1)
                        );*/
                        var minPrice = candleIntervalUpDownData.minClose;
                        var maxPrice = candleIntervalUpDownData.maxClose;
                        var factorPrice = (maxPrice - candle.getClosingPrice().floatValue())
                                / (maxPrice - minPrice);
                        var profitPercent = 100f * (maxPrice - minPrice) / Math.abs(minPrice);
                        annotation += " min = " + printPrice(minPrice);
                        annotation += " max = " + printPrice(maxPrice);
                        annotation += " profitPercent = " + printPrice(profitPercent) + " > " + buyCriteria.getCandleProfitMinPercent();
                        annotation += " factorPrice = " + printPrice(maxPrice - candle.getClosingPrice().floatValue())
                                + " / " + printPrice(maxPrice - minPrice)
                                + " = " + printPrice(factorPrice) + " < " + buyCriteria.getCandlePriceMaxFactor();
                        Float factorCandle = 1f * upCandles.size() / downCandles.size();
                        annotation += " factorCandle = " + printPrice(1f * upCandles.size())
                                + " / " + printPrice(1f * downCandles.size())
                                + " = " + printPrice(factorCandle);
                        var factor2 = factorPrice * factorPrice * Math.sqrt(Math.sqrt(Math.sqrt(factorCandle)));
                        var factor = factorPrice * factorPrice * Math.sqrt(factorCandle);
                        annotation += " factor = " + printPrice((float) factor);
                        annotation += " factor2 = " + printPrice((float) factor2);
                        candlePriceMinFactor = buyCriteria.getCandlePriceMinFactor();
                        candlePriceMaxFactor = buyCriteria.getCandlePriceMaxFactor();
                        var candleMinFactorCandle = factorPrice * buyCriteria.getCandleMinFactorCandle();
                        annotation += " candleMinFactorCandle = " + candleMinFactorCandle;
                        CandleIntervalUpResult isIntervalUpRes = null;
                        if (null != candleIntervalUpDownDataPrev) {
                            var size = Math.max(
                                    candleIntervalUpDownDataPrev.maxClose - candleIntervalUpDownDataPrev.minClose,
                                    candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose
                            );
                            var minPercent = 100f * (candleIntervalUpDownDataPrev.minClose - candleIntervalUpDownData.minClose) / size;
                            var maxPercent = 100f * (candleIntervalUpDownData.maxClose - candleIntervalUpDownDataPrev.maxClose) / size;
                            var minPercentPrev = minPercent;
                            var maxPercentPrev = maxPercent;
                            if (candleIntervalUpDownDataPrevPrev != null) {
                                var minClose = Math.min(candleIntervalUpDownDataPrev.minClose, candleIntervalUpDownDataPrevPrev.minClose);
                                var maxClose = Math.max(candleIntervalUpDownDataPrev.maxClose, candleIntervalUpDownDataPrevPrev.maxClose);
                                var sizePrev = Math.max(
                                        maxClose - minClose,
                                        candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose
                                );
                                minPercentPrev = 100f * (minClose - candleIntervalUpDownData.minClose) / sizePrev;
                                maxPercentPrev = 100f * (candleIntervalUpDownData.maxClose - maxClose) / sizePrev;

                                if (isPrevPrev) {
                                    isIntervalUpRes = calcIsIntervalUp(
                                            candle,
                                            order,
                                            newStrategy,
                                            candleIntervalUpDownData,
                                            candleIntervalUpDownDataPrev,
                                            candleIntervalUpDownDataPrevPrev,
                                            getPrevCandleIntervalUpDownData(newStrategy, candleIntervalUpDownDataPrevPrev)
                                    );
                                    isIntervalUp = isIntervalUpRes.isIntervalUp;
                                    annotation += isIntervalUpRes.annotation;
                                }
                            }
                            annotation += " minPercent = " + minPercent;
                            annotation += " maxPercent = " + maxPercent;
                            annotation += " minPercentPrev = " + minPercentPrev;
                            annotation += " maxPercentPrev = " + maxPercentPrev;

                            var avgPercentPrev = (minPercentPrev - maxPercentPrev) / 2;
                            var avgPercent = (minPercent - maxPercent) / 2;
                            annotation += " avgPercent = " + avgPercent;
                            annotation += " avgPercentPrev = " + avgPercentPrev;
                            isIntervalDown = maxPercent < 0;
                            if (
                                    candleIntervalUpDownDataPrev.minClose > candleIntervalUpDownData.maxClose
                                    && (candleIntervalUpDownDataPrev.minClose - candleIntervalUpDownData.maxClose)
                                            > ((candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) / 2)
                            ) {
                                isIntervalDown = true;
                                candlePriceMinFactor = Math.max(
                                        0.5f,
                                        (candleIntervalUpDownDataPrev.maxClose - candleIntervalUpDownDataPrev.minClose)
                                                / (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose)
                                );
                                if (candleIntervalUpDownDataPrevPrev.minClose > candleIntervalUpDownDataPrev.minClose) {
                                    candlePriceMinFactor = Math.max(
                                            candlePriceMinFactor,
                                            (candleIntervalUpDownDataPrevPrev.maxClose - candleIntervalUpDownDataPrev.minClose)
                                                    / (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose)
                                    );
                                }
                                annotation += " new 1 candlePriceMinFactor = " + candlePriceMinFactor;
                            } else if (
                                    candleIntervalUpDownDataPrevPrev != null
                                    && candleIntervalUpDownDataPrevPrev.minClose > candleIntervalUpDownDataPrev.maxClose
                            ) {
                                isIntervalDown = true;
                                candlePriceMinFactor = candlePriceMinFactor + 0.5f;
                                candlePriceMaxFactor = candlePriceMaxFactor + 1.f;
                                candlePriceMinFactor = candlePriceMinFactor +
                                        Math.max((candleIntervalUpDownDataPrev.maxClose - candleIntervalUpDownDataPrev.minClose)
                                                / (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose),
                                        (candleIntervalUpDownDataPrevPrev.maxClose - candleIntervalUpDownDataPrevPrev.minClose)
                                                / (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose)
                                        )
                                ;
                                annotation += " new 2 candlePriceMinFactor = " + candlePriceMinFactor;
                                annotation += " new candlePriceMaxFactor = " + candlePriceMaxFactor;
                            } else if (avgPercentPrev > 5f
                                    //&& minPercentPrev > 0f
                                //&& maxPercent > 5
                            ) {
                                isIntervalDown = true;
                                if (maxPercent > 0f) {
                                    candlePriceMinFactor = Math.min(candlePriceMinFactor, 1f + ((avgPercentPrev + avgPercent) / 2 - 15f) / 100f);
                                } else {
                                    //if (maxPercent > 5f && maxPercentPrev > 0f) {
                                    //    candlePriceMinFactor = 1f;
                                    //} else {
                                    candlePriceMinFactor = 1f + ((avgPercentPrev + avgPercent) / 2 - 15f) / 100f;
                                    //}
                                }
                                annotation += " new 3 candlePriceMinFactor = " + candlePriceMinFactor;
                                if (
                                        maxPercent < 0f
                                ) {
                                    candlePriceMaxFactor = 1f - maxPercent / 100f;
                                    annotation += " new candlePriceMaxFactor = " + candlePriceMaxFactor;
                                }
                            } else if (avgPercent > 5f) {
                                isIntervalDown = true;
                                if (maxPercentPrev > 0f) {
                                    candlePriceMinFactor = 1f + (avgPercent - 15f) / 100f;
                                } else {
                                    candlePriceMinFactor = 1f + ((avgPercentPrev + avgPercent) / 2 - 15f) / 100f;
                                }
                                annotation += " new 4 candlePriceMinFactor = " + candlePriceMinFactor;
                                if (
                                        maxPercentPrev < 0f
                                ) {
                                    candlePriceMaxFactor = 1f - maxPercent / 100f;
                                    annotation += " new candlePriceMaxFactor = " + candlePriceMaxFactor;
                                }
                            } else {
                                if (
                                        (maxPercentPrev + 5f) < 0
                                        && (maxPercentPrev + 5f) < minPercentPrev
                                ) {
                                    //isIntervalDown = true;
                                    candlePriceMinFactor = candlePriceMinFactor - (minPercentPrev + 5f) / 100f;
                                    annotation += " new 5 candlePriceMinFactor = " + candlePriceMinFactor;
                                } else if (
                                        minPercent < -5f
                                        && maxPercent < -5f
                                        && minPercent < maxPercent
                                ) {
                                    isIntervalDown = true;
                                    candlePriceMinFactor = 1f - minPercent / 100f;
                                    candleMinFactorCandle = 0.1f;
                                    annotation += " new 6 candlePriceMinFactor = " + candlePriceMinFactor;
                                }
                            }
                        }
                        annotation += " isIntervalDown = " + isIntervalDown;
                        annotation += " isIntervalUp = " + isIntervalUp;
                        setOrderInfo(strategy, candle, isIntervalUp, candleIntervalUpDownData, isIntervalUpRes);

                        var PointLengthOk = false;
                        var PointLengthOkRes = false;
                        var isMinMin = false;
                        if (
                                buyCriteria.getCandleDownPointSize() != null
                                && candleIntervalRes.res
                        ) {
                            var points = getIntervalBuyPoints(
                                    newStrategy,
                                    candle,
                                    candleIntervalRes,
                                    1,
                                    1
                            );
                            annotation += " PointSize " + points.size() + " - " + (points.size() > 0 ? points.get(0).size() : 0);
                            if (
                                    points.size() > 0
                                    && points.get(0).size() >= buyCriteria.getCandleDownPointSize()
                            ) {
                                var prevDownCandle = points.get(0).get(Math.min(points.get(0).size() - 1, buyCriteria.getCandleDownPointSize() - 1)).candle;
                                annotation += " " + printPrice(points.get(0).get(0).candle.getClosingPrice()) + " >= " + printPrice(prevDownCandle.getClosingPrice());
                                if (points.get(0).get(0).candle.getClosingPrice().compareTo(prevDownCandle.getClosingPrice()) >= 0) {
                                    annotation += " OK";
                                    PointLengthOk = true;
                                }
                            }
                        }
                        if (
                                !PointLengthOk
                                && candleIntervalRes.res
                                && buyCriteria.getCandleDownPointLength() != null
                        ) {
                            var points = getIntervalBuyPoints(
                                    newStrategy,
                                    candle,
                                    candleIntervalRes,
                                    buyCriteria.getCandleDownPointPointLength(),
                                    1
                            );
                            annotation += " PointLength " + points.size() + " - " + (points.size() > 0 ? points.get(0).size() : 0);
                            if (points.size() > 0 && points.get(0).size() >= buyCriteria.getCandleDownPointPointLengthSize()) {
                                var pointCandles = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                                        candle.getFigi(),
                                        points.get(0).get(points.get(0).size() - 1).candle.getDateTime(),
                                        points.get(0).get(0).candle.getDateTime(),
                                        strategy.getInterval()
                                    );
                                annotation += " length " + pointCandles.size();
                                if (pointCandles.size() < buyCriteria.getCandleDownPointLength()) {
                                    var prevDownCandle = points.get(points.size() - 1).get(points.get(0).size() - 1).candle;
                                        annotation += " " + printPrice(points.get(0).get(0).candle.getClosingPrice()) + " >= " + printPrice(prevDownCandle.getClosingPrice());
                                    if (points.get(0).get(0).candle.getClosingPrice().compareTo(prevDownCandle.getClosingPrice()) >= 0) {
                                        annotation += " OK";
                                        PointLengthOk = true;
                                    }
                                }
                            }
                        }
                        setOrderBooleanData(strategy, candle, "isPointLength", PointLengthOk);
                        if (
                                isIntervalDown
                                && PointLengthOk
                                && order != null
                                && order.getPurchasePrice().compareTo(order.getSellPrice()) > 0
                        ) {
                            annotation += " SKIP PointLength";
                            PointLengthOk = false;
                        }
                        res = false;
                        if (
                                candleIntervalRes.res
                                && (factor > buyCriteria.getCandleMinFactor() || factor2 > buyCriteria.getCandleMinFactor())
                                        && (factor < buyCriteria.getCandleMaxFactor() || factor2 < buyCriteria.getCandleMaxFactor())
                                        && (buyCriteria.getCandleProfitMinPercent() == null || profitPercent > buyCriteria.getCandleProfitMinPercent())
                        ) {
                            annotation += " candleFactor try";
                            if (factorCandle < buyCriteria.getCandleMaxFactor()
                                    && factorCandle > candleMinFactorCandle
                                    && factorPrice < buyCriteria.getCandleMaxFactor()
                                    && factorPrice > candlePriceMinFactor
                                    && factorPrice < candlePriceMaxFactor) {
                                annotation += " OK";
                                res = true;
                            } else if (PointLengthOk) {
                                    annotation += " PointLength OK";
                                    res = true;
                            }
                        } else if (
                                candleIntervalRes.res
                                && buyCriteria.getCandlePriceMinMaxFactor() != null
                                        && ((factor2 < buyCriteria.getCandlePriceMinMaxFactor()
                                        && factor2 > buyCriteria.getCandlePriceMinMinFactor())
                                        || (factor < buyCriteria.getCandlePriceMinMaxFactor()
                                        && factor > buyCriteria.getCandlePriceMinMinFactor()))
                                        && (!isIntervalDown || isIntervalUp)
                        ) {
                            var isBoth = ((factor2 < buyCriteria.getCandlePriceMinMaxFactor()
                                    && factor2 > buyCriteria.getCandlePriceMinMinFactor())
                                    && (factor < buyCriteria.getCandlePriceMinMaxFactor()
                                    && factor > buyCriteria.getCandlePriceMinMinFactor()));
                            var f2 = buyCriteria.getCandleProfitMinPercent() / Math.sqrt(factorPrice);
                            if (factor2 < buyCriteria.getCandlePriceMinMaxFactor()
                                    && factor2 > buyCriteria.getCandlePriceMinMinFactor()) {
                                f2 = buyCriteria.getCandleProfitMinPercent() / Math.sqrt(factor2);
                            }
                            annotation += " minmin f > " + printPrice(f2);
                            var isOk = true;
                            if (buyCriteria.getCandleDownMinMinMaxLength() != null) {
                                var points = getIntervalBuyPoints(
                                        newStrategy,
                                        candle,
                                        candleIntervalRes,
                                        buyCriteria.getCandleDownMinMinPointLength(),
                                        buyCriteria.getCandleDownMinMinMaxLength()
                                );
                                if (!isBoth && points.size() <= buyCriteria.getCandleDownMinMinMaxLength()) {
                                    annotation += " SKIP by size: " + points.size() + "<=" + buyCriteria.getCandleDownMinMinMaxLength();
                                    isOk = false;
                                }
                            }
                            if (buyCriteria.getCandleDownPointSize() != null) {
                                if (PointLengthOk) {
                                    isOk = true;
                                } else {
                                    annotation += " SKIP by size PointLength";
                                    isOk = false;
                                }
                            }
                            if (
                                    !isOk
                                    && null != candleIntervalUpDownDataPrev
                                    && null != candleIntervalUpDownDataPrev.maxClose
                            ) {
                                double max = candleIntervalUpDownDataPrev.maxClose;
                                max += (candleIntervalUpDownData.maxClose - candleIntervalUpDownDataPrev.maxClose) * 0.25;
                                annotation += " " + printPrice(candle.getClosingPrice()) + " < " + printPrice(max);
                                if (
                                        max > candle.getClosingPrice().doubleValue()
                                        && (buyCriteria.getCandleMinFactorCandle() == null || factorCandle < buyCriteria.getCandleMinFactorCandle())
                                ) {
                                    annotation += " OK by maxClose";
                                    isOk = true;
                                }
                            }
                            if (isOk
                                && (buyCriteria.getCandleProfitMinPercent() == null
                                    || profitPercent > f2)) {
                                annotation += " candleFactor minmin OK";
                                res = true;
                                isMinMin = true;
                                setOrderBooleanData(strategy, candle, "isMinMin", true);
                            }
                        } else if (PointLengthOk) {
                            annotation += " PointLength OK";
                            res = true;
                            PointLengthOkRes = true;
                        }
                        if (isIntervalUp) {
                            res = false;
                            annotation += " isIntervalUp TRY";
                            if (candleIntervalRes.res) {
                                if (buyCriteria.getCandleUpPointLength() != null) {
                                    var points = getIntervalAllBuyPoints(
                                            newStrategy,
                                            candle,
                                            candleIntervalUpDownData,
                                            candleIntervalRes,
                                            buyCriteria.getCandleUpPointLength(),
                                            buyCriteria.getCandleUpMinLength()
                                    );
                                    annotation += " size: " + points.size() + ">" + buyCriteria.getCandleUpMinLength();
                                    if (points.size() >= buyCriteria.getCandleUpMinLength()) {
                                        annotation += " OK";
                                        res = true;
                                    }
                                } else {
                                    annotation += " OK";
                                    res = true;
                                }
                            } else if (
                                    candleIntervalResSell.res
                                    && buyCriteria.getCandleUpSellPointLength() != null
                                    && (order == null || order.getSellDateTime().isBefore(candleIntervalUpDownData.endPost.candle.getDateTime()))
                            ) {
                                var points = getIntervalSellPoints(
                                        newStrategy,
                                        candle,
                                        candleIntervalResSell,
                                        buyCriteria.getCandleUpSellPointLength(),
                                        buyCriteria.getCandleUpSellMinLength()
                                );
                                var prevCandle = points.get(points.size() - 1).candle;
                                var maxUpPrice = candleIntervalUpDownData.maxClose + (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) * 0.33f;
                                annotation += " sell size: " + points.size() + ">" + buyCriteria.getCandleUpSellMinLength();
                                annotation += " prevCandle=" + printDateTime(prevCandle.getDateTime());
                                annotation += " maxUpPrice=" + printPrice(maxUpPrice);
                                if (
                                        points.size() >= buyCriteria.getCandleUpSellMinLength()
                                        && candle.getClosingPrice().floatValue() > candleIntervalUpDownData.maxClose
                                        && candle.getClosingPrice().floatValue() < maxUpPrice
                                        && prevCandle.getClosingPrice().floatValue() > candleIntervalUpDownData.maxClose
                                ) {
                                    annotation += " OK";
                                    res = true;
                                }
                            }
                        } else if (buyCriteria.getIsDownWithLimits()) {
                            var intervalPercent = 100f * (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) / Math.abs(candleIntervalUpDownData.minClose);
                            annotation += " intervalPercent=" + printPrice(intervalPercent);
                            if (intervalPercent < buyCriteria.getCandlePriceMinFactor()) {
                                res = false;
                                annotation += " SKIP by < " + printPrice(buyCriteria.getCandlePriceMinFactor());
                            } else {
                                if (intervalPercent > buyCriteria.getCandlePriceMinFactor()) {
                                    intervalPercent = (float) Math.max(buyCriteria.getCandlePriceMinFactor(), Math.sqrt(intervalPercent));
                                    annotation += " new intervalPercent=" + printPrice(intervalPercent);
                                }
                                var takeProfitPriceStart = Math.min(
                                        candleIntervalUpDownData.maxClose,
                                        candle.getClosingPrice().doubleValue() + candle.getClosingPrice().abs().doubleValue() * intervalPercent / 100f
                                );
                                BigDecimal StopLossPrice;
                                if (isMinMin) {
                                    StopLossPrice = BigDecimal.valueOf(candleIntervalUpDownData.minClose);
                                    intervalPercent = 100f * (candleIntervalUpDownData.maxClose - candle.getClosingPrice().floatValue()) / Math.abs(candleIntervalUpDownData.minClose);
                                    annotation += " minmin intervalPercent=" + printPrice(intervalPercent);
                                } else {
                                    StopLossPrice = BigDecimal.valueOf(Math.min(
                                            candleIntervalUpDownData.minClose - Math.abs(candleIntervalUpDownData.minClose) * buyCriteria.getDownStopLossFactor() * intervalPercent / 100f,
                                            candle.getClosingPrice().doubleValue() - Math.abs(buyCriteria.getDownStopLossFactor()) * intervalPercent / 100f
                                    ));
                                }
                                annotation += " takeProfitPriceStart=" + printPrice(takeProfitPriceStart);
                                annotation += " StopLossPrice=" + printPrice(StopLossPrice);
                                setOrderBigDecimalData(strategy, candle, "takeProfitPriceStart", BigDecimal.valueOf(takeProfitPriceStart));
                                setOrderBigDecimalData(strategy, candle, "intervalPercentStep", BigDecimal.valueOf(intervalPercent));
                                setOrderBigDecimalData(strategy, candle, "stopLossPrice", StopLossPrice);
                                setOrderBooleanData(strategy, candle, "isDownWithLimits", true);
                            }
                        }

                        if (!isIntervalUp && buyCriteria.getIsOnlyUp() && !buyCriteria.getIsDownWithLimits()) {
                            annotation += " SKIP NOT UP";
                            res = false;
                        }

                        if (buyCriteria.getNotLossBuyUnderPercent() > 0) {
                            /*List<CandleIntervalResultData> sellPoints = getIntervalSellPoints(
                                    newStrategy,
                                    candle,
                                    candleIntervalRes,
                                    1,
                                    1
                            );*/
                            //if (sellPoints.size() > 0) {
                                //var sellCandle = sellPoints.get(0).candle;
                                //var buyCandle = candleIntervalUpDownData.minCandle;
                                //var sellCandle = candleIntervalUpDownData.maxCandle;
                                upCandles.size();
                                notLossBuyUnderPrice = candleIntervalUpDownData.maxClose
                                        - downCandles.size() * buyCriteria.getNotLossBuyUnderPercent() * (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) / upCandles.size();
                                annotation += " notLossBuyUnderPrice=" + printPrice(notLossBuyUnderPrice);
                                if (
                                        res
                                        && PointLengthOkRes
                                        && notLossBuyUnderPrice < candle.getClosingPrice().floatValue()
                                ) {
                                    annotation += " SKIP by UnderPrice";
                                    res = false;
                                }
                            //}
                        }
                    }
                }
            } else {
                var candleList = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                        candle.getDateTime(), 2, strategy.getFactorialInterval());
                var curHourCandleForFactorial = candleList.get(1);
                if (candleIntervalResSell.res) {
                    String key = buildKeyHour(strategy.getExtName(), candle);
                    addCashedIsBuyValue(key, null);
                    if (strategy.getBuyCriteria().getProfitPercentFromBuyMinPriceLength() > 1) {
                        String keyPrev = buildKeyHour(strategy.getExtName(), curHourCandleForFactorial);
                        addCashedIsBuyValue(keyPrev, null);
                    }

                    if (null != buyCriteria.getCandleOnlyUpLength()) {
                        annotation += " CandleOnlyUpLength=" + buyCriteria.getCandleOnlyUpLength();
                        var intervalCandles = getCandleIntervals(newStrategy, candle);
                        var order = orderService.findLastByFigiAndStrategy(candle.getFigi(), strategy);
                        var upRes = isOrderCandleUp(newStrategy, candle, order, buyCriteria, sellCriteria);
                        if (upRes.res) {
                            annotation += upRes.annotation;
                            order = null;
                        }

                        annotation += candleIntervalUpDownData.annotation;
                        CandleIntervalResultData candleResDown = candleIntervalUpDownData.endPost;
                        Float lastBottomPrice = candleIntervalUpDownData.minClose;
                        Float lastTopPrice = candleIntervalUpDownData.maxClose;
                        Float lastBetweenPrice = lastTopPrice != null && lastBottomPrice != null ? lastTopPrice - lastBottomPrice : null;

                        if (
                                null != candleResDown
                                        && null != order
                                        && candleResDown.getCandle().getDateTime().compareTo(order.getSellDateTime()) > 0
                        ) {
                            annotation += " down after order";
                            order = null;
                        }
                        if (
                                null != intervalCandles
                                        && null != order
                                        && order.getSellProfit().compareTo(BigDecimal.ZERO) > 0
                        ) {
                            List<CandleIntervalResultData> sellPoints = getIntervalSellPoints(
                                    newStrategy,
                                    candle,
                                    candleIntervalResSell,
                                    buyCriteria.getCandleOnlyUpPointLength(),
                                    buyCriteria.getCandleOnlyUpLength()
                            );
                            if (sellPoints.size() > buyCriteria.getCandleOnlyUpLength()) {
                                if (buyCriteria.getCandleOnlyUpBetweenPercent() != null) {
                                    if (order != null) {
                                        var percentOrderFromDown = 100f * (order.getSellPrice().floatValue() - order.getPurchasePrice().floatValue()) / order.getPurchasePrice().abs().floatValue();
                                        Float priceFromDown = null;
                                        if (
                                                null != lastBottomPrice
                                            //&& candleResDown.getCandle().getClosingPrice().compareTo(order.getPurchasePrice()) < 0
                                        ) {
                                            priceFromDown = candle.getClosingPrice().floatValue() - lastBottomPrice;
                                            annotation += " candleResDown = " + printPrice(priceFromDown);
                                            percentOrderFromDown = 100f * (order.getSellPrice().floatValue() - candleResDown.getCandle().getClosingPrice().floatValue())
                                                    / candleResDown.getCandle().getClosingPrice().abs().floatValue();
                                        }
                                        //var percent = 100f * (candle.getClosingPrice().floatValue() - order.getSellPrice().floatValue()) / candle.getClosingPrice().floatValue();
                                        //annotation += " percentB = " + printPrice(percent) + " > " + printPrice(percentOrderFromDown);
                                        var percent = 100f * (candle.getClosingPrice().floatValue() - lastTopPrice) / Math.abs(lastTopPrice);
                                        annotation += " percentB = " + printPrice(percent) + " > " + printPrice(sellCriteria.getCandleProfitMinPercent());
                                        var upSize = intervalCandles.stream().filter(ic ->
                                                !ic.isDown
                                                        && ic.getCandle().getDateTime().isAfter(candleResDown.getCandle().getDateTime())
                                                        && ic.getCandle().getClosingPrice().floatValue() > lastTopPrice
                                        ).count() + 1;
                                        annotation += " upSize=" + upSize;
                                        var priceFromTop = candle.getClosingPrice().floatValue() - lastTopPrice;
                                        annotation += " priceFromTop=" + priceFromTop;
                                        annotation += " MinFactorPrice=" + printPrice(buyCriteria.getCandleUpMinFactor() * lastBetweenPrice);
                                        var candleSizePercent = 100f * (lastTopPrice - lastBottomPrice) / Math.abs(lastBottomPrice);
                                        annotation += " candleSizePercent: " + printPrice(candleSizePercent) + " > " + printPrice(buyCriteria.getCandleOnlyUpBetweenPercent());
                                        if (true
                                                //&& percent > sellCriteria.getCandleProfitMinPercent()
                                                && percent > 0
                                                && ((buyCriteria.getCandleUpSkipLength() == null || upSize > buyCriteria.getCandleUpSkipLength())
                                                || ((buyCriteria.getCandleUpSkipLength() == null && buyCriteria.getCandleMinFactor() == null)
                                                || (buyCriteria.getCandleUpMinFactor() * lastBetweenPrice < priceFromTop
                                                && percent > sellCriteria.getCandleProfitMinPercent()
                                        )))
                                                && (lastTopPrice == null || lastTopPrice < candle.getClosingPrice().floatValue())
                                                && (lastBetweenPrice == null || priceFromDown == null || lastBetweenPrice < priceFromDown)
                                                && (lastBetweenPrice == null || priceFromDown == null || buyCriteria.getCandleUpMaxFactor() == null || buyCriteria.getCandleUpMaxFactor() * lastBetweenPrice > priceFromDown)
                                        ) {
                                            annotation += " candle UP OK";
                                            var resBetween = true;
                                            for (var iC = 0; iC < buyCriteria.getCandleOnlyUpLength() - 1 && null != buyCriteria.getCandleOnlyUpBetweenPointsPercent(); iC++) {
                                                var pointUp = sellPoints.get(iC);
                                                var pointDown = sellPoints.get(iC + 1);
                                                if (pointUp.candle.getClosingPrice().compareTo(pointDown.candle.getClosingPrice()) < 0) {
                                                    resBetween = false;
                                                    annotation += " break down: " + printPrice(pointUp.candle.getClosingPrice()) + "(" + printDateTime(pointUp.candle.getDateTime()) + ") >= "
                                                            + printPrice(pointDown.candle.getClosingPrice()) + "(" + printDateTime(pointDown.candle.getDateTime()) + ")";
                                                    break;
                                                }
                                                annotation += " point i" + iC + ": " + printDateTime(pointDown.getCandle().getDateTime())
                                                        + " - " + printDateTime(pointUp.getCandle().getDateTime());
                                                var candlesBetween = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                                                        candle.getFigi(),
                                                        pointDown.getCandle().getDateTime(),
                                                        pointUp.getCandle().getDateTime(),
                                                        strategy.getInterval()
                                                );
                                                var minPrice = candlesBetween.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).min().orElse(-1);
                                                var percentMin = 100f * (pointDown.getCandle().getClosingPrice().doubleValue() - minPrice) / Math.abs(minPrice);
                                                annotation += "percentMin = " + printPrice(percentMin) + " < " + printPrice(buyCriteria.getCandleOnlyUpBetweenPointsPercent());
                                                if (percentMin > buyCriteria.getCandleOnlyUpBetweenPointsPercent()) {
                                                    resBetween = false;
                                                    annotation += " FALSE";
                                                    break;
                                                }
                                            }
                                            if (resBetween) {
                                                res = true;
                                                annotation += " candle UP OK BETWEEN";
                                            }
                                        }
                                    }
                                } else {
                                    annotation += " candle UP OK";
                                    res = true;
                                }
                            }
                        }
                    }
                } else {
                    annotation += " SELL false: " + candleIntervalResSell.annotation;
                    if (buyCriteria.getIsCandleUpAny()
                            && candleIntervalUpDownData.maxClose != null
                            && candleIntervalUpDownData.maxClose < candle.getClosingPrice().floatValue()
                    ) {
                        var priceFromTop = candle.getClosingPrice().floatValue() - candleIntervalUpDownData.maxClose;
                        annotation += " priceFromTop=" + priceFromTop;
                        var minFactorPrice = buyCriteria.getCandleUpMinFactorAny() * (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose);
                        var maxFactorPrice = buyCriteria.getCandleUpMaxFactorAny() * (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose);
                        annotation += " minFactorPrice=" + printPrice(minFactorPrice);
                        annotation += " maxFactorPrice=" + printPrice(maxFactorPrice);
                        var order = orderService.findLastByFigiAndStrategy(candle.getFigi(), strategy);
                        var upRes = isOrderCandleUp(newStrategy, candle, order, buyCriteria, sellCriteria);
                        if (
                                priceFromTop > minFactorPrice
                                        && priceFromTop < maxFactorPrice
                                        && (order == null && !upRes.res)
                                        && (order == null || order.getSellProfit().compareTo(BigDecimal.ZERO) > 0)
                        ) {
                            annotation += " candle UP ANY";
                            res = true;
                        }
                    }
                }
            }
            if (candleIntervalResSell != null && candleIntervalResSell.res) {
                addCandleInterval(keyCandles, candleIntervalResSell);
            }
        }
        return CandleIntervalBuyResult.builder()
                .candleIntervalSell(candleIntervalSell)
                .candleIntervalBuy(candleIntervalBuy)
                .res(res)
                .annotation(annotation)
                .candleIntervalUpDownData(candleIntervalUpDownData)
                .candlePriceMaxFactor(candlePriceMaxFactor)
                .candlePriceMinFactor(candlePriceMinFactor)
                .notLossBuyUnderPrice(notLossBuyUnderPrice)
                .isIntervalUp(isIntervalUp)
                .build();
    }

    private void setOrderInfo(
            FactorialDiffAvgAdapterStrategy strategy,
            CandleDomainEntity candle,
            Boolean isIntervalUp,
            CandleIntervalUpDownData candleIntervalUpDownData,
            CandleIntervalUpResult isIntervalUpRes
    ) {
        var buyCriteria = strategy.getBuyCriteria();

        var stopLossPrice = BigDecimal.ZERO;
        //var stopLossPriceBottom = BigDecimal.ZERO;
        if (isIntervalUpRes != null && isIntervalUpRes.stopLossPrice != null && !isIntervalUpRes.stopLossPrice.equals(BigDecimal.ZERO)) {
            stopLossPrice = isIntervalUpRes.stopLossPrice;
            //stopLossPriceBottom = isIntervalUpRes.stopLossPriceBottom;
            setOrderBigDecimalData(strategy, candle, "stopLossPrice", isIntervalUpRes.stopLossPrice);
            setOrderBigDecimalData(strategy, candle, "stopLossPriceBottom", isIntervalUpRes.stopLossPriceBottom);
        }
        var isSetProfit = false;
        if (isIntervalUpRes != null && isIntervalUpRes.takeProfitPriceStart != null) {
            isSetProfit = true;
            setOrderBigDecimalData(strategy, candle, "takeProfitPriceStart", isIntervalUpRes.takeProfitPriceStart);
            setOrderBigDecimalData(strategy, candle, "takeProfitPriceStep", isIntervalUpRes.takeProfitPriceStep);
            var intervalPercentStep = 100f * (isIntervalUpRes.takeProfitPriceStep.floatValue()) / isIntervalUpRes.takeProfitPriceStart.abs().floatValue();
            setOrderBigDecimalData(strategy, candle, "intervalPercentStep", BigDecimal.valueOf(intervalPercentStep));
        } else {
            setOrderBigDecimalData(strategy, candle, "takeProfitPriceStart", BigDecimal.ZERO);
            setOrderBigDecimalData(strategy, candle, "takeProfitPriceStep", BigDecimal.ZERO);
            setOrderBigDecimalData(strategy, candle, "intervalPercentStep", BigDecimal.ZERO);
        }

        setOrderBooleanData(strategy, candle, "isIntervalUp", isIntervalUp);
        setOrderBooleanData(strategy, candle, "isMinMin", false);
        setOrderBooleanData(strategy, candle, "isDownWithLimits", false);
        setOrderBigDecimalData(strategy, candle, "maxBuyIntervalPrice", BigDecimal.valueOf(candleIntervalUpDownData.maxClose));
        var intervalPercentNear = 100f * (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) / Math.abs(candleIntervalUpDownData.minClose);
        if (intervalPercentNear < buyCriteria.getCandlePriceMinFactor()) {
            intervalPercentNear = buyCriteria.getCandlePriceMinFactor();
        }
        setOrderBigDecimalData(strategy, candle, "intervalPercentNear", BigDecimal.valueOf(intervalPercentNear));
        setOrderBigDecimalData(strategy, candle, "intervalPercentNearDown", BigDecimal.valueOf(intervalPercentNear));
        if (candle.getClosingPrice().doubleValue() > candleIntervalUpDownData.maxClose) {
            var intervalPercentNearDown = 100f * (candle.getClosingPrice().doubleValue() - candleIntervalUpDownData.minClose) / Math.abs(candleIntervalUpDownData.minClose);
            if (intervalPercentNearDown < buyCriteria.getCandlePriceMinFactor()) {
                intervalPercentNearDown = buyCriteria.getCandlePriceMinFactor();
            }
            setOrderBigDecimalData(strategy, candle, "intervalPercentNearDown", BigDecimal.valueOf(intervalPercentNearDown));
        }
        if (!isSetProfit && strategy.getBuyCriteria().getPrevLengthSearchTakeProfit() > 0) {
            var candleIntervalUpDownDataCur = candleIntervalUpDownData;
            var takeProfit = candleIntervalUpDownData.maxClose;
            for(var i = 0; i < strategy.getBuyCriteria().getPrevLengthSearchTakeProfit(); i++) {
                var candleIntervalUpDownDataPrev = getPrevCandleIntervalUpDownData(strategy, candleIntervalUpDownDataCur);
                if (candleIntervalUpDownDataPrev.maxClose == null) {
                    break;
                }
                if (candleIntervalUpDownDataPrev.maxClose > takeProfit) {
                    takeProfit = candleIntervalUpDownDataPrev.maxClose;
                }
                candleIntervalUpDownDataCur = candleIntervalUpDownDataPrev;
            }
            setOrderBigDecimalData(strategy, candle, "takeProfitForStep", BigDecimal.valueOf(takeProfit));
            if (takeProfit > candle.getClosingPrice().floatValue()) {
                var takeProfitPriceStart = candle.getClosingPrice().floatValue() + (takeProfit - candle.getClosingPrice().floatValue()) / 2;
                var intervalPercentStep = 100f * (takeProfit - takeProfitPriceStart) / Math.abs(takeProfitPriceStart);
                setOrderBigDecimalData(strategy, candle, "takeProfitForStepPercentStep", BigDecimal.valueOf(intervalPercentStep));
                if (intervalPercentStep > buyCriteria.getCandlePriceMinFactor()) {
                    setOrderBigDecimalData(strategy, candle, "takeProfitPriceStart", BigDecimal.valueOf(takeProfitPriceStart));
                    setOrderBigDecimalData(strategy, candle, "intervalPercentStep", BigDecimal.valueOf(intervalPercentStep));
                    isSetProfit = true;
                }
            }
            if (!isSetProfit && !stopLossPrice.equals(BigDecimal.ZERO) && stopLossPrice.compareTo(candle.getClosingPrice()) < 0) {
                takeProfit = candle.getClosingPrice().floatValue() + (candle.getClosingPrice().floatValue() - stopLossPrice.floatValue());
                setOrderBigDecimalData(strategy, candle, "takeProfitFromLossForStep", BigDecimal.valueOf(takeProfit));
                if (takeProfit > candle.getClosingPrice().floatValue()) {
                    var takeProfitPriceStart = candle.getClosingPrice().floatValue() + (takeProfit - candle.getClosingPrice().floatValue()) / 2;
                    var intervalPercentStep = 100f * (takeProfit - takeProfitPriceStart) / Math.abs(takeProfitPriceStart);
                    setOrderBigDecimalData(strategy, candle, "takeProfitForStepPercentStep", BigDecimal.valueOf(intervalPercentStep));
                    if (intervalPercentStep > buyCriteria.getCandlePriceMinFactor()) {
                        setOrderBigDecimalData(strategy, candle, "takeProfitPriceStart", BigDecimal.valueOf(takeProfitPriceStart));
                        setOrderBigDecimalData(strategy, candle, "intervalPercentStep", BigDecimal.valueOf(intervalPercentStep));
                    }
                }
            }
        }
    }

    @Builder
    @Data
    public static class CandleIntervalUpResult {
        String annotation;
        Boolean isIntervalUp;
        Boolean isIntervalUpAfterDown;
        Boolean isIntervalUpMayBe;
        BigDecimal stopLossPrice;
        BigDecimal stopLossPriceBottom;
        BigDecimal takeProfitPriceStart;
        BigDecimal takeProfitPriceStep;
        BigDecimal minPercent;
        BigDecimal minPercentPrev;
        BigDecimal maxPercent;
        BigDecimal maxPercentPrev;
        BigDecimal maxBuyPrice;
        BigDecimal minBuyPrice;
    }

    private CandleIntervalUpResult calcIsIntervalUpMaybe(
            CandleDomainEntity candle,
            Order order,
            AInstrumentByFiatFactorialStrategy.BuyCriteria buyCriteria,
            FactorialDiffAvgAdapterStrategy newStrategy,
            CandleIntervalUpDownData candleIntervalUpDownData,
            CandleIntervalUpDownData candleIntervalUpDownDataPrev
    ) {
        if (candleIntervalUpDownDataPrev.minClose == null) {
            return CandleIntervalUpResult.builder()
                    .isIntervalUp(false)
                    .annotation("DataPrev=null")
                    .build();
        }
        var annotation = "";
        var candleIntervalUpDownDataPrevPrev = getPrevCandleIntervalUpDownData(newStrategy, candleIntervalUpDownDataPrev);
        if (candleIntervalUpDownDataPrevPrev.minClose != null) {
            var candleIntervalUpDownDataPrevPrevPrev = getPrevCandleIntervalUpDownData(newStrategy, candleIntervalUpDownDataPrevPrev);
            var isIntervalUpRes = calcIsIntervalUp(
                    candle,
                    order,
                    newStrategy,
                    candleIntervalUpDownData,
                    candleIntervalUpDownDataPrev,
                    candleIntervalUpDownDataPrevPrev,
                    candleIntervalUpDownDataPrevPrevPrev
            );
            annotation += " isIntervalUpRes=" + isIntervalUpRes.isIntervalUp;
            //if (isIntervalUpRes.isIntervalUp) {
                annotation += " new stopLossPrice BY PREV";
                isIntervalUpRes.annotation += annotation + " new stopLossPrice BY PREV";
                //return isIntervalUpRes;
            //} else {
                var candleResDownMaybe = CandleIntervalResultData.builder()
                        .res(true)
                        .candle(candle)
                        .annotation("")
                        .isDown(true)
                        .build();
                if (null != candleResDownMaybe) {
                    annotation += " candleResDownMaybe=" + printDateTime(candleResDownMaybe.getCandle().getDateTime());
                    if (candleResDownMaybe.getCandle().getDateTime().isAfter(candleIntervalUpDownData.endPost.candle.getDateTime()))
                    {
                        var candleIntervalUpDownDataNew = getCurCandleIntervalUpDownData(newStrategy, candle, candleResDownMaybe);
                        annotation += " endPostNew=" + printDateTime(candleIntervalUpDownDataNew.endPost.candle.getDateTime());
                        annotation += " mmCloseNew=" + printPrice(candleIntervalUpDownDataNew.minClose) + "-" + printPrice(candleIntervalUpDownDataNew.maxClose);
                        if (candleIntervalUpDownDataNew.endPost.candle.getDateTime().equals(candleIntervalUpDownData.endPost.candle.getDateTime())) {
                            annotation += candleIntervalUpDownDataNew.annotation;
                            var cList = candleHistoryService.getCandlesByFigiBetweenDateTimes(candle.getFigi(), candleIntervalUpDownData.endPost.candle.getDateTime(), candle.getDateTime(), newStrategy.getInterval());
                            var maxClose = cList.stream().mapToDouble(v -> v.getClosingPrice().max(v.getOpenPrice()).doubleValue()).max().orElse(-1);
                            var minClose = cList.stream().mapToDouble(v -> v.getClosingPrice().min(v.getOpenPrice()).doubleValue()).min().orElse(-1);
                            annotation += " new maxClose=" + printPrice(maxClose);
                            if (maxClose > candleIntervalUpDownData.maxClose) {
                                candleIntervalUpDownDataNew.minClose = (float) minClose;
                                candleIntervalUpDownDataNew.maxClose = (float) maxClose;
                            } else {
                                CandleIntervalUpResult.builder()
                                        .isIntervalUp(false)
                                        .annotation(annotation)
                                        .build();
                            }
                        }
                        var isIntervalUpResNew = calcIsIntervalUp(
                                candle,
                                order,
                                newStrategy,
                                candleIntervalUpDownDataNew,
                                candleIntervalUpDownData,
                                candleIntervalUpDownDataPrev,
                                candleIntervalUpDownDataPrevPrev
                        );
                        isIntervalUpResNew.isIntervalUpMayBe = isIntervalUpResNew.isIntervalUp;
                        annotation += " isIntervalUpResNew=" + isIntervalUpResNew.isIntervalUp + "isIntervalUpResNew";
                        if (isIntervalUpResNew.isIntervalUp) {
                            isIntervalUpResNew.annotation += annotation + " new stopLossPrice BY PREV MAYBE";
                        } else {
                            isIntervalUpResNew.isIntervalUp = isIntervalUpRes.isIntervalUp;
                            isIntervalUpResNew.annotation += annotation + " old UP: " + isIntervalUpRes.annotation;
                        }
                        return isIntervalUpResNew;
                    }
//                }
            }
        }
        return CandleIntervalUpResult.builder()
                .isIntervalUp(false)
                .annotation(annotation)
                .build();
    }

    private CandleIntervalUpResult calcIsIntervalUp(
            CandleDomainEntity candle,
            Order order,
            FactorialDiffAvgAdapterStrategy newStrategy,
            CandleIntervalUpDownData candleIntervalUpDownData,
            CandleIntervalUpDownData candleIntervalUpDownDataPrev,
            CandleIntervalUpDownData candleIntervalUpDownDataPrevPrev,
            CandleIntervalUpDownData candleIntervalUpDownDataPrevPrevPrev
    ) {
        var isIntervalUp = false;
        var isIntervalUpAfterAllDown = false;
        var isIntervalUpAfterDown = false;
        var isIntervalUpMinMax = false;
        var annotation = "";
        var buyCriteria = newStrategy.getBuyCriteria();
        BigDecimal StopLossPrice = BigDecimal.ZERO;
        var size = Math.max(
                candleIntervalUpDownDataPrev.maxClose - candleIntervalUpDownDataPrev.minClose,
                candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose
        );
        var sizePrev = Math.max(
                candleIntervalUpDownDataPrev.maxClose - candleIntervalUpDownDataPrev.minClose,
                candleIntervalUpDownDataPrevPrev.maxClose - candleIntervalUpDownDataPrevPrev.minClose
        );
        var minPercent = 100f * (candleIntervalUpDownDataPrev.minClose - candleIntervalUpDownData.minClose) / size;
        var minPercentPrev = 100f * (candleIntervalUpDownDataPrevPrev.minClose - candleIntervalUpDownDataPrev.minClose) / sizePrev;
        var maxPercentPrev = 100f * (candleIntervalUpDownDataPrev.maxClose - candleIntervalUpDownDataPrevPrev.maxClose) / sizePrev;
        var maxPercent = 100f * (candleIntervalUpDownData.maxClose - candleIntervalUpDownDataPrev.maxClose) / size;
        var maxBuyPrice = BigDecimal.ZERO;
        var minBuyPrice = BigDecimal.ZERO;
        annotation += " minPercent=" + printPrice(minPercent);
        annotation += " maxPercent=" + printPrice(maxPercent);
        annotation += " minPercentPrev=" + printPrice(minPercentPrev);
        annotation += " maxPercentPrev=" + printPrice(maxPercentPrev);
        annotation += " prevPrevDown=" + printPrice((candleIntervalUpDownDataPrevPrev.minClose - candleIntervalUpDownDataPrev.maxClose) / size);
        if (
                minPercent > 0f
                        && maxPercent > 0f
                        && (maxPercent > minPercent
                            || (candleIntervalUpDownDataPrevPrev.minClose > candleIntervalUpDownDataPrev.maxClose
                                && (candleIntervalUpDownDataPrevPrev.minClose - candleIntervalUpDownDataPrev.maxClose) / size > (minPercent - maxPercent)
                            ))
                        && candleIntervalUpDownDataPrevPrev.minClose >= candleIntervalUpDownDataPrev.minClose
                        && candleIntervalUpDownDataPrevPrev.maxClose >= candleIntervalUpDownDataPrev.maxClose
                        && (
                        candleIntervalUpDownDataPrevPrev.minClose > candleIntervalUpDownDataPrev.minClose
                                || candleIntervalUpDownDataPrevPrev.maxClose > candleIntervalUpDownDataPrev.maxClose
                )
        ) {
            annotation += " up by big size after down";
            isIntervalUp = true;
            isIntervalUpAfterAllDown = true;
            if (
                    candleIntervalUpDownDataPrevPrev.maxClose > candleIntervalUpDownData.maxClose
                            || (candleIntervalUpDownDataPrevPrevPrev.maxClose != null
                            && candleIntervalUpDownDataPrevPrevPrev.maxClose > candleIntervalUpDownData.maxClose)
            ) {
                isIntervalUpAfterDown = true;
            }
        } else if (
                minPercent < 5f
                && maxPercent > 5f
                && minPercentPrev > 0f
                && maxPercentPrev > 0f
        ) {
            annotation += " up after big size";
            isIntervalUp = true;
            isIntervalUpAfterAllDown = true;
            isIntervalUpAfterDown = true;
            minBuyPrice = BigDecimal.valueOf(candleIntervalUpDownData.minClose);
            maxBuyPrice = BigDecimal.valueOf(candleIntervalUpDownDataPrev.maxClose);
            isIntervalUpMinMax = true;
        } else if (
                minPercent < -15f
                        && maxPercent > 15f
                        && candleIntervalUpDownDataPrevPrev.minClose > candleIntervalUpDownDataPrev.maxClose
                        && candleIntervalUpDownDataPrevPrev.minClose > candleIntervalUpDownData.maxClose
        ) {
            annotation += " up by big down";
            isIntervalUp = true;
        } else if (false && maxPercent > 0f
                && candleIntervalUpDownDataPrevPrev.minClose < candleIntervalUpDownDataPrev.minClose
                && candleIntervalUpDownDataPrevPrev.maxClose > candleIntervalUpDownData.maxClose
        ) {
            isIntervalUp = true;
        } else if (maxPercent > 0f
                && minPercent <= 0f
                && candleIntervalUpDownDataPrevPrev.minClose <= candleIntervalUpDownDataPrev.minClose
                && candleIntervalUpDownDataPrevPrev.maxClose < candleIntervalUpDownData.maxClose
                && (
                minPercent < 0
                        || candleIntervalUpDownDataPrevPrev.minClose < candleIntervalUpDownDataPrev.minClose
        )
        ) {
            annotation += " up by up";
            isIntervalUp = true;
            minBuyPrice = BigDecimal.valueOf(candleIntervalUpDownData.minClose);
        } else if (
                minPercent > 0f
                && maxPercent > 0f
                //&& maxPercent > minPercent
                && candleIntervalUpDownDataPrevPrev.maxClose > candleIntervalUpDownDataPrev.maxClose
        ) {
            annotation += " PPMaxCandle: " + printDateTime(candleIntervalUpDownDataPrevPrev.beginDownFirst.candle.getDateTime());
            annotation += " PPPrev.min-maxCandle=" + printPrice(candleIntervalUpDownDataPrevPrevPrev.minClose) + "-" +printPrice(candleIntervalUpDownDataPrevPrevPrev.maxClose) ;
            var candleIntervalUpDownDataPPPP = getPrevCandleIntervalUpDownData(newStrategy, candleIntervalUpDownDataPrevPrevPrev);
            if (
                    candleIntervalUpDownDataPrevPrevPrev.maxClose != null
                            && (candleIntervalUpDownDataPrevPrevPrev.maxClose > candleIntervalUpDownDataPrevPrev.maxClose
                                || (candleIntervalUpDownDataPPPP.maxClose != null && candleIntervalUpDownDataPPPP.maxClose > candleIntervalUpDownDataPrevPrev.maxClose))
                            && candleIntervalUpDownDataPrevPrevPrev.minClose > candleIntervalUpDownDataPrev.minClose
                            && (maxPercent > minPercent || candle.getClosingPrice().floatValue() > candleIntervalUpDownDataPrevPrevPrev.maxClose || candleIntervalUpDownDataPrevPrevPrev.minClose < candleIntervalUpDownData.minClose)
            ) {
                annotation += " up by down size1";
                isIntervalUp = true;
                isIntervalUpAfterDown = true;
                StopLossPrice = BigDecimal.valueOf(Math.min(
                        candleIntervalUpDownData.minClose,
                        candleIntervalUpDownDataPrevPrev.minClose - (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) * 1.f
                ));
            }
            if (
                    !isIntervalUp
                    && candleIntervalUpDownDataPrevPrevPrev.maxClose != null
                    && candleIntervalUpDownDataPrevPrev.minClose < candleIntervalUpDownDataPrev.minClose
                    && candleIntervalUpDownDataPrevPrevPrev.maxClose > candleIntervalUpDownDataPrevPrev.maxClose
                    && candleIntervalUpDownDataPrevPrevPrev.minClose < candleIntervalUpDownDataPrevPrev.minClose
                    && (maxPercent > minPercent || candle.getClosingPrice().floatValue() > candleIntervalUpDownDataPrevPrevPrev.maxClose || candleIntervalUpDownDataPrevPrevPrev.minClose < candleIntervalUpDownData.minClose)
            ) {
                isIntervalUp = true;
                isIntervalUpAfterDown = true;
                annotation += " up by down size2";
            }

            if (
                    !isIntervalUp
                    && false
                    && candleIntervalUpDownDataPrevPrevPrev.maxClose != null
                    && candleIntervalUpDownDataPrevPrevPrev.maxClose < candleIntervalUpDownDataPrevPrev.maxClose
                    && candleIntervalUpDownDataPrevPrev.maxClose > candleIntervalUpDownDataPrev.maxClose
                    && candleIntervalUpDownDataPrevPrev.minClose < candleIntervalUpDownDataPrev.minClose
                    && (maxPercent > minPercent
                            || candle.getClosingPrice().floatValue() > candleIntervalUpDownDataPrevPrev.maxClose
                    )
            ) {
                isIntervalUp = true;
                isIntervalUpAfterDown = true;
                annotation += " up by down size3";
                StopLossPrice = BigDecimal.valueOf(Math.max(
                        candleIntervalUpDownData.minClose,
                        candleIntervalUpDownDataPrevPrev.minClose - (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) * 1.f
                ));
            }
        }
        if (
                (!isIntervalUp || isIntervalUpMinMax || isIntervalUpAfterDown)
                && maxPercent > 0
                && candle.getClosingPrice().floatValue() > candleIntervalUpDownData.maxClose
        ) {
                                    /*var candlesList = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                                            candle.getFigi(),
                                            candleIntervalUpDownData.beginDownFirst.candle.getDateTime(),
                                            candleIntervalUpDownData.endPost.candle.getDateTime(),
                                            strategy.getInterval()
                                    );
                                    var maxCandle = candlesList
                                            .stream().reduce((first, second) ->
                                                    first.getClosingPrice().compareTo(second.getClosingPrice()) > 0 ? first : second).orElse(null);*/
            var maxCandle = candleIntervalUpDownData.maxCandle;
            var maxCandlePrice = candleIntervalUpDownData.maxClose;
            annotation += " maxCandlePrev = " + printDateTime(maxCandle.getDateTime()) + "(" + printPrice(maxCandlePrice) + ")";
            if (maxCandlePrice.floatValue() > candleIntervalUpDownDataPrev.maxClose) {
                annotation += " up by 2 max";
                if (isIntervalUpMinMax) {
                    minBuyPrice = BigDecimal.ZERO;
                    maxBuyPrice = BigDecimal.ZERO;
                }
                isIntervalUp = true;
                isIntervalUpMinMax = false;
                isIntervalUpAfterDown = false;
            }
        }
        isIntervalUpAfterAllDown = isIntervalUpAfterAllDown || isIntervalUpAfterDown;
        if (
                isIntervalUp
                        && order != null
                        && !order.getPurchaseDateTime().isBefore(candleIntervalUpDownData.endPost.candle.getDateTime())
        ) {
            annotation += " isIntervalUp = false by loss";
            isIntervalUp = false;
            StopLossPrice = BigDecimal.ZERO;
        }
        if (
                isIntervalUp
                && !minBuyPrice.equals(BigDecimal.ZERO)
                && candle.getClosingPrice().min(candle.getOpenPrice()).compareTo(minBuyPrice) < 0
        ) {
            annotation += " isIntervalUp = false by min: " + printPrice(minBuyPrice) + "-" + printPrice(maxBuyPrice);
            isIntervalUp = false;
            StopLossPrice = BigDecimal.ZERO;
        }
        if (
                isIntervalUp
                && !maxBuyPrice.equals(BigDecimal.ZERO)
                && candle.getClosingPrice().min(candle.getOpenPrice()).compareTo(maxBuyPrice) > 0
        ) {
            annotation += " isIntervalUp = false by max: " + printPrice(minBuyPrice) + "-" + printPrice(maxBuyPrice);
            isIntervalUp = false;
            StopLossPrice = BigDecimal.ZERO;
        }
        if (
                isIntervalUp
                        && candle.getClosingPrice().min(candle.getOpenPrice()).floatValue() < (candleIntervalUpDownData.minClose
                        - Math.abs(candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) * 0.33f)
        ) {
            annotation += " isIntervalUp = false by under minClose";
            isIntervalUp = false;
            StopLossPrice = BigDecimal.ZERO;
        }
        if (
                isIntervalUp
                && isIntervalUpAfterAllDown
                && candle.getClosingPrice().min(candle.getOpenPrice()).floatValue() < (candleIntervalUpDownData.minClose
                + Math.abs(candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) * 0.382f)
        ) {
            annotation += " isIntervalUp = false by under minCloseF";
            isIntervalUp = false;
            StopLossPrice = BigDecimal.ZERO;
        }
        var isSizeUp = false;
        if (
                isIntervalUp
                        && !isIntervalUpAfterDown
                        && candleIntervalUpDownDataPrev.maxClose < candleIntervalUpDownData.maxClose
                        && (candleIntervalUpDownDataPrevPrev.maxClose < candleIntervalUpDownDataPrev.maxClose
                        || candleIntervalUpDownDataPrevPrev.maxClose < candleIntervalUpDownData.maxClose)
                        //&& candleIntervalUpDownDataPrevPrev.minClose < candleIntervalUpDownData.minClose
        ) {
            var minClosePrev = Math.min(candleIntervalUpDownDataPrev.minClose, candleIntervalUpDownData.minClose);
            var prevHeight = (Math.max(candleIntervalUpDownDataPrev.maxClose, candleIntervalUpDownDataPrevPrev.maxClose) - minClosePrev);
            if (candleIntervalUpDownDataPrev.minClose.equals(candleIntervalUpDownData.minClose)) {
                annotation += " ppEquals";
                prevHeight = (candleIntervalUpDownDataPrevPrev.maxClose - minClosePrev);
            }
            var sizePercentUp = 100f * (candleIntervalUpDownData.maxClose - minClosePrev)
                    / prevHeight;
            var maxPercentUp = 180f;
            annotation += " sizePercentUp=" + sizePercentUp;
            if (sizePercentUp > maxPercentUp) {
                annotation += " isIntervalUp = false by size up";
                isIntervalUp = false;
                StopLossPrice = BigDecimal.ZERO;
                isSizeUp = true;
            }
            if (
                    buyCriteria.getIsUpMaxPercentSeePrevSize() != null
                            && sizePercentUp < buyCriteria.getIsUpMaxPercentSeePrevSize()
            ) {
                minClosePrev = Math.min(candleIntervalUpDownDataPrev.minClose, candleIntervalUpDownDataPrevPrev.minClose);
                var sizePercentUpPrev = 100f * (candleIntervalUpDownDataPrev.maxClose - minClosePrev)
                        / (candleIntervalUpDownDataPrevPrev.maxClose - minClosePrev);
                annotation += " sizePercentUpPrev=" + sizePercentUpPrev;
                sizePercentUpPrev = sizePercentUpPrev * 100f / sizePercentUp;
                annotation += " sizePUPrev=" + sizePercentUpPrev;
                if (sizePercentUpPrev > maxPercentUp || sizePercentUpPrev <= 100f) {
                    annotation += " isIntervalUp = false by size up prev";
                    isIntervalUp = false;
                    StopLossPrice = BigDecimal.ZERO;
                }
            }

            if (
                    candleIntervalUpDownData.minClose > candleIntervalUpDownDataPrev.minClose
                    && candleIntervalUpDownDataPrev.minClose > candleIntervalUpDownDataPrevPrev.minClose
            ) {
                var minDiffP = 100f * (candleIntervalUpDownData.minClose - candleIntervalUpDownDataPrev.minClose) / candleIntervalUpDownDataPrev.minClose;
                var minDiffPPrev = 100f * (candleIntervalUpDownDataPrev.minClose - candleIntervalUpDownDataPrev.minClose) / candleIntervalUpDownDataPrev.minClose;
                if (minDiffP < buyCriteria.getCandlePriceMinFactor()) {
                    minDiffP = buyCriteria.getCandlePriceMinFactor();
                }
                if (minDiffPPrev < buyCriteria.getCandlePriceMinFactor()) {
                    minDiffPPrev = buyCriteria.getCandlePriceMinFactor();
                }
                var minUpPercent = 100f * minDiffP / minDiffPPrev;
                annotation += " minUpPercent=" +printPrice(minUpPercent);
                if (minUpPercent >= buyCriteria.getMaxSizeUpMinClosePercent()) {
                    annotation += " isIntervalUp = false by size min up prev";
                    isIntervalUp = false;
                    StopLossPrice = BigDecimal.ZERO;
                }
            }
        }
        var percent = (100f * (candleIntervalUpDownData.maxClose - candleIntervalUpDownData.minClose) / Math.abs(candleIntervalUpDownData.minClose));
        if (percent < buyCriteria.getCandlePriceMinFactor()) {
            annotation += " percent=" + printPrice(percent) + " < " + printPrice(buyCriteria.getCandlePriceMinFactor());
            percent = buyCriteria.getCandlePriceMinFactor();
        }
        if (StopLossPrice.equals(BigDecimal.ZERO)) {
            StopLossPrice = BigDecimal.valueOf(candleIntervalUpDownData.minClose - Math.abs(candleIntervalUpDownData.minClose) * percent * 2f / 100f);
        }
        annotation += " StopLossPrice=" + printPrice(StopLossPrice);
        StopLossPrice = StopLossPrice.min(
                BigDecimal.valueOf(candle.getClosingPrice().doubleValue() - Math.abs(candleIntervalUpDownData.minClose) * percent * 0.5f / 100f)
        );
        annotation += " StopLossPrice=" + printPrice(StopLossPrice);
        var stopLossPriceBottom = StopLossPrice.subtract(BigDecimal.valueOf(Math.abs(candleIntervalUpDownData.minClose) * percent * 1 / 100f));

        var res = CandleIntervalUpResult.builder()
                .annotation(annotation)
                .isIntervalUp(isIntervalUp)
                .isIntervalUpAfterDown(isIntervalUpAfterDown)
                .stopLossPrice(StopLossPrice)
                .stopLossPriceBottom(stopLossPriceBottom)
                .minPercent(BigDecimal.ZERO)
                .minPercentPrev(BigDecimal.ZERO)
                .maxPercent(BigDecimal.ZERO)
                .maxPercentPrev(BigDecimal.ZERO)
                .minBuyPrice(minBuyPrice)
                .maxBuyPrice(maxBuyPrice)
                .build();

        if (newStrategy.getSellCriteria().getIsMaxPriceByFib()) {
            var candleIntervalUpDownDataCur = candleIntervalUpDownData;
            var maxClose = candleIntervalUpDownDataCur.maxClose;
            var minClose = candleIntervalUpDownDataCur.minClose;
            res.annotation += " beginDownFirst=" + printDateTime(candleIntervalUpDownDataCur.beginDownFirst.candle.getDateTime());
            res.annotation += " minClose=" + printPrice(minClose);
            res.annotation += " maxClose=" + printPrice(maxClose);
            var iDown = 0;
            var iUp = 0;
            var i = 0;
            for (; i < newStrategy.getBuyCriteria().getPrevLengthSearchTakeProfit(); i++) {
                var candleIntervalUpDownDataCurPrev = getPrevCandleIntervalUpDownData(newStrategy, candleIntervalUpDownDataCur);
                if (candleIntervalUpDownDataCurPrev.maxCandle == null) {
                    break;
                }
                res.annotation += " Prev.min-maxCandle=" + printDateTime(candleIntervalUpDownDataCurPrev.beginDownFirst.candle.getDateTime())
                        + "-" + printPrice(candleIntervalUpDownDataCurPrev.minClose) + "-" +printPrice(candleIntervalUpDownDataCurPrev.maxClose);

                if (candleIntervalUpDownDataCur.maxClose < candleIntervalUpDownDataCurPrev.maxClose) {
                    if (maxClose < candleIntervalUpDownDataCurPrev.maxClose) {
                        maxClose = Math.max(maxClose, candleIntervalUpDownDataCurPrev.maxClose);
                    } else {
                        iUp++;
                    }
                    minClose = Math.min(minClose, candleIntervalUpDownDataCurPrev.minClose);
                } else {
                    iDown++;
                    minClose = Math.min(minClose, candleIntervalUpDownDataCurPrev.minClose);
                }
                if (iDown > 2 || iUp > 2) {
                    break;
                }
                res.annotation += " i=" + i;
                res.annotation += " minClose=" + printPrice(minClose);
                res.annotation += " maxClose=" + printPrice(maxClose);
                candleIntervalUpDownDataCur = candleIntervalUpDownDataCurPrev;
            }
            res.annotation += " i=" + i;
            res.annotation += " k=" + iDown;
            res.annotation += " minClose=" + printPrice(minClose);
            res.annotation += " maxClose=" + printPrice(maxClose);
            var sellTrendPrice = minClose + Math.abs(maxClose - minClose) * 0.236;
            res.annotation += " sellTrendPrice=" + printPrice(sellTrendPrice);
            var buyTrendPrice = minClose + Math.abs(maxClose - minClose) * (1 - 0.236);
            res.annotation += " buyTrendPrice=" + printPrice(buyTrendPrice);
            if (isIntervalUpAfterAllDown) {
                buyTrendPrice = minClose + Math.abs(maxClose - minClose) * 0.382;
                res.annotation += " new buyTrendPrice=" + printPrice(buyTrendPrice);
            }
            var minCloseBottom = minClose - Math.abs(maxClose - minClose) * 0.382;

            res.stopLossPrice = BigDecimal.valueOf(minClose);
            res.stopLossPriceBottom = BigDecimal.valueOf(minCloseBottom);
            res.takeProfitPriceStart = BigDecimal.valueOf(minClose);
            res.takeProfitPriceStep = BigDecimal.valueOf(maxClose - minClose).abs();

            res.annotation += " takeProfitPriceStart=" + printPrice(res.takeProfitPriceStart);
            res.annotation += " takeProfitPriceStep=" + printPrice(res.takeProfitPriceStep);
            res.annotation += " new StopLossPrice=" + printPrice(res.getStopLossPrice());
            res.annotation += " new StopLossPriceBottom=" + printPrice(res.getStopLossPriceBottom());

            var candlePrice = candle.getClosingPrice().min(candle.getOpenPrice()).floatValue();
            if (candlePrice < sellTrendPrice) {
                if (isIntervalUpAfterAllDown) {
                    res.annotation += " isIntervalUp = false by under trend";
                    res.isIntervalUp = false;
                    res.isIntervalUpAfterDown = false;
                }
            }
            if (candlePrice > buyTrendPrice) {
                if (isIntervalUpAfterAllDown || (res.isIntervalUp && candlePrice < maxClose)) {
                    res.annotation += " isIntervalUp = false by over trend";
                    res.isIntervalUp = false;
                    res.isIntervalUpAfterDown = false;
                }
                if (isSizeUp && candlePrice > maxClose) {
                    res.annotation += " up by over max";
                    res.isIntervalUp = true;
                }
            }
            var diff = Math.abs(minClose - minCloseBottom);
            res.annotation += " diff=" + printPrice(diff);
            if (candlePrice - diff < minClose) {
                sellTrendPrice = Math.min(minClose, candlePrice) - diff;
                res.stopLossPrice = BigDecimal.valueOf(sellTrendPrice);
                res.stopLossPriceBottom = BigDecimal.valueOf(sellTrendPrice - diff);
                res.annotation += " new StopLossPrice=" + printPrice(res.getStopLossPrice());
                res.annotation += " new StopLossPriceBottom=" + printPrice(res.getStopLossPriceBottom());
            }
        }
        try {
            res.minPercent = BigDecimal.valueOf(minPercent);
            res.minPercentPrev = BigDecimal.valueOf(minPercentPrev);
            res.maxPercent = BigDecimal.valueOf(maxPercent);
            res.maxPercentPrev = BigDecimal.valueOf(maxPercentPrev);
        } catch (NumberFormatException e) {
            log.error("Error in " + candle.getFigi() + " " + candle.getDateTime() + ": "
                    + candleIntervalUpDownDataPrev.minClose + " - " + candleIntervalUpDownData.minClose + " / " +
                    size, e);
        }
        return res;
    }

    private List<CandleIntervalResultData> getIntervalSellPoints(
            FactorialDiffAvgAdapterStrategy strategy,
            CandleDomainEntity candle,
            CandleIntervalResultData lastSellPoint,
            Integer pointLength,
            Integer upLength
    ) {
        var intervalCandles = getCandleIntervals(strategy, candle);
        List<CandleIntervalResultData> sellPoints = new ArrayList<>();
        if (lastSellPoint.res && !lastSellPoint.isDown) {
            sellPoints.add(lastSellPoint);
        } else {
            lastSellPoint = null;
        }
        Integer lastPointI = sellPoints.size() - 1;
        for (var i = intervalCandles.size() - 1; i >= 0; i--) {
            var candleRes = intervalCandles.get(i);
            if (
                    candleRes.isDown
                            //&& sellPoints.size() != upLength
            ) {
                //if (lastSellPoint == null) {
                //    continue;
                //}
                break;
            }
            if (null == lastSellPoint) {
                sellPoints.add(candleRes);
                lastPointI = sellPoints.size() - 1;
            } else {
                var upCandles = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                        candle.getFigi(),
                        candleRes.getCandle().getDateTime(),
                        lastSellPoint.getCandle().getDateTime(),
                        strategy.getInterval()
                );
                if (
                        upCandles.size() > pointLength
                ) {
                    //annotation += " new point = " + lastPointI + ":" + printDateTime(candleRes.getCandle().getDateTime());
                    //if (candleRes.candle.getClosingPrice().compareTo(lastSellPoint.candle.getClosingPrice()) < 0) {
                    sellPoints.add(candleRes);
                    lastPointI = sellPoints.size() - 1;
                } else {
                    sellPoints.set(lastPointI, candleRes);
                }
            }
            lastSellPoint = candleRes;
            if (sellPoints.size() > upLength) {
                break;
            }
        }
        return sellPoints;
    }

    private List<CandleIntervalResultData> getIntervalPoints(
            FactorialDiffAvgAdapterStrategy strategy,
            CandleDomainEntity candle,
            CandleIntervalResultData lastSellPoint,
            Integer pointLength,
            Integer upLength
    ) {
        var intervalCandles = getCandleIntervals(strategy, candle);
        List<CandleIntervalResultData> sellPoints = new ArrayList<>();
        if (lastSellPoint.res) {
            sellPoints.add(lastSellPoint);
        } else {
            lastSellPoint = null;
        }
        Integer lastPointI = sellPoints.size() - 1;
        for (var i = intervalCandles.size() - 1; i >= 0; i--) {
            var candleRes = intervalCandles.get(i);
            if (null == lastSellPoint) {
                sellPoints.add(candleRes);
                lastPointI = sellPoints.size() - 1;
            } else {
                var upCandles = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                        candle.getFigi(),
                        candleRes.getCandle().getDateTime(),
                        lastSellPoint.getCandle().getDateTime(),
                        strategy.getInterval()
                );
                if (
                        upCandles.size() > pointLength
                ) {
                    //annotation += " new point = " + lastPointI + ":" + printDateTime(candleRes.getCandle().getDateTime());
                    //if (candleRes.candle.getClosingPrice().compareTo(lastSellPoint.candle.getClosingPrice()) < 0) {
                    sellPoints.add(candleRes);
                    lastPointI = sellPoints.size() - 1;
                } else {
                    sellPoints.set(lastPointI, candleRes);
                }
            }
            lastSellPoint = candleRes;
            if (sellPoints.size() > upLength) {
                break;
            }
        }
        return sellPoints;
    }

    private List<List<CandleIntervalResultData>> getIntervalBuyPoints(
            FactorialDiffAvgAdapterStrategy strategy,
            CandleDomainEntity candle,
            CandleIntervalResultData lastSellPoint,
            Integer pointLength,
            Integer upLength
    ) {
        var intervalCandles = getCandleIntervals(strategy, candle);
        List<List<CandleIntervalResultData>> sellPoints = new ArrayList<>();
        sellPoints.add(new ArrayList<>());
        Integer lastPointI = sellPoints.size() - 1;
        sellPoints.get(lastPointI).add(lastSellPoint);
        for (var i = intervalCandles.size() - 1; i >= 0; i--) {
            var candleRes = intervalCandles.get(i);
            if (
                    !candleRes.isDown
                            //&& sellPoints.size() != upLength
            ) {
                break;
            }
            var upCandles = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                    candle.getFigi(),
                    candleRes.getCandle().getDateTime(),
                    lastSellPoint.getCandle().getDateTime(),
                    strategy.getInterval()
            );
            if (
                    upCandles.size() > pointLength
            ) {
                //annotation += " new point = " + lastPointI + ":" + printDateTime(candleRes.getCandle().getDateTime());
                //if (candleRes.candle.getClosingPrice().compareTo(lastSellPoint.candle.getClosingPrice()) < 0) {
                if (sellPoints.size() >= upLength) {
                    break;
                }
                sellPoints.add(new ArrayList<>());
                lastPointI = sellPoints.size() - 1;
            }
            if (!lastSellPoint.candle.getDateTime().equals(candleRes.candle.getDateTime())) {
                sellPoints.get(lastPointI).add(candleRes);
            }
            lastSellPoint = candleRes;
        }
        return sellPoints;
    }

    private List<List<CandleIntervalResultData>> getIntervalAllBuyPoints(
            FactorialDiffAvgAdapterStrategy strategy,
            CandleDomainEntity candle,
            CandleIntervalUpDownData candleIntervalUpDownData,
            CandleIntervalResultData lastSellPoint,
            Integer pointLength,
            Integer upLength
    ) {
        var intervalCandles = getCandleIntervals(strategy, candle);
        List<List<CandleIntervalResultData>> sellPoints = new ArrayList<>();
        sellPoints.add(new ArrayList<>());
        Integer lastPointI = sellPoints.size() - 1;
        sellPoints.get(lastPointI).add(lastSellPoint);
        for (var i = intervalCandles.size() - 1; i >= 0; i--) {
            var candleRes = intervalCandles.get(i);
            if (
                    !candleRes.isDown
            ) {
                continue;
            }
            if (candleRes.candle.getDateTime().isBefore(candleIntervalUpDownData.endPost.candle.getDateTime())) {
                break;
            }
            var upCandles = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                    candle.getFigi(),
                    candleRes.getCandle().getDateTime(),
                    lastSellPoint.getCandle().getDateTime(),
                    strategy.getInterval()
            );
            if (
                    upCandles.size() > pointLength
            ) {
                //annotation += " new point = " + lastPointI + ":" + printDateTime(candleRes.getCandle().getDateTime());
                //if (candleRes.candle.getClosingPrice().compareTo(lastSellPoint.candle.getClosingPrice()) < 0) {
                if (sellPoints.size() >= upLength) {
                    break;
                }
                sellPoints.add(new ArrayList<>());
                lastPointI = sellPoints.size() - 1;
            }
            if (!lastSellPoint.candle.getDateTime().equals(candleRes.candle.getDateTime())) {
                sellPoints.get(lastPointI).add(candleRes);
            }
            lastSellPoint = candleRes;
        }
        return sellPoints;
    }

    private Map<String, FactorialData> factorialCashMap = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 1000;
        }
    };

    private synchronized FactorialData getCashedValue(String indent)
    {
        if (factorialCashMap.containsKey(indent)) {
            return factorialCashMap.get(indent);
        }
        if (factorialCashMap.size() > 1000) {
            factorialCashMap.clear();
        }
        return null;
    }

    private synchronized void addCashedValue(String indent, FactorialData v)
    {
        //if (factorialCashMap.size() > 100) {
        //    factorialCashMap.clear();
        //}
        factorialCashMap.put(indent, v);
    }

    private LinkedHashMap<String, BuyData> factorialCashIsBuy = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 1000;
        }
    };

    private String buildKeyHour(String strategyName, CandleDomainEntity candle)
    {
        var curBeginHour = candle.getDateTime();
        curBeginHour = curBeginHour.minusMinutes(curBeginHour.getMinute() + 1);
        String key = strategyName + candle.getFigi() + printDateTime(curBeginHour);
        return key;
    }

    private synchronized BuyData getCashedIsBuyValue(String indent)
    {
        if (factorialCashIsBuy.containsKey(indent)) {
            return factorialCashIsBuy.get(indent);
        }
        return null;
    }

    private synchronized BuyData addCashedIsBuyValue(String indent, BuyData v)
    {
        factorialCashIsBuy.put(indent, v);
        return v;
    }

    private LinkedHashMap<String, List<CandleIntervalResultData>> candleIntervalResult = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 1000;
        }
    };

    private String buildKeyCandleIntervals(AInstrumentByFiatFactorialStrategy strategy, CandleDomainEntity candle)
    {
        String keyCandles = strategy.getExtName() + candle.getFigi();
        return keyCandles;
    }

    private synchronized List<CandleIntervalResultData> getCandleIntervals(FactorialDiffAvgAdapterStrategy strategy, CandleDomainEntity candle)
    {
        String keyCandles = buildKeyCandleIntervals(strategy, candle);
        if (candleIntervalResult.containsKey(keyCandles)) {
            return candleIntervalResult.get(keyCandles);
        }
        var buyCriteria = strategy.getBuyCriteria();
        if (null == buyCriteria.getCandleMaxIntervalLess()) {
            return null;
        }
        var candleIPrev = candleHistoryService.getCandlesByFigiByLength(
                candle.getFigi(),
                candle.getDateTime(),
                buyCriteria.getCandleMaxIntervalLess() * 2,
                buyCriteria.getCandleInterval()
        );
        if (null == candleIPrev) {
            return null;
        }
        Collections.reverse(candleIPrev);
        List<CandleIntervalResultData> results = new ArrayList<>();
        CandleDomainEntity candleStrategyCurHour = null;
        FactorialDiffAvgAdapterStrategy candleStrategy = null;
        var upCount = 0;
        var downCount = 0;
        var upDownCount = 0;
        var prevIsDown = true;
        log.info("{}: Candle Intervals size = {} from {}", keyCandles, candleIPrev.size(), candleIPrev.get(0).getDateTime());
        for (var i = 1; i < candleIPrev.size(); i++) {
            var candleCur = candleIPrev.get(i);
            //log.info("Loading from cash {} {} {} {}", strategy.getExtName(), candleCur.getFigi(), candleCur.getDateTime(), candleCur.getInterval());
            var cashed = candleStrategyResultRepository.findByStrategyAndFigiAndDateTimeAndInterval(strategy.getExtName(), candleCur.getFigi(), candleCur.getDateTime(), candleCur.getInterval());
            if (cashed != null) {
                if (cashed.getDetails().getBooleanDataMap().getOrDefault("res", false)) {
                    CandleIntervalResultData candleIntervalRes = CandleIntervalResultData.builder()
                            .res(true)
                            .candle(candleCur)
                            .isDown(cashed.getDetails().getBooleanDataMap().getOrDefault("isDown", false))
                            .annotation(cashed.getDetails().getAnnotations().getOrDefault("annotation", "empty"))
                            .build();
                    log.info("Cashed {} {} {} OK: {} {}", strategy.getExtName(), candle.getFigi(), candleIntervalRes.isDown ? "Buy" : "Sell", candleCur.getDateTime(), candleIntervalRes.annotation);
                    results.add(candleIntervalRes);
                }
                continue;
            }
            var candleStrategyCurHourI = getCandleHour(strategy, candleCur);
            if (candleStrategyCurHour == null || candleStrategyCurHourI.getDateTime().compareTo(candleStrategyCurHour.getDateTime()) != 0) {
                candleStrategyCurHour = candleStrategyCurHourI;
                candleStrategy = buildAvgStrategy(strategy.getStrategy(), candleCur);
            }
            if (null == candleStrategy) {
                break;
            }
            var sellCriteria = candleStrategy.getSellCriteria();
            var candleIntervalRes = checkCandleInterval(candleCur, sellCriteria);
            if (candleIntervalRes.res) {
                log.info("Sell OK: {} priceDiffAvgReal={} {}", candleCur.getDateTime(), candleStrategy.getPriceDiffAvgReal(), candleIntervalRes.annotation);
            }
            if (!candleIntervalRes.res
                    && sellCriteria.getCandleUpLength() > 1
                    && null != sellCriteria.getCandleTrySimple()
            ) {
                var sellCriteriaSimple = sellCriteria.clone();
                sellCriteriaSimple.setCandleUpLength(sellCriteria.getCandleUpLength() / sellCriteria.getCandleTrySimple());
                sellCriteriaSimple.setCandleIntervalMinPercent(sellCriteria.getCandleIntervalMinPercent() * sellCriteria.getCandleTrySimple());
                candleIntervalRes = checkCandleInterval(candleCur, sellCriteriaSimple);
                if (candleIntervalRes.res) {
                    log.info("Sell OK simple: {} priceDiffAvgReal={} {}", candleCur.getDateTime(), candleStrategy.getPriceDiffAvgReal(), candleIntervalRes.annotation);
                }
            }
            if (candleIntervalRes.res) {
                upCount++;
                if (prevIsDown) {
                    upDownCount++;
                    log.info("{}: Switch to UP in {}", keyCandles, candleCur.getDateTime());
                }
                prevIsDown = false;
            } else {
                candleIntervalRes = checkCandleInterval(candleCur, candleStrategy.getBuyCriteria());
                if (candleIntervalRes.res) {
                    log.info("Buy OK: {} priceDiffAvgReal={} {}", candleCur.getDateTime(), candleStrategy.getPriceDiffAvgReal(), candleIntervalRes.annotation);
                    downCount++;
                    if (!prevIsDown) {
                        upDownCount++;
                        log.info("{}: Switch to DOWN in {}", keyCandles, candleCur.getDateTime());
                    }
                    prevIsDown = true;
                }
            }
            OrderDetails details = OrderDetails.builder()
                    .build();
            details.getBooleanDataMap().put("res", candleIntervalRes.res);
            if (candleIntervalRes.res) {
                results.add(candleIntervalRes);
                details.getAnnotations().put("annotation", candleIntervalRes.annotation);
                details.getBooleanDataMap().put("isDown", candleIntervalRes.isDown);
            }
            CandleStrategyResultEntity candleStrategyResultEntity = CandleStrategyResultEntity.builder()
                    .strategy(strategy.getExtName())
                    .figi(candleCur.getFigi())
                    .dateTime(candleCur.getDateTime())
                    .interval(candleCur.getInterval())
                    .details(details)
                    .build();
            candleStrategyResultRepository.save(candleStrategyResultEntity);
            log.info("Save {} {} {} OK: {}", strategy.getExtName(), candle.getFigi(), candleIntervalRes.isDown ? "Buy" : "Sell", candleCur.getDateTime());
            if (
                    //results.size() > 1500 ||
                    (
                    upCount > buyCriteria.getCandleUpDownSkipLength() * 4
                    && downCount > buyCriteria.getCandleUpDownSkipLength() * 4
                    && upDownCount > 17)
            ) {
                log.info("{}: Break upCount = {}, downCount = {}, upDownCount = {}, results.size = {}", keyCandles, upCount, downCount, upDownCount, results.size());
                break;
            }
        }
        for (var i = results.size() - 1; i >= 0; i--) {
            addCandleInterval(keyCandles, results.get(i));
        }
        if (candleIntervalResult.containsKey(keyCandles)) {
            return candleIntervalResult.get(keyCandles);
        }
        return null;
    }

    private synchronized void clearCandleInterval(FactorialDiffAvgAdapterStrategy strategy, CandleDomainEntity candle)
    {
        String indent = buildKeyCandleIntervals(strategy, candle);
        if (!candleIntervalResult.containsKey(indent)) {
            return;
        }
        var results = candleIntervalResult.get(indent);
        if (results.size() > 0) {
            var last = results.get(results.size() - 1);
            if (printDateTime(last.candle.getDateTime()).equals(printDateTime(candle.getDateTime()))) {
                results.remove(results.size() - 1);
            }
        }
    }

    private synchronized List<CandleIntervalResultData> addCandleInterval(String indent, CandleIntervalResultData v)
    {
        List<CandleIntervalResultData> results;
        if (candleIntervalResult.containsKey(indent)) {
            results = candleIntervalResult.get(indent);
        } else {
            results = new ArrayList<CandleIntervalResultData>() {
                @Override
                public boolean add(final CandleIntervalResultData present) {
                    if (size() >= 400) {
                        remove(0);
                    }
                    return super.add(present);
                }
            };
        }
        if (results.size() > 0) {
            var last = results.get(results.size() - 1);
            if (printDateTime(last.candle.getDateTime()).equals(printDateTime(v.candle.getDateTime()))) {
                results.remove(results.size() - 1);
            }
        }
        results.add(v);
        candleIntervalResult.put(indent, results);
        return results;
    }

    private String printPrice(BigDecimal s)
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

    private String printPrice(Double s)
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
        var candleList = getCandlesByFigiByLength(figi,
                currentDateTime, 1 + 1, interval);
        if (candleList == null) {
            return null;
        }
        var indent = "ema" + figi + printDateTime(candleList.get(1).getDateTime()) + interval + length + getMethodKey(keyExtractor);
        var ema = getCashedValueEma(indent);
        var indentPrev = "ema" + figi + printDateTime(candleList.get(0).getDateTime()) + interval + length + getMethodKey(keyExtractor);
        var emaPrev = getCashedValueEma(indentPrev);

        List<Double> ret = new ArrayList<Double>();
        if (ema == null || emaPrev == null) {
            candleList = getCandlesByFigiByLength(figi,
                    currentDateTime, length + prevTicks - 1, interval);
            if (candleList == null) {
                return null;
            }
            for (var j = 0; j < prevTicks; j++) {
                ema = Optional.ofNullable(candleList.get(j)).map(keyExtractor).orElse(null).doubleValue();
                for (int i = 1 + j; i < (length + j); i++) {
                    var alpha = 2f / (3 + i - (1 + j));
                    ema = alpha * Optional.ofNullable(candleList.get(i)).map(keyExtractor).orElse(null).doubleValue() + (1 - alpha) * ema;
                }
                ret.add(ema);
            }
            /*
            if (ema == null) {
                ema = Optional.ofNullable(candleList.get(1)).map(keyExtractor).orElse(null).doubleValue();
                for (int i = 2; i < (length + 1); i++) {
                    var alpha = 2f / (i + 1);
                    ema = alpha * Optional.ofNullable(candleList.get(i)).map(keyExtractor).orElse(null).doubleValue() + (1 - alpha) * ema;
                }
                addCashedValue(indent, ema);
            }

            if (emaPrev == null) {
                emaPrev = Optional.ofNullable(candleList.get(0)).map(keyExtractor).orElse(null).doubleValue();
                for (int i = 1; i < length; i++) {
                    var alpha = 2f / (i + 1 + 1);
                    emaPrev = alpha * Optional.ofNullable(candleList.get(i)).map(keyExtractor).orElse(null).doubleValue() + (1 - alpha) * emaPrev;
                }
                addCashedValue(indentPrev, emaPrev);
            }
             */
        }
        //return List.of(emaPrev, ema);
        //Collections.reverse(ret);
        return ret;
    }

    private List<CandleDomainEntity> getCandlesByFigiByLength(String figi, OffsetDateTime currentDateTime, Integer length, String interval)
    {
        return candleHistoryService.getCandlesByFigiByLength(figi,
                currentDateTime, length, interval);
    }

    private String getMethodKey(Function<? super CandleDomainEntity, ? extends BigDecimal> keyExtractor)
    {
        return Integer.toHexString(keyExtractor.hashCode());
    }

    private synchronized Double getCashedValueEma(String indent)
    {
        return null;
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

    private final Map<String, Map<String, Boolean>> booleanDataMap = new ConcurrentHashMap<>();

    public synchronized Map<String, Boolean> getOrderBooleanDataMap(AStrategy strategy, CandleDomainEntity candle)
    {
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

    public synchronized Map<String, BigDecimal> getOrderBigDecimalDataMap(AStrategy strategy, CandleDomainEntity candle)
    {
        String key = strategy.getExtName() + candle.getFigi();
        return bigDecimalDataMap.getOrDefault(key, new ConcurrentHashMap<>()).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private synchronized void setOrderBigDecimalData(FactorialDiffAvgAdapterStrategy strategy, CandleDomainEntity candle, String key, BigDecimal value)
    {
        String keyS = strategy.getExtName() + candle.getFigi();
        if (!bigDecimalDataMap.containsKey(keyS)) {
            bigDecimalDataMap.put(keyS, new ConcurrentHashMap<>());
        }
        bigDecimalDataMap.get(keyS).put(key, value);
    }
}
