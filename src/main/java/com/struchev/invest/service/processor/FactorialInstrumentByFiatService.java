package com.struchev.invest.service.processor;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.service.candle.CandleHistoryService;
import com.struchev.invest.service.notification.NotificationService;
import com.struchev.invest.strategy.AStrategy;
import com.struchev.invest.strategy.instrument_by_fiat_factorial.AInstrumentByFiatFactorialStrategy;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class FactorialInstrumentByFiatService implements ICalculatorService<AInstrumentByFiatFactorialStrategy> {

    private final CandleHistoryService candleHistoryService;
    private final NotificationService notificationService;

    @Builder
    @Data
    public static class FactorialData {
        Integer i;
        Integer size;
        Integer length;
        Float diff;
        Float diffValue;
        List<CandleDomainEntity> candleList;
        List<CandleDomainEntity> candleListFeature;
        List<CandleDomainEntity> candleListPast;
        String info;
        Float expectProfit;
        Float expectLoss;
    }

    public boolean isShouldBuy2(AInstrumentByFiatFactorialStrategy strategy, CandleDomainEntity candle) {
        var candleList = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                candle.getDateTime(), 2, strategy.getInterval());
        var factorial = findBestFactorialInPast(strategy, candleList.get(0));
        String annotation = "null";
        var res = false;
        Double profit = candle.getClosingPrice().doubleValue();
        Double loss = candle.getClosingPrice().doubleValue();
        if (null != factorial) {
            annotation = "factorial from " + factorial.getCandleList().get(0).getDateTime()
                    + " to " + factorial.getCandleList().get(factorial.getCandleList().size() - 1).getDateTime() + " size=" + factorial.getSize()
                    + " diff=" + factorial.diff
                    + " for from " + factorial.candleListPast.get(0).getDateTime();
            Double maxPrice = (factorial.candleListFeature.stream().mapToDouble(value -> value.getHighestPrice().doubleValue()).max().orElse(-1));
            Double minPrice = factorial.candleListFeature.stream().mapToDouble(value -> value.getLowestPrice().doubleValue()).min().orElse(-1);
            var expectProfit = 100f * (maxPrice - factorial.candleList.get(factorial.candleList.size() - 1).getClosingPrice().doubleValue()) / maxPrice;
            var expectLoss = 100f * (factorial.candleList.get(factorial.candleList.size() - 1).getClosingPrice().doubleValue() - minPrice) / minPrice;
            loss = loss * (1f - expectLoss / 100f);
            annotation += " expectProfitPrev=" + expectProfit
                    + " expectLossPrev=" + expectLoss
                    + " loss=" + loss + " >= " + candle.getClosingPrice();
            if (loss >= candle.getClosingPrice().doubleValue()) {
                annotation += " ok";
                annotation += " info: " + factorial.getInfo();
                var factorialPrev = findBestFactorialInPast(strategy, candle);
                Double maxPricePrev = (factorialPrev.candleListFeature.stream().mapToDouble(value -> value.getHighestPrice().doubleValue()).max().orElse(-1));
                Double minPricePrev = factorialPrev.candleListFeature.stream().mapToDouble(value -> value.getLowestPrice().doubleValue()).min().orElse(-1);
                var expectProfitPrev = 100f * (maxPricePrev - factorialPrev.candleList.get(factorialPrev.candleList.size() - 1).getClosingPrice().doubleValue()) / maxPricePrev;
                var expectLossPrev = 100f * (factorialPrev.candleList.get(factorialPrev.candleList.size() - 1).getClosingPrice().doubleValue() - minPricePrev) / minPricePrev;
                annotation += " expectProfit=" + expectProfitPrev
                        + " expectLoss=" + expectLossPrev
                        + " > getTakeProfitPercent=" + strategy.getBuyCriteria().getTakeProfitPercent();
                if (expectProfitPrev > strategy.getBuyCriteria().getTakeProfitPercent()) {
                    annotation += " ok";
                    res = true;
                }
                profit = profit * (1f + expectProfitPrev / 100f);
                //loss = loss * (1f - expectLossPrev / 100f);
            }
            log.info("FactorialInstrumentByFiatService {} from {} to {} {} {} {}", candle.getFigi(), factorial.candleListPast.get(0).getDateTime(), candle.getDateTime(), maxPrice, minPrice, factorial.candleListFeature.size(), annotation);
        }
        notificationService.reportStrategy(
                strategy,
                candle.getFigi(),
                "Date|smaSlowest|smaSlow|smaFast|emaFast|ema2|bye|sell|position|deadLineBottom|deadLineTop|investBottom|investTop|smaTube|strategy|average|averageBottom|averageTop|openPrice"
                //+ "|ClosingPrice|OpenPrice",
                ,
                "{} | {} | {} | {} | {} | {} | {} | {} |||||||by {}||||",
                notificationService.formatDateTime(candle.getDateTime()),
                candle.getClosingPrice(),
                candle.getOpenPrice(),
                candle.getHighestPrice(),
                candle.getLowestPrice(),
                candle.getClosingPrice(),
                profit,
                loss,
                annotation
        );
        return res;
    }

    public boolean isShouldBuy(AInstrumentByFiatFactorialStrategy strategy, CandleDomainEntity candle) {
        var factorial = findBestFactorialInPast(strategy, candle);
        String annotation = "null";
        var res = false;
        Double profit = candle.getClosingPrice().doubleValue();
        Double loss = candle.getClosingPrice().doubleValue();
        if (null != factorial) {
            annotation = "factorial from " + factorial.getCandleList().get(0).getDateTime()
                    + " to " + factorial.getCandleList().get(factorial.getCandleList().size() - 1).getDateTime() + " size=" + factorial.getSize()
                    + " diff=" + factorial.diff
                    + " for from " + factorial.candleListPast.get(0).getDateTime();
            Double maxPrice = (factorial.candleListFeature.stream().mapToDouble(value -> value.getHighestPrice().doubleValue()).max().orElse(-1));
            Double minPrice = factorial.candleListFeature.stream().mapToDouble(value -> value.getLowestPrice().doubleValue()).min().orElse(-1);
            var expectProfit = 100f * (maxPrice - factorial.candleList.get(factorial.candleList.size() - 1).getClosingPrice().doubleValue()) / maxPrice;
            var expectLoss = 100f * (factorial.candleList.get(factorial.candleList.size() - 1).getClosingPrice().doubleValue() - minPrice) / minPrice;
            annotation += " expectProfit=" + expectProfit
                    + " expectLoss=" + expectLoss
                    + "(from " + factorial.candleList.get(0).getDateTime() + " to " + factorial.candleList.get(factorial.candleList.size() - 1).getDateTime() + ")"
                    + "(from " + factorial.candleListFeature.get(0).getDateTime() + " to " + factorial.candleListFeature.get(factorial.candleListFeature.size() - 1).getDateTime() + ")";
            if (expectProfit > strategy.getBuyCriteria().getTakeProfitPercent() && expectLoss < strategy.getBuyCriteria().getStopLossPercent()) {
                annotation += " ok";
                annotation += " info: " + factorial.getInfo();
                var candleList = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                        candle.getDateTime(), 2, strategy.getInterval());
                var factorialPrev = findBestFactorialInPast(strategy, candleList.get(0));
                Double maxPricePrev = (factorialPrev.candleListFeature.stream().mapToDouble(value -> value.getHighestPrice().doubleValue()).max().orElse(-1));
                Double minPricePrev = factorialPrev.candleListFeature.stream().mapToDouble(value -> value.getLowestPrice().doubleValue()).min().orElse(-1);
                var expectProfitPrev = 100f * (maxPricePrev - factorialPrev.candleList.get(factorialPrev.candleList.size() - 1).getClosingPrice().doubleValue()) / maxPricePrev;
                var expectLossPrev = 100f * (factorialPrev.candleList.get(factorialPrev.candleList.size() - 1).getClosingPrice().doubleValue() - minPricePrev) / minPricePrev;
                annotation += " expectProfitPrev=" + expectProfitPrev
                        + " expectLossPrev=" + expectLossPrev;
                if (expectProfitPrev > strategy.getBuyCriteria().getTakeProfitPercent() && expectLossPrev < strategy.getBuyCriteria().getStopLossPercent()) {
                    annotation += " ok";
                    res = true;
                }
            }
            profit = profit * (1f + expectProfit / 100f);
            loss = loss * (1f - expectLoss / 100f);
            log.info("FactorialInstrumentByFiatService {} from {} to {} {} {} {}", candle.getFigi(), factorial.candleListPast.get(0).getDateTime(), candle.getDateTime(), maxPrice, minPrice, factorial.candleListFeature.size(), annotation);
        }
        notificationService.reportStrategy(
                strategy,
                candle.getFigi(),
                "Date|smaSlowest|smaSlow|smaFast|emaFast|ema2|bye|sell|position|deadLineBottom|deadLineTop|investBottom|investTop|smaTube|strategy|average|averageBottom|averageTop|openPrice",
                "{} | {} | {} | {} | {} | {} | {} | {} |||||||by {}||||",
                notificationService.formatDateTime(candle.getDateTime()),
                candle.getClosingPrice(),
                candle.getOpenPrice(),
                candle.getHighestPrice(),
                candle.getLowestPrice(),
                candle.getClosingPrice(),
                profit,
                loss,
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
        notificationService.reportStrategy(
                strategy,
                candle.getFigi(),
                "Date|smaSlowest|smaSlow|smaFast|emaFast|ema2|bye|sell|position|deadLineBottom|deadLineTop|investBottom|investTop|smaTube|strategy|average|averageBottom|averageTop|openPrice",
                "{} | {} | {} | {} | | {} |  | |  |  |  |  |  |  |sell||||",
                notificationService.formatDateTime(candle.getDateTime()),
                candle.getClosingPrice(),
                candle.getOpenPrice(),
                candle.getHighestPrice(),
                candle.getLowestPrice(),
                candle.getClosingPrice()
        );
        if (sellCriteria.getStopLossPercent() != null && profitPercent.floatValue() < -1 * sellCriteria.getStopLossPercent()) {
            return true;
        }
        if (sellCriteria.getExitProfitPercent() != null
                && profitPercent.floatValue() > sellCriteria.getExitProfitPercent()
        ) {
            return true;
        }
        return false;
    }

    @Override
    public AStrategy.Type getStrategyType() {
        return AStrategy.Type.instrumentFactorialByFiat;
    }

    private FactorialData findBestFactorialInPast(AInstrumentByFiatFactorialStrategy strategy, CandleDomainEntity candle) {
        var candleList = candleHistoryService.getCandlesByFigiByLength(candle.getFigi(),
                candle.getDateTime(), strategy.getFactorialHistoryLength(), strategy.getInterval());
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
            for (var i = 0; i < candleList.size() - strategy.getFactorialLength() * 2 * size; i++) {
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
                Float diffValue = 0f;
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
                    /*curDiff +=
                            Math.abs(((modelCandle.getHighestPrice().floatValue() - modelCandle.getLowestPrice().floatValue())
                                    //- (modelCandlePrev.getHighestPrice().floatValue() - modelCandlePrev.getLowestPrice().floatValue())
                            )
                            - ((testCandle.getHighestPrice().floatValue() - testCandle.getLowestPrice().floatValue())
                                    //- (testCandlePrev.getHighestPrice().floatValue() - testCandlePrev.getLowestPrice().floatValue())
                            ));*/
                    curDiff +=
                            Math.abs(((modelCandle.getClosingPrice().floatValue() - modelCandlePrev.getClosingPrice().floatValue())/modelCandle.getClosingPrice().floatValue())
                            - (testCandle.getClosingPrice().floatValue() - testCandlePrev.getClosingPrice().floatValue())/testCandle.getClosingPrice().floatValue());
                    curDiff +=
                            Math.abs(((modelCandle.getOpenPrice().floatValue() - modelCandlePrev.getOpenPrice().floatValue())/modelCandle.getOpenPrice().floatValue())
                                    - (testCandle.getOpenPrice().floatValue() - testCandlePrev.getOpenPrice().floatValue())/testCandle.getOpenPrice().floatValue());

                    curDiff +=
                            Math.abs(((modelCandle.getOpenPrice().floatValue() - modelCandle.getClosingPrice().floatValue())/modelCandle.getOpenPrice().floatValue())
                                    - (testCandle.getOpenPrice().floatValue() - testCandle.getClosingPrice().floatValue())/testCandle.getOpenPrice().floatValue());
                    curDiff +=
                            Math.abs(((modelCandle.getHighestPrice().floatValue() - modelCandle.getLowestPrice().floatValue())/modelCandle.getHighestPrice().floatValue())
                                    - (testCandle.getHighestPrice().floatValue() - testCandle.getLowestPrice().floatValue())/testCandle.getHighestPrice().floatValue());
                    curDiffValue += Math.abs(modelCandle.getVolume() - testCandle.getVolume());
                    diff += curDiff * (0.5f + j / (2f * strategy.getFactorialLength()));
                    diffValue += curDiffValue * curDiffValue;
                    if (j == 1 || j == strategy.getFactorialLength() - 1) {
                        info += " + " + curDiff + "(" + testCandle.getDateTime() + " with " + modelCandle.getDateTime() + ")";
                    }
                    testCandlePrev = testCandle;
                    modelCandlePrev = modelCandle;
                }
                if (null == bestDiff || bestDiff > diff) {
                    startCandleI = i;
                    bestSize = size;
                    bestDiff = diff;
                    bestInfo = info;
                }
                factorialDataList.add(FactorialData.builder()
                        .i(i)
                        .size(size)
                        .length(strategy.getFactorialLength())
                        .diff(diff)
                        .diffValue(diffValue)
                        .candleList(candleList.subList(i, i + 1))
                        .candleListFeature(candleList.subList(i, i + 1))
                        .candleListPast(candleList.subList(i, i + 1))
                        .info(info)
                        .build());
            }
        }
        if (null == bestDiff) {
            return null;
        }
        var maxDiff = factorialDataList.stream().mapToDouble(value -> value.getDiff().doubleValue()).max().orElse(-1);
        var maxDiffValue = factorialDataList.stream().mapToDouble(value -> value.getDiffValue().doubleValue()).max().orElse(-1);
        var iBest = IntStream.range(0, factorialDataList.size()).reduce((i, j) ->
                0.90 * factorialDataList.get(i).getDiff()/maxDiff + 0.10 * factorialDataList.get(i).getDiffValue()/maxDiffValue
                        > 0.90 * factorialDataList.get(j).getDiff()/maxDiff + 0.10 * factorialDataList.get(j).getDiffValue()/maxDiffValue
                ? j : i
        ).getAsInt();

        startCandleI = factorialDataList.get(iBest).getI();
        bestSize = factorialDataList.get(iBest).getSize();
        bestDiff = factorialDataList.get(iBest).getDiff();
        bestInfo = factorialDataList.get(iBest).getInfo();

        bestInfo += " diffAverage=" + (factorialDataList.stream().mapToDouble(value -> value.getDiff().doubleValue()).average().orElse(-1));

        log.info("Select from {} best diff={} i={}", candleList.get(0).getDateTime(), bestDiff, startCandleI);
        var res = FactorialData.builder()
                .size(bestSize)
                .length(strategy.getFactorialLength())
                .diff(bestDiff)
                .candleList(candleList.subList(startCandleI, startCandleI + strategy.getFactorialLength() * bestSize))
                .candleListFeature(candleList.subList(startCandleI + strategy.getFactorialLength() * bestSize, startCandleI + strategy.getFactorialLength() * bestSize * 2))
                .candleListPast(candleList.subList(candleList.size() - strategy.getFactorialLength(), candleList.size()))
                .info(bestInfo)
                .build();
        log.info("Select from {} best diff={} i={}, {} ({}) {} ({}) {} ({})", candleList.get(0).getDateTime(), bestDiff, startCandleI,
                res.candleList.get(0).getDateTime(), res.candleList.size(),
                res.candleListFeature.get(0).getDateTime(), res.candleListFeature.size(),
                res.candleListPast.get(0).getDateTime(), res.candleListPast.size()
        );
        return res;
    }
}
