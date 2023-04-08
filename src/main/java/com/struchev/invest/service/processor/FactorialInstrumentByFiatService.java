package com.struchev.invest.service.processor;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.entity.OrderDomainEntity;
import com.struchev.invest.service.candle.CandleHistoryService;
import com.struchev.invest.service.notification.NotificationService;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class FactorialInstrumentByFiatService implements ICalculatorService<AInstrumentByFiatFactorialStrategy> {

    private final CandleHistoryService candleHistoryService;
    private final NotificationService notificationService;

    private final OrderService orderService;

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
        var curHourCandleForFactorial = candleList.get(1);
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
        var futureProfit = 100f * (factorial.getProfit() - candle.getClosingPrice().doubleValue()) / candle.getClosingPrice().doubleValue();
        var futureLoss = 100f * (candle.getClosingPrice().doubleValue() - factorial.getLoss()) / candle.getClosingPrice().doubleValue();
        var closeMax = (candle.getClosingPrice().doubleValue() - factorial.getLoss())/(factorial.getProfit() - factorial.getLoss());
        annotation += " futureProfit=" + futureProfit;
        annotation += " futureLoss=" + futureLoss;
        annotation += " closeMax=" + closeMax;
        //annotation += " info: " + factorial.getInfo();
        Double lossAvg = null;
        Double profitAvg = null;
        if (null != factorial) {
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

        var candleIntervalBuy = false;
        var candleIntervalSell = false;
        if (!res
                //&& candle.getClosingPrice().floatValue() < factorial.getProfit()
                && buyCriteria.getCandleIntervalMinPercent() != null
        ) {
            String keyCandles = strategy.getName() + candle.getFigi();
            var candleIntervalRes = checkCandleInterval(candle, buyCriteria);
            annotation += candleIntervalRes.annotation;
            if (candleIntervalRes.res) {
                candleIntervalBuy = true;
                var isOrderFind = false;
                var order = orderService.findLastByFigiAndStrategy(candle.getFigi(), strategy);
                var upRes = isOrderCandleUp(strategy, candle, order, buyCriteria, sellCriteria);
                if (upRes.res) {
                    annotation += upRes.annotation;
                    order = null;
                }
                var intervalCandles = getCandleIntervals(keyCandles);
                var candleIPrev = candleHistoryService.getCandlesByFigiByLength(
                        candle.getFigi(),
                        candle.getDateTime(),
                        buyCriteria.getCandleMaxIntervalLess(),
                        buyCriteria.getCandleInterval()
                );
                if (null == intervalCandles) {
                    var candleStrategyCurHour = curHourCandleForFactorial;
                    var candleStrategy = newStrategy;
                    for (var i = 1; i < candleIPrev.size() && !isOrderFind; i++) {
                        var candleStrategyCurHourI = getCandleHour(strategy, candleIPrev.get(i));
                        if (candleStrategyCurHourI.getDateTime().compareTo(candleStrategyCurHour.getDateTime()) != 0) {
                            candleStrategyCurHour = curHourCandleForFactorial;
                            candleStrategy = buildAvgStrategy(strategy, candleIPrev.get(i));
                        }
                        var candleIntervalResSell = checkCandleInterval(candleIPrev.get(i), candleStrategy.getSellCriteria());
                        if (candleIntervalResSell.res) {
                            addCandleInterval(keyCandles, candleIntervalResSell);
                        } else {
                            var candleIntervalResBuy = checkCandleInterval(candleIPrev.get(i), candleStrategy.getBuyCriteria());
                            if (candleIntervalResBuy.res) {
                                addCandleInterval(keyCandles, candleIntervalResSell);
                            }
                        }
                    }
                    intervalCandles = getCandleIntervals(keyCandles);
                }
                if (null != intervalCandles) {
                    CandleDomainEntity candleIntervalResBuyLast = null;
                    CandleDomainEntity candleIntervalResSellLast = null;
                    CandleIntervalResultData candleIntervalResBuyOk = null;
                    CandleIntervalResultData candleIntervalResSellOk = null;
                    CandleIntervalResultData candleIntervalResSellOk2 = null;
                    for (var i = intervalCandles.size() - 1; i >= 0 && !isOrderFind ; i--) {
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
                                    if (order.getPurchasePrice().compareTo(order.getSellPrice()) < 0) {
                                        annotation += " ORDER buy < sell: " + printPrice(order.getPurchasePrice()) + " (" + order.getPurchaseDateTime() +
                                                ") < " + printPrice(order.getSellPrice()) + "(" +order.getSellProfit() + ")";
                                        annotation += " CandleInterval OK";
                                        res = true;
                                        candleIntervalResSellLast = candleHistoryService.getCandlesByFigiByLength(
                                                candle.getFigi(),
                                                order.getSellDateTime(),
                                                1,
                                                strategy.getInterval()
                                        ).get(0);
                                        /*candleIntervalResBuyLast = candleHistoryService.getCandlesByFigiByLength(
                                                candle.getFigi(),
                                                order.getPurchaseDateTime(),
                                                1,
                                                strategy.getInterval()
                                        ).get(0);*/
                                        isOrderFind = true;
                                    }
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
                            && null == buyCriteria.getCandleMinFactor()
                            && !res
                            && null != order
                            && null != candleIntervalResBuyOk
                            && order.getSellDateTime().isAfter(candleIntervalResBuyOk.candle.getDateTime())
                    ) {
                        isOrderFind = true;
                        if (order.getPurchasePrice().compareTo(order.getSellPrice()) < 0) {
                            annotation += " ORDER buy < sell: " + printPrice(order.getPurchasePrice()) + " (" + order.getPurchaseDateTime() +
                                    ") < " + printPrice(order.getSellPrice()) + "(" +order.getSellProfit() + ")";
                            annotation += " CandleInterval OK";
                            res = true;
                        }
                    }
                    if (
                            res
                            && null == buyCriteria.getCandleMinFactor()
                            && isOrderFind
                            && null != order
                            && (candleIntervalResBuyOk == null || candleIntervalResBuyOk.candle.getDateTime().isBefore(candleIPrev.get(0).getDateTime()))
                            && order.getSellDateTime().isBefore(candleIPrev.get(0).getDateTime())
                            && order.getPurchasePrice().compareTo(order.getSellPrice()) > 0
                    ) {
                        annotation += " ORDER after buy < sell: " + printPrice(order.getPurchasePrice()) + " (" + order.getPurchaseDateTime() +
                                ") < " + printPrice(order.getSellPrice()) + "(" +order.getSellProfit() + ")";
                        annotation += " CandleInterval FALSE";
                        res = false;
                    }

                    if (
                            null != buyCriteria.getCandleMinFactor()
                            && null != candleIntervalResSellLast
                            && null != candleIntervalResBuyLast
                    ) {
                        annotation += " factor: " + notificationService.formatDateTime(candleIntervalResBuyLast.getDateTime())
                                + " - " + notificationService.formatDateTime(candleIntervalResSellLast.getDateTime());
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
                        var minPrice = Math.min(
                                upCandles.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).min().orElse(-1),
                                downCandles.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).min().orElse(-1)
                                );
                        var maxPrice = Math.max(
                                upCandles.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).max().orElse(-1),
                                downCandles.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).max().orElse(-1)
                            );
                        var factorPrice = (maxPrice - candle.getClosingPrice().floatValue())
                                / (maxPrice - minPrice);
                        annotation += " min = " + printPrice(minPrice);
                        annotation += " max = " + printPrice(maxPrice);
                        annotation += " factorPrice = " + printPrice(maxPrice - candle.getClosingPrice().floatValue())
                                + " / " + printPrice(maxPrice - minPrice)
                                + " = " + printPrice(factorPrice);
                        Float factorCandle = 1f * upCandles.size() / downCandles.size();
                        annotation += " factorCandle = " + printPrice(1f * upCandles.size())
                                + " / " + printPrice(1f * downCandles.size())
                                + " = " + printPrice(factorCandle);
                        var factor = factorPrice * factorPrice * Math.sqrt(factorCandle);
                        annotation += " factor = " + printPrice((float) factor);
                        if (
                                factor > buyCriteria.getCandleMinFactor()
                                && factor < buyCriteria.getCandleMaxFactor()
                                && factorPrice < buyCriteria.getCandleMaxFactor()
                                && factorCandle < buyCriteria.getCandleMaxFactor()
                                && factorPrice > buyCriteria.getCandlePriceMinFactor()
                                && factorPrice < buyCriteria.getCandlePriceMaxFactor()
                        ) {
                            annotation += " candleFactor OK";
                            res = true;
                        } else if (
                                buyCriteria.getCandlePriceMinMaxFactor() != null
                                && factor < buyCriteria.getCandlePriceMinMaxFactor()
                                && factor > buyCriteria.getCandlePriceMinMinFactor()
                        ) {
                            annotation += " candleFactor minmin OK";
                            res = true;
                        } else {
                            res = false;
                        }
                    }
                }
                addCandleInterval(keyCandles, candleIntervalRes);
            } else {
                var candleIntervalResSell = checkCandleInterval(candle, sellCriteria);
                if (candleIntervalResSell.res) {
                    candleIntervalSell = true;
                    annotation += " SELL ok: " + candleIntervalResSell.annotation;
                    String key = buildKeyHour(strategy.getName(), candle);
                    addCashedIsBuyValue(key, null);
                    if (strategy.getBuyCriteria().getProfitPercentFromBuyMinPriceLength() > 1) {
                        String keyPrev = buildKeyHour(strategy.getName(), curHourCandleForFactorial);
                        addCashedIsBuyValue(keyPrev, null);
                    }

                    if (null != buyCriteria.getCandleOnlyUpLength()) {
                        annotation += " CandleOnlyUpLength=" + buyCriteria.getCandleOnlyUpLength();
                        var intervalCandles = getCandleIntervals(keyCandles);
                        var order = orderService.findLastByFigiAndStrategy(candle.getFigi(), strategy);
                        var upRes = isOrderCandleUp(strategy, candle, order, buyCriteria, sellCriteria);
                        if (upRes.res) {
                            annotation += upRes.annotation;
                            order = null;
                        }
                        if (
                                null != intervalCandles
                                && (order == null || order.getSellProfit().compareTo(BigDecimal.ZERO) > 0)
                        ) {
                            List<CandleIntervalResultData> sellPoints = new ArrayList<>();
                            CandleIntervalResultData lastSellPoint = candleIntervalResSell;
                            sellPoints.add(lastSellPoint);
                            Integer lastPointI = sellPoints.size() - 1;
                            for (var i = intervalCandles.size() - 1; i >= 0; i--) {
                                var candleRes = intervalCandles.get(i);
                                if (
                                        candleRes.isDown
                                        && sellPoints.size() != buyCriteria.getCandleOnlyUpLength()
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
                                        upCandles.size() > buyCriteria.getCandleOnlyUpPointLength()
                                ) {
                                    annotation += " new point = " + lastPointI + ":" + notificationService.formatDateTime(candleRes.getCandle().getDateTime());
                                    //if (candleRes.candle.getClosingPrice().compareTo(lastSellPoint.candle.getClosingPrice()) < 0) {
                                        sellPoints.add(candleRes);
                                        lastPointI = sellPoints.size() - 1;
                                    //} else {
                                    //    annotation += " break down: " + printPrice(candleRes.candle.getClosingPrice()) + " >= "
                                    //            + printPrice(lastSellPoint.candle.getClosingPrice()) + "(" + notificationService.formatDateTime(lastSellPoint.candle.getDateTime()) + ")";
                                    //    break;
                                    //}
                                } else {
                                    sellPoints.set(lastPointI, candleRes);
                                }
                                lastSellPoint = candleRes;
                                if (sellPoints.size() > buyCriteria.getCandleOnlyUpLength()) {
                                    if (buyCriteria.getCandleOnlyUpBetweenPercent() != null) {
                                        if (order != null) {
                                            var percentOrderFromDown = 100f * (order.getSellPrice().floatValue() - order.getPurchasePrice().floatValue()) / order.getPurchasePrice().floatValue();
                                            var candleResDown = intervalCandles.stream().filter(c -> c.isDown).reduce((first, second) -> second).orElse(null);
                                            if (
                                                    null != candleResDown
                                                    && candleResDown.getCandle().getClosingPrice().compareTo(order.getPurchasePrice()) < 0
                                            ) {
                                                annotation += " candleResDown = " + printPrice(candleResDown.getCandle().getClosingPrice());
                                                percentOrderFromDown = 100f * (order.getSellPrice().floatValue() - candleResDown.getCandle().getClosingPrice().floatValue()) / candleResDown.getCandle().getClosingPrice().floatValue();
                                            }
                                            var percent = 100f * (candle.getClosingPrice().floatValue() - order.getSellPrice().floatValue()) / candle.getClosingPrice().floatValue();
                                            annotation += " percentB = " + printPrice(percent) + " > " + printPrice(percentOrderFromDown);
                                            if (percent > percentOrderFromDown) {
                                                annotation += " candle UP OK";
                                                var resBetween = true;
                                                for (var iC = 0; iC < buyCriteria.getCandleOnlyUpLength() - 1 && null != buyCriteria.getCandleOnlyUpBetweenPointsPercent(); iC++) {
                                                    var pointUp = sellPoints.get(iC);
                                                    var pointDown = sellPoints.get(iC + 1);
                                                    if (pointUp.candle.getClosingPrice().compareTo(pointDown.candle.getClosingPrice()) < 0) {
                                                        resBetween = false;
                                                            annotation += " break down: " + printPrice(pointUp.candle.getClosingPrice()) + "(" + notificationService.formatDateTime(pointUp.candle.getDateTime()) + ") >= "
                                                                    + printPrice(pointDown.candle.getClosingPrice()) + "(" + notificationService.formatDateTime(pointDown.candle.getDateTime()) + ")";
                                                        break;
                                                    }
                                                    annotation += " point i" + i + ": " + notificationService.formatDateTime(pointDown.getCandle().getDateTime())
                                                            + " - " + notificationService.formatDateTime(pointUp.getCandle().getDateTime());
                                                    var candlesBetween = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                                                            candle.getFigi(),
                                                            pointDown.getCandle().getDateTime(),
                                                            pointUp.getCandle().getDateTime(),
                                                            strategy.getInterval()
                                                    );
                                                    var minPrice = candlesBetween.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).min().orElse(-1);
                                                    var percentMin = 100f * (pointDown.getCandle().getClosingPrice().doubleValue() - minPrice) / minPrice;
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
                                    break;
                                }
                            }
                        }
                    }
                    addCandleInterval(keyCandles, candleIntervalResSell);
                } else {
                    annotation += " SELL false: " + candleIntervalResSell.annotation;
                }
            }
        }

        if (res && buyCriteria.getSkipIfOutPrevLength() != null) {
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

        if (res && buyCriteria.getNotLossSellLength() > 0) {
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
                    }
                }
            }
        }

        String key = buildKeyHour(strategy.getName(), candle);
        var buyPrice = getCashedIsBuyValue(key);
        if (null == buyPrice
                && buyCriteria.getProfitPercentFromBuyMinPriceLength() > 1
        ) {
            String keyPrev = buildKeyHour(strategy.getName(), curHourCandleForFactorial);
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
                            var pDown = 100f * (maxPrice - curPrice) / maxPrice;
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
        notificationService.reportStrategyExt(
                res,
                strategy,
                candle,
                "Date|open|high|low|close|ema2|profit|loss|limitPrice|lossAvg|deadLineTop|investBottom|investTop|smaTube|strategy|average|averageBottom|averageTop|candleBuySell",
                "{} | {} | {} | {} | {} | | {} | {} | | {} | ||||by {}||||{}",
                notificationService.formatDateTime(candle.getDateTime()),
                candle.getOpenPrice(),
                candle.getHighestPrice(),
                candle.getLowestPrice(),
                candle.getClosingPrice(),
                profit,
                loss,
                lossAvg == null ? "" : lossAvg,
                annotation,
                candleIntervalSell ? candle.getClosingPrice().multiply(BigDecimal.valueOf(1. + candleIntervalMinPercent / 100))
                        : (candleIntervalBuy ? candle.getClosingPrice().multiply(BigDecimal.valueOf(1. - profitPercentFromBuyMinPrice / 100)) : "")
                );
        return resBuy;
    }

    @Override
    public boolean isShouldSell(AInstrumentByFiatFactorialStrategy strategy, CandleDomainEntity candle, BigDecimal purchaseRate) {

        var profitPercent = candle.getClosingPrice().subtract(purchaseRate)
                .multiply(BigDecimal.valueOf(100))
                .divide(purchaseRate, 4, RoundingMode.HALF_DOWN);

        var candleListPrev = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                candle.getDateTime(), strategy.getFactorialLossIgnoreSize(), strategy.getFactorialInterval());
        var curHourCandle = candleListPrev.get(strategy.getFactorialLossIgnoreSize() - 1);
        var factorial = findBestFactorialInPast(strategy, curHourCandle);

        String key = buildKeyHour(strategy.getName(), candle);
        addCashedIsBuyValue(key, null);
        if (strategy.getBuyCriteria().getProfitPercentFromBuyMinPriceLength() > 1) {
            String keyPrev = buildKeyHour(strategy.getName(), curHourCandle);
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
                        candle.getDateTime(),  sellCriteria.getSellUnderLossLength(), strategy.getFactorialInterval());
                annotation += " time=" + candleListPrevProfit.get(0).getDateTime();
                isOrdeCrossTunel = candleListPrevProfit.get(0).getDateTime().isAfter(order.getPurchaseDateTime());
            } else {
                if (!isOrdeCrossTunel) {
                    annotation += " time=" + curBeginHour;
                    isOrdeCrossTunel = curBeginHour.isAfter(order.getPurchaseDateTime());
                }
            }
            if (isOrdeCrossTunel
                    || candleListPrev.get(0).getDateTime().isAfter(order.getPurchaseDateTime())
            ) {
                if (factorial.getProfit() < candle.getClosingPrice().doubleValue()) {
                    annotation += " profit < close";
                } else {
                    annotation += "loss > close";
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
            String keySell = "sell" + candle.getFigi() + notificationService.formatDateTime(order.getPurchaseDateTime());
            var sellData = getCashedIsBuyValue(keySell);
            if (sellData != null) {
                purchasePrice = sellData.price;
                startDate = sellData.dateTime;
                profitPercentSell2 = (float) (100f * (candle.getClosingPrice().floatValue() - purchasePrice) / purchasePrice);
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
            var minPercent = 100f * (purchasePrice - minPrice) / purchasePrice;
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
                var percent = 100f * (maxPrice - candle.getClosingPrice().doubleValue()) / maxPrice;
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
            String keySell = "sellDown" + candle.getFigi() + notificationService.formatDateTime(order.getPurchaseDateTime());
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
        String keyCandles = strategy.getName() + candle.getFigi();
        if (!res) {
            var upRes = isOrderCandleUp(strategy, candle, order, buyCriteria, sellCriteria);
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
            String keySell = "sellUp" + candle.getFigi() + notificationService.formatDateTime(order.getPurchaseDateTime());
            var isSkipOnlyUp = false;
            if (
                    true
                    // && candleIntervalRes.res
                    && null != buyCriteria.getCandleOnlyUpLength()
                    && null != sellCriteria.getCandleOnlyUpProfitMinPercent()
                    && isOrderUpCandle
            ) {
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
            if (candleIntervalRes.res) {
                candleIntervalSell = true;
                addCandleInterval(keyCandles, candleIntervalRes);
            } else {
                var candleIntervalBuyRes = checkCandleInterval(candle, buyCriteria);
                candleIntervalBuy = candleIntervalBuyRes.res;
                if (candleIntervalBuyRes.res) {
                    annotation += " res BUY OK candleInterval: " + candleIntervalBuyRes.annotation;
                    addCandleInterval(keyCandles, candleIntervalBuyRes);
                }
            }
            if (sellCriteria.getProfitPercentFromSellMinPrice() != null) {
                var sellData = getCashedIsBuyValue(keySell);
                if (null == sellData
                        && sellCriteria.getProfitPercentFromSellMinPriceLength() > 1
                ) {
                    String keyPrev = buildKeyHour("sellUp" + candle.getFigi(), curHourCandle);
                    sellData = getCashedIsBuyValue(keyPrev);
                    if (null != sellData) {
                        keySell = keyPrev;
                    }
                }
                if (res || null != sellData) {
                    annotation += " sellUpKEY=" + keySell;
                    if (res) {
                        sellData = addCashedIsBuyValue(keySell, BuyData.builder()
                                .price(candle.getClosingPrice().doubleValue())
                                .dateTime(candle.getDateTime())
                                .build());
                    }
                    var curProfitPercentFromSellMinPrice = 100f * (candle.getClosingPrice().floatValue() - sellData.getPrice()) / sellData.getPrice();
                    annotation += " curProfPerFromSell=" + curProfitPercentFromSellMinPrice + " > " + sellCriteria.getProfitPercentFromSellMinPrice();
                    if (curProfitPercentFromSellMinPrice > sellCriteria.getProfitPercentFromSellMinPrice()
                            || (sellCriteria.getProfitPercentFromSellMaxPrice() != null
                            && curProfitPercentFromSellMinPrice < sellCriteria.getProfitPercentFromSellMaxPrice())
                    ) {
                        annotation += " sell OK";
                        res = true;
                    } else {
                        res = false;
                    }
                }
            }
            if (
                    res
                    && sellCriteria.getCandleProfitMinPercent() != null
            ) {
                annotation += " CandleProfitMinPercent = " + sellCriteria.getCandleProfitMinPercent();
                if (profitPercent.floatValue() < sellCriteria.getCandleProfitMinPercent()) {
                    annotation += " SKIP min candleInterval";
                    res = false;
                }
            }
            if (
                    null != buyCriteria.getCandleOnlyUpLength()
                    && null != sellCriteria.getCandleOnlyUpStopLossPercent()
                    && isOrderUpCandle
            ) {
                var intervalCandles = getCandleIntervals(keyCandles);
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
                            if (curProfitPercent.floatValue() < -sellCriteria.getCandleOnlyUpStopLossPercent()) {
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

        annotation += " res=" + res;
        BigDecimal limitPrice = null;
        if (strategy.getSellLimitCriteria() != null) {
            limitPrice = purchaseRate.multiply(BigDecimal.valueOf((strategy.getSellLimitCriteria().getExitProfitPercent() + 100.) / 100.));
            annotation += " limit=" + strategy.getSellLimitCriteria().getExitProfitPercent();
        }
        var candleIntervalMinPercent = buyCriteria.getCandleIntervalMinPercent();
        if (null != sellCriteria.getProfitPercentFromSellMinPrice()) {
            candleIntervalMinPercent = sellCriteria.getProfitPercentFromSellMinPrice();
        }
        var profitPercentFromBuyMinPrice = sellCriteria.getCandleIntervalMinPercent();
        if (null != buyCriteria.getProfitPercentFromBuyMinPrice()) {
            profitPercentFromBuyMinPrice = (float) -buyCriteria.getProfitPercentFromBuyMinPrice();
        }
        notificationService.reportStrategyExt(
                res,
                strategy,
                candle,
                "Date|open|high|low|close|ema2|profit|loss|limitPrice|lossAvg|deadLineTop|investBottom|investTop|smaTube|strategy|average|averageBottom|averageTop|candleBuySell",
                "{} | {} | {} | {} | {} | | {} | {} | {} |  |  |  |  |  |sell {}||||{}",
                notificationService.formatDateTime(candle.getDateTime()),
                candle.getOpenPrice(),
                candle.getHighestPrice(),
                candle.getLowestPrice(),
                candle.getClosingPrice(),
                factorial.getProfit(),
                factorial.getLoss(),
                limitPrice,
                annotation,
                candleIntervalSell ? candle.getClosingPrice().multiply(BigDecimal.valueOf(1. + candleIntervalMinPercent / 100))
                        : (candleIntervalBuy ? candle.getClosingPrice().multiply(BigDecimal.valueOf(1. - profitPercentFromBuyMinPrice / 100)) : "")
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
        String key = strategy.getName() + candle.getFigi() + candleListCash.get(0).getDateTime();
        var ret = getCashedValue(key);
        if (ret != null) {
            return ret;
        }
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
            var expectProfit = 100f * (maxPrice - candleListFactorial.get(candleListFactorial.size() - 1).getClosingPrice().doubleValue()) / maxPrice;
            var expectLoss = 100f * (candleListFactorial.get(candleListFactorial.size() - 1).getClosingPrice().doubleValue() - minPrice) / minPrice;
            expectProfitList.add(expectProfit);
            expectLossList.add(expectLoss);
            bestInfo += " expectLoss = " + candleListFactorial.get(candleListFactorial.size() - 1).getDateTime() + ":" + candleListFactorial.get(candleListFactorial.size() - 1).getClosingPrice().doubleValue() + "-"
                    +  minPrice + "=" + expectLoss;
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
                .dateTime(candleListCash.get(0).getDateTime())
                .build();
        addCashedValue(key, res);
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
    private CandleIntervalResultData checkCandleInterval(CandleDomainEntity candle, AInstrumentByFiatFactorialStrategy.CandleIntervalInterface buyCriteria) {
        if (buyCriteria.getCandleIntervalMinPercent() == null) {
            return CandleIntervalResultData.builder()
                    .res(false)
                    .annotation("")
                    .candle(candle)
                    .build();
        }
        var res = false;
        var annotation = "";
        var candleIPrev = candleHistoryService.getCandlesByFigiByLength(
                candle.getFigi(),
                candle.getDateTime(),
                buyCriteria.getCandleMaxInterval(),
                buyCriteria.getCandleInterval()
        );
        Collections.reverse(candleIPrev);
        var isOk = true;
        var iBegin = 0;
        if (candleIPrev.get(0).getDateTime().isEqual(candle.getDateTime())) {
            iBegin = 1;
        }
        var i = iBegin;
        var iEndDown = i;
        var candleUpLength = 0;
        for (; i < candleIPrev.size(); i++) {
            if (buyCriteria.isCandleIntervalReverseDirection(candleIPrev.get(i).getOpenPrice(), candleIPrev.get(i).getClosingPrice())) {
                annotation += " i=" + i + " not target " + candleIPrev.get(i).getOpenPrice() + " - " + candleIPrev.get(i).getClosingPrice();
                if (candleUpLength < buyCriteria.getCandleUpLength()) {
                    annotation += " CandleUpLength false: " + candleUpLength + " < " + buyCriteria.getCandleUpLength();
                    isOk = false;
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
            candleMinLength = buyCriteria.getCandleUpLength() + (candleMinLength - buyCriteria.getCandleUpLength()) * iUpMiddleLength;
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
        if (isOk) {
            var intervalPercent = Math.abs(100f * (candleIPrev.get(iBeginDown).getOpenPrice().floatValue() - candle.getClosingPrice().floatValue()) / candleIPrev.get(iBeginDown).getOpenPrice().floatValue());
            annotation += " intervalPercent = " + intervalPercent + " (" + candleIPrev.get(iBeginDown).getOpenPrice() + "(" + iBeginDown + ": " + candleIPrev.get(iBeginDown).getDateTime() + ") - "
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
                .isDown(candleIPrev.get(iBeginDown).getClosingPrice().compareTo(candleIPrev.get(iEndDown).getClosingPrice()) > 0)
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

        var candleListForAvg = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                candle.getDateTime(), strategy.getPriceDiffAvgLength() + 1, strategy.getFactorialInterval());
        var priceDiffAvgReal = factorial.getExpectLoss() + factorial.getExpectProfit();
        for (var i = 0; i < (candleListForAvg.size() - 1); i++) {
            var factorialForAvg = findBestFactorialInPast(strategy, candleListForAvg.get(i));
            if (null == factorialForAvg) {
                return null;
            }
            priceDiffAvgReal += factorialForAvg.getExpectLoss() + factorialForAvg.getExpectProfit();
        }
        priceDiffAvgReal = priceDiffAvgReal / candleListForAvg.size();
        newStrategy.setPriceDiffAvgReal(priceDiffAvgReal);
        return newStrategy;
    }

    private CandleIntervalResultData isOrderCandleUp(
            AInstrumentByFiatFactorialStrategy strategy,
            CandleDomainEntity candle,
            OrderDomainEntity order,
            AInstrumentByFiatFactorialStrategy.BuyCriteria buyCriteria,
            AInstrumentByFiatFactorialStrategy.SellCriteria sellCriteria
    ) {
        Boolean isOrderUpCandle = false;
        String annotation = "";
        String keyCandles = strategy.getName() + candle.getFigi();
        if (
                null != order
                && buyCriteria.getCandleIntervalMinPercent() != null
                && null != buyCriteria.getCandleOnlyUpLength()
                && null != sellCriteria.getCandleOnlyUpProfitMinPercent()
        ) {
            var intervalCandles = getCandleIntervals(keyCandles);
            if (null != intervalCandles) {
                for (var i = intervalCandles.size() - 1; i >= 0; i--) {
                    var candleRes = intervalCandles.get(i);
                    if (!order.getPurchaseDateTime().isBefore(candleRes.candle.getDateTime())) {
                        annotation += " upOrDown: i=" + i + ":" + notificationService.formatDateTime(candleRes.candle.getDateTime());
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
        String key = strategyName + candle.getFigi() + notificationService.formatDateTime(curBeginHour);
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

    private synchronized List<CandleIntervalResultData> getCandleIntervals(String indent)
    {
        if (candleIntervalResult.containsKey(indent)) {
            return candleIntervalResult.get(indent);
        }
        return null;
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
                    if (size() >= 1000) {
                        remove(0);
                    }
                    return super.add(present);
                }
            };
        }
        results.add(v);
        candleIntervalResult.put(indent, results);
        return results;
    }

    private String printPrice(BigDecimal s)
    {
        return printPrice(s.toString());
    }

    private String printPrice(Float s)
    {
        return printPrice(s.toString());
    }

    private String printPrice(Double s)
    {
        return printPrice(s.toString());
    }

    private String printPrice(String s)
    {
        return s.indexOf(".") < 0 ? s : s.replaceAll("0*$", "").replaceAll("\\.$", "");
    }
}
