package com.struchev.invest.service.processor;

import com.struchev.invest.entity.CandleDomainEntity;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    }

    public boolean isShouldBuy(AInstrumentByFiatFactorialStrategy strategy, CandleDomainEntity candle) {
        var candleList = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                candle.getDateTime(), 3, strategy.getFactorialInterval());
        var factorial = findBestFactorialInPast(strategy, candleList.get(1));
        String annotation = "null";
        var res = false;
        Double profit = factorial.getProfit();
        Double loss = factorial.getLoss();
        var futureProfit = 100f * (factorial.getProfit() - candle.getClosingPrice().doubleValue()) / candle.getClosingPrice().doubleValue();
        var closeMax = (candle.getClosingPrice().doubleValue() - factorial.getLoss())/(factorial.getProfit() - factorial.getLoss());
        annotation += " futureProfit=" + futureProfit;
        annotation += " closeMax=" + closeMax;
        Double lossAvg = null;
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
            if (closeMax < strategy.getBuyCriteria().getTakeProfitPercentBetweenCloseMax()
                    && ((strategy.getBuyCriteria().getTakeProfitPercentBetween() != null
                    && expectProfit > strategy.getBuyCriteria().getTakeProfitPercentBetween()
                    && expectLoss < strategy.getBuyCriteria().getStopLossPercent())
                    || (
                            strategy.getBuyCriteria().getTakeProfitRatio() != null
                                    && (expectLoss < strategy.getBuyCriteria().getStopLossPercent()
                                    && (expectProfit / expectLoss > strategy.getBuyCriteria().getTakeProfitRatio()
                            || expectLoss < 0))
                    ))
                    //&& candleList.get(1).getClosingPrice().doubleValue() > candle.getClosingPrice().doubleValue()
                    //&& false
            ) {
                annotation += " ok";
                annotation += " info: " + factorial.getInfo();
                var candleListPrev = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                        candle.getDateTime(), strategy.getBuyCriteria().getTakeProfitPercentBetweenLength() + 1, strategy.getFactorialInterval());
                for (var i = 0; i < strategy.getBuyCriteria().getTakeProfitPercentBetweenLength() - 1; i++) {
                    var factorialPrev = findBestFactorialInPast(strategy, candleListPrev.get(i));
                    var expectProfitPrev = factorialPrev.getExpectProfit();
                    var expectLossPrev = factorialPrev.getExpectLoss();
                    annotation +=  " i=" + i + " expectProfitPrev=" + expectProfitPrev
                            + " expectLossPrev=" + expectLossPrev;
                    if ((strategy.getBuyCriteria().getTakeProfitPercentBetween() != null
                            && expectProfitPrev > strategy.getBuyCriteria().getTakeProfitPercentBetween()
                            && expectLossPrev < strategy.getBuyCriteria().getStopLossPercent())
                            || (
                                    strategy.getBuyCriteria().getTakeProfitRatio() != null
                                            && (
                                            expectLoss < strategy.getBuyCriteria().getStopLossPercent() && (
                                                    expectProfitPrev / expectLossPrev > strategy.getBuyCriteria().getTakeProfitRatio()
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
                    && ((strategy.getBuyCriteria().getTakeLossPercentBetween() != null
                    && expectProfit < strategy.getBuyCriteria().getStopLossPercent()
                    && expectLoss > strategy.getBuyCriteria().getTakeLossPercentBetween())
                    || (strategy.getBuyCriteria().getTakeLossRatio() != null
                    && expectLoss/expectProfit > strategy.getBuyCriteria().getTakeLossRatio()
                    && expectLoss < strategy.getBuyCriteria().getTakeLossRatioMax()
                    )
                    )
                    //&& candleList.get(0).getClosingPrice().doubleValue() > candle.getClosingPrice().doubleValue()
                    && profit > candle.getClosingPrice().doubleValue()
            ) {
                annotation += " ok TakeLossPercentBetween";
                annotation += " info: " + factorial.getInfo();
                var candleListPrev = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                        candle.getDateTime(), strategy.getBuyCriteria().getTakeLossPercentBetweenLength() + 1, strategy.getFactorialInterval());
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
                    for (var i = 0; i < strategy.getBuyCriteria().getTakeLossPercentBetweenLength() - 1; i++) {
                        var factorialPrev = findBestFactorialInPast(strategy, candleListPrev.get(i));
                        var expectProfitPrev = factorialPrev.getExpectProfit();
                        var expectLossPrev = factorialPrev.getExpectLoss();
                        lossPrevAvg += factorialPrev.getLoss() / (strategy.getBuyCriteria().getTakeLossPercentBetweenLength() - 1);
                        expectProfitPrevAvg += factorialPrev.getExpectProfit() / (strategy.getBuyCriteria().getTakeLossPercentBetweenLength() - 1);
                        expectLossPrevAvg += factorialPrev.getExpectLoss() / (strategy.getBuyCriteria().getTakeLossPercentBetweenLength() - 1);
                        annotation += " i=" + i + " expectProfitPrev=" + expectProfitPrev
                                + " expectLossPrev=" + expectLossPrev;
                        if (
                                (strategy.getBuyCriteria().getTakeLossPercentBetween() != null
                                && expectProfitPrev < strategy.getBuyCriteria().getStopLossPercent()
                                && expectLossPrev > strategy.getBuyCriteria().getTakeLossPercentBetween())
                                || (
                                        strategy.getBuyCriteria().getTakeLossRatio() != null
                                                && expectLossPrev/expectProfitPrev > strategy.getBuyCriteria().getTakeLossRatio()
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
                            //&& expectProfitPrevAvg < strategy.getBuyCriteria().getStopLossPercent()
                            //&& expectLossPrevAvg > strategy.getBuyCriteria().getTakeLossPercentBetween()
                            && lossPrevAvg < loss
                    ) {
                        annotation += " ok";
                        res = true;
                    } else {
                        res = false;
                    }*/
                //}
            }

            //log.info("FactorialInstrumentByFiatService {} from {} to {} {}", candle.getFigi(), factorial.candleListPast.get(0).getDateTime(), candle.getDateTime(), factorial.candleListFeature.size(), annotation);
            if (!res
                    && strategy.getBuyCriteria().getTakeProfitLossPercent() != null
                    && candle.getClosingPrice().doubleValue() < loss
                    && futureProfit > strategy.getBuyCriteria().getTakeProfitLossPercent()
                    //&& (expectLoss + expectProfit) > strategy.getBuyCriteria().getTakeProfitPercent()
                    && expectLoss > 0
            ) {
                Double expectLossAvg = Double.valueOf(expectLoss);
                //lossAvg = candleList.get(1).getLowestPrice().doubleValue();
                lossAvg = 0.0;
                if (strategy.getFactorialAvgSize() > 1) {
                    Double maxV = null;
                    Double minV = null;
                    Double minProfit = null;
                    var candleListPrev = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                            candle.getDateTime(), strategy.getFactorialAvgSize() + 2, strategy.getFactorialInterval());
                    for (var i = 0; i < strategy.getFactorialAvgSize(); i++) {
                        var factorialPrev = findBestFactorialInPast(strategy, candleListPrev.get(i));
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
                    }
                    if (strategy.getFactorialAvgSize() > 2) {
                        //expectLossAvg -= maxV;
                        //expectLossAvg -= minV;
                        lossAvg -= minV;
                        lossAvg = lossAvg / (strategy.getFactorialAvgSize() - 1);
                        expectLossAvg = expectLossAvg / (strategy.getFactorialAvgSize() - 2);
                    } else {
                        expectLossAvg = expectLossAvg / strategy.getFactorialAvgSize();
                        lossAvg = lossAvg / strategy.getFactorialAvgSize();
                    }
                    //lossAvg = lossAvg / strategy.getFactorialAvgSize();
                    //lossAvg = lossAvg / strategy.getFactorialAvgSize();
                    //lossAvg = Math.min(candleList.get(1).getLowestPrice().doubleValue(), lossAvg);
                    //lossAvg = lossAvg * (1f - expectLossAvg / 100f);
                    //annotation += " expectLossAvg=" + expectLossAvg;
                    annotation += " lossAvg=" + lossAvg;
                    annotation += " minProfit=" + minProfit;
                    if (true
                            //&& candle.getClosingPrice().doubleValue() < lossAvg
                            && lossAvg <= loss
                            && factorial.getExpectProfit() > strategy.getBuyCriteria().getTakeProfitLossPercent()
                            //&& minProfit > loss
                            //&& (expectLossAvg + expectProfit) > strategy.getBuyCriteria().getTakeProfitPercent()
                            //&& (expectLoss + expectProfit) > strategy.getBuyCriteria().getTakeProfitPercent()
                            //&& expectLossAvg > 0
                    ) {
                        annotation += " ok < lossAvg";
                        res = true;
                    } else {
                        lossAvg = (double) 0;
                        var candleListPrevPrev = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                                candle.getDateTime(), strategy.getFactorialDownAvgSize() + 2, strategy.getFactorialInterval());
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
                                && lossAvg > loss
                                && isLess
                        ) {
                            annotation += " ok < lossDownAvg";
                            res = true;
                        }
                    }
                } else {
                    if (futureProfit > strategy.getBuyCriteria().getTakeProfitLossPercent()
                            //&& (expectLoss + expectProfit) > strategy.getBuyCriteria().getTakeProfitPercent()
                            && expectLoss >= 0
                            && factorial.getExpectProfit() > strategy.getBuyCriteria().getTakeProfitLossPercent()
                    ) {
                        annotation += " ok < loss";
                        res = true;
                    }
                }
            }
            if (!res
                    && candle.getClosingPrice().doubleValue() > profit
                    && expectProfit < 0
                    && false
            ) {
                annotation += "ok < profit";
                res = true;
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

            if (!res
                    && strategy.getBuyCriteria().getSplashLossPercentMax() != null
                    && factorial.getExpectLoss() < strategy.getBuyCriteria().getSplashLossPercentMax()
                    && factorial.getExpectProfit() > strategy.getBuyCriteria().getSplashProfitPercentMin()
                    && factorial.getLoss() < candle.getClosingPrice().doubleValue()
            ) {
                var factorialPrev = findBestFactorialInPast(strategy, candleList.get(0));
                annotation += " lossRatio=" + (factorialPrev.getExpectLoss()/factorial.getExpectLoss())
                        + " profitRatio=" + factorial.getExpectProfit()/factorialPrev.getExpectProfit()
                        + " factorialPrev.getProfit()=" + factorialPrev.getProfit();
                if (factorialPrev.getExpectLoss()/factorial.getExpectLoss() > strategy.getBuyCriteria().getSplashLossRatio()
                        && factorial.getExpectProfit()/factorialPrev.getExpectProfit() > strategy.getBuyCriteria().getSplashProfitRatio()
                        //&& factorialPrev.getProfit() > factorial.getProfit()
                ) {
                    annotation += "ok splash";
                    res = true;
                }
            }
        }
        notificationService.reportStrategy(
                strategy,
                candle.getFigi(),
                "Date|smaSlowest|smaSlow|smaFast|emaFast|ema2|bye|sell|position|deadLineBottom|deadLineTop|investBottom|investTop|smaTube|strategy|average|averageBottom|averageTop|openPrice",
                "{} | {} | {} | {} | {} | {} | {} | {} | {} ||||||by {}||||",
                notificationService.formatDateTime(candle.getDateTime()),
                candle.getClosingPrice(),
                candle.getOpenPrice(),
                candle.getHighestPrice(),
                candle.getLowestPrice(),
                candle.getClosingPrice(),
                profit,
                loss,
                lossAvg == null ? "" : lossAvg,
                annotation
        );
        return res;
    }

    @Override
    public boolean isShouldSell(AInstrumentByFiatFactorialStrategy strategy, CandleDomainEntity candle, BigDecimal purchaseRate) {
        var sellCriteria = strategy.getSellCriteria();
        var profitPercent = candle.getClosingPrice().subtract(purchaseRate)
                .multiply(BigDecimal.valueOf(100))
                .divide(purchaseRate, 4, RoundingMode.HALF_DOWN);

        var candleListPrev = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                candle.getDateTime(), strategy.getFactorialLossIgnoreSize(), strategy.getFactorialInterval());
        var factorial = findBestFactorialInPast(strategy, candleListPrev.get(strategy.getFactorialLossIgnoreSize() - 2));
        var order = orderService.findActiveByFigiAndStrategy(candle.getFigi(), strategy);
        Boolean res = false;
        String annotation = " profitPercent=" + profitPercent;
        annotation += " orderDate=" + order.getPurchaseDateTime() + " prevCandleDate=" + candleListPrev.get(0).getDateTime();
        if (sellCriteria.getStopLossSoftPercent() != null
                && profitPercent.floatValue() < -1 * sellCriteria.getStopLossSoftPercent()
                && factorial.getLoss() < candle.getClosingPrice().doubleValue()
                && candleListPrev.get(0).getDateTime().isAfter(order.getPurchaseDateTime())
        ) {
            if (strategy.getSellCriteria().getStopLossSoftLength() > 1) {
                var candleList = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                        candle.getDateTime(), strategy.getSellCriteria().getStopLossSoftLength(), strategy.getInterval());
                var profitPercentPrev = candleList.get(0).getClosingPrice().subtract(purchaseRate)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(purchaseRate, 4, RoundingMode.HALF_DOWN);
                annotation += " profitPercentPrev(" + strategy.getSellCriteria().getStopLossSoftLength()+ ")=" + profitPercentPrev;
                if (sellCriteria.getStopLossSoftPercent() != null && profitPercentPrev.floatValue() < -1 * sellCriteria.getStopLossSoftPercent()) {
                    res = true;
                }
            } else {
                res = true;
            }
        }
        if (sellCriteria.getStopLossPercent() != null
                && profitPercent.floatValue() < -1 * sellCriteria.getStopLossPercent()
                && factorial.getLoss() < candle.getClosingPrice().doubleValue()
                && candleListPrev.get(0).getDateTime().isAfter(order.getPurchaseDateTime())
        ) {
            if (strategy.getSellCriteria().getStopLossLength() > 1) {
                var candleList = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                        candle.getDateTime(), strategy.getSellCriteria().getStopLossLength(), strategy.getInterval());
                var profitPercentPrev = candleList.get(0).getClosingPrice().subtract(purchaseRate)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(purchaseRate, 4, RoundingMode.HALF_DOWN);
                annotation += " profitPercentPrev(" + strategy.getSellCriteria().getStopLossLength()+ ")=" + profitPercentPrev;
                if (sellCriteria.getStopLossSoftPercent() != null && profitPercentPrev.floatValue() < -1 * sellCriteria.getStopLossSoftPercent()) {
                    res = true;
                }
            } else {
                res = true;
            }
        }
        if (sellCriteria.getExitLossPercent() != null
                && profitPercent.floatValue() < -1 * sellCriteria.getExitLossPercent()
                && factorial.getLoss() < candle.getClosingPrice().doubleValue()
        ) {
            annotation += "ok < ExitLossPercent " + sellCriteria.getExitLossPercent();
            res = true;
        }
        if (sellCriteria.getTakeProfitPercent() != null
                && profitPercent.floatValue() > sellCriteria.getTakeProfitPercent()
        ) {
            if (strategy.getSellCriteria().getExitProfitLossPercent() != null) {
                var candleList = candleHistoryService.getCandlesByFigiBetweenDateTimes(candle.getFigi(),
                        order.getPurchaseDateTime(), candle.getDateTime(), strategy.getInterval());
                var maxPrice = candleList.stream().mapToDouble(value -> value.getClosingPrice().doubleValue()).max().orElse(-1);
                var percent = 100f * (maxPrice - candle.getClosingPrice().doubleValue()) / maxPrice;
                annotation += " maxPrice=" + maxPrice + "(" + candleList.size() + ")" + " ClosingPrice=" + candle.getClosingPrice()
                        + " percent=" + percent;
                if (percent > strategy.getSellCriteria().getExitProfitLossPercent()) {
                    res = true;
                }
            } else {
                res = true;
            }
        }
        annotation += " res=" + res;
        notificationService.reportStrategy(
                strategy,
                candle.getFigi(),
                "Date|smaSlowest|smaSlow|smaFast|emaFast|ema2|bye|sell|position|deadLineBottom|deadLineTop|investBottom|investTop|smaTube|strategy|average|averageBottom|averageTop|openPrice",
                "{} | {} | {} | {} | {} | {} | {} | {} |  |  |  |  |  |  |sell {}||||",
                notificationService.formatDateTime(candle.getDateTime()),
                candle.getClosingPrice(),
                candle.getOpenPrice(),
                candle.getHighestPrice(),
                candle.getLowestPrice(),
                candle.getClosingPrice(),
                factorial.getProfit(),
                factorial.getLoss(),
                annotation
        );
        return res;
    }

    @Override
    public AStrategy.Type getStrategyType() {
        return AStrategy.Type.instrumentFactorialByFiat;
    }

    private FactorialData findBestFactorialInPast(AInstrumentByFiatFactorialStrategy strategy, CandleDomainEntity candle) {
        var candleList = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                candle.getDateTime(), strategy.getFactorialHistoryLength(), strategy.getFactorialInterval());
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

        //expectProfit = Math.abs(expectProfit);
        //expectLoss = Math.abs(expectLoss);

        bestInfo += " diffAverage=" + (factorialDataList.stream().mapToDouble(value -> value.getDiffPrice().doubleValue()).average().orElse(-1));

        bestInfo += "Select from " + candleList.get(0).getDateTime() + ", candleList.size=" + candleList.size();
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
        log.info("Select from {} best diff={} i={}, {} ({}) {} ({}) {} ({})", candleList.get(0).getDateTime(), bestDiff, startCandleI,
                res.candleList.get(0).getDateTime(), res.candleList.size(),
                res.candleListFeature.get(0).getDateTime(), res.candleListFeature.size(),
                res.candleListPast.get(0).getDateTime(), res.candleListPast.size()
        );
        return res;
    }
}
