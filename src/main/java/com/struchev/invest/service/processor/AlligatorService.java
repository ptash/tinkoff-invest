package com.struchev.invest.service.processor;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.expression.Date;
import com.struchev.invest.service.candle.ICandleHistoryService;
import com.struchev.invest.service.notification.INotificationService;
import com.struchev.invest.service.order.IOrderService;
import com.struchev.invest.strategy.AStrategy;
import com.struchev.invest.strategy.alligator.AAlligatorStrategy;
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

    private synchronized void setOrderBooleanData(AStrategy strategy, CandleDomainEntity candle, String key, Boolean value)
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
        log.trace("isShouldBuy {} {} begin", candle.getFigi(), candle.getDateTime());
        var annotation = "";
        var resBuy = false;
        var resBuyMax = false;
        var resBuyMin = false;

        var candleOrig = candle;
        var candlePrevList = getCandlesByFigiByLength(candle.getFigi(), candle.getDateTime(), 1, strategy.getInterval());
        candle = candlePrevList.get(0);
        annotation += " origDate=" + printDateTime(candleOrig.getDateTime());
        annotation += " date=" + printDateTime(candle.getDateTime());

        var currentPrice = candle.getLowestPrice();
        var purchaseRate = candle.getClosingPrice();
        annotation += " currentPrice=" + printPrice(currentPrice);
        annotation += " purchaseRate=" + printPrice(purchaseRate);

        var blue = getAlligatorBlue(candle.getFigi(), candle.getDateTime(), strategy);
        var red = getAlligatorRed(candle.getFigi(), candle.getDateTime(), strategy);
        var green = getAlligatorGreen(candle.getFigi(), candle.getDateTime(), strategy);

        log.trace("isShouldBuy {} {} blue={} red={} green={}", candle.getFigi(), candle.getDateTime(), blue, red, green);

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

        Double smaUp = null;
        Double smaDown = null;
        var isTrendUp = true;
        String trendName = "null";
        Double sma = null;

        var smaList = getSma(candle.getFigi(), candle.getDateTime(), strategy.getSmaLength(), strategy.getInterval(), CandleDomainEntity::getMedianPrice, 1);
        sma = smaList != null && smaList.size() > 1 ? smaList.get(1) : null;
        var smaPrev = smaList != null && smaList.size() > 0 ? smaList.get(0) : null;
        if (sma != null && smaPrev != null) {
            isTrendUp = smaPrev <= sma;
            trendName = isTrendUp ? "UP" : "DOWN";
            annotation += " isTrendUp=" + isTrendUp + " " + printPrice(smaPrev) + "<" + printPrice(sma);
            if (isTrendUp) {
                smaUp = sma;
                if (strategy.getTrendUpLength() > 1) {
                    annotation += " TrendUpLength=" + strategy.getTrendUpLength();
                    var smaListPrev = getSma(candle.getFigi(), candle.getDateTime(), strategy.getSmaLength(), strategy.getInterval(), CandleDomainEntity::getMedianPrice, strategy.getTrendUpLength());
                    for (var iSma = 1; iSma < smaListPrev.size(); iSma++) {
                        var isTrendUpPrev = smaListPrev.get(iSma - 1) <= smaListPrev.get(iSma);
                        if (!isTrendUpPrev) {
                            annotation += " isTrendUp=false iSma=" + iSma + " " + printPrice(smaListPrev.get(iSma - 1)) + "<=" + printPrice(smaListPrev.get(iSma));
                            isTrendUp = false;
                            trendName = "DOWN";
                            break;
                        } else {
                            //annotation += " iSma=" + iSma + " " + printPrice(smaListPrev.get(iSma - 1)) + ">";
                            //if (iSma == (smaListPrev.size() - 1)) {
                            //    annotation += " iSma=" + (iSma + 1) + " " + printPrice(smaListPrev.get(iSma));
                            //}
                        }
                    }
                }
            } else {
                smaDown = sma;
            }
        }

        log.trace("isShouldBuy {} {} sma={}", candle.getFigi(), candle.getDateTime(), sma);

        CandleDomainEntity lastFMaxCandle;
        CandleDomainEntity beginMonthCandle = null;
        var isIgnoreSkip = false;
        var lastFMaxCandleData = getLastFMaxCandle(candle.getFigi(), candle.getDateTime(), strategy, strategy.getFMaxCandleCountFromEnd());
        if (null != lastFMaxCandleData) {
            //annotation += " lastFMaxCandle " + lastFMaxCandleData.getAnnotation();
        }
        if (null != lastFMaxCandleData && null != lastFMaxCandleData.getFMaxCandle()) {
            lastFMaxCandle = lastFMaxCandleData.getFMaxCandle();
            beginMonthCandle = lastFMaxCandleData.getBeginCandle();
            isIgnoreSkip = lastFMaxCandleData.getIsUpPrev() && isTrendUp;
            annotation += " isUpPrev=" + isIgnoreSkip;
            annotation += " lastFMaxCandle=" + printDateTime(lastFMaxCandle.getDateTime());
            /*annotation += " ann=" + lastFMaxCandleData.getAnnotation();
            for (var i = 0; i < lastFMaxCandleData.getMaxMaxCandleList().size(); i++) {
                annotation += " i=" + i;
                annotation += " maxFMax=" + printDateTime(lastFMaxCandleData.getMaxMaxCandleList().get(i).getDateTime());
            }
            for (var i = 0; i < lastFMaxCandleData.getMaxCandleList().size(); i++) {
                annotation += " ii=" + i;
                annotation += " fMax=" + printDateTime(lastFMaxCandleData.getMaxCandleList().get(i).getDateTime());
            }*/
        } else {
            lastFMaxCandle = null;
        }
        log.trace("isShouldBuy {} {} lastFMaxCandle={}", candle.getFigi(), candle.getDateTime(), lastFMaxCandle);

        BigDecimal waitMax = null;
        BigDecimal waitMax2 = null;
        BigDecimal waitMaxBuy = null;
        BigDecimal delta = null;
        BigDecimal deltaDown = null;
        BigDecimal priceWanted = null;
        BigDecimal priceWantedOrig = null;
        Double limitPrice = null;
        Double stopLoss = null;
        Double zs = null;
        Integer stepMaxLength = null;
        Integer stepMinLength = null;
        Integer stepAvLength = null;
        var average = getAveragePercent(candle.getFigi(), candle.getDateTime(), strategy);

        log.trace("isShouldBuy {} {} average={}", candle.getFigi(), candle.getDateTime(), average);

        if (green != null && blue != null && strategy.isReverse()) {
            CandleDomainEntity lastFMinCandle;
            var startCandle = strategy.isCandleOrigInMinCandleList() ? candleOrig : candle;
            var lastFMinCandleData = getLastFMinCandle(
                    candle.getFigi(),
                    startCandle.getDateTime(),
                    strategy,
                    strategy.getFMaxCandleCountFromEnd()
            );
            if (null != lastFMinCandleData) {
                //annotation += " " + lastFMinCandleData.getAnnotation();
                lastFMinCandle = lastFMinCandleData.getFMaxCandle();
            } else {
                lastFMinCandle = null;
            }
            var isDownDelta = true;
            if (null != lastFMinCandle) {
                annotation += " lastFMinCandle=" + printDateTime(lastFMinCandle.getDateTime());
                waitMax = lastFMinCandle.getLowestPrice();
                delta = deltaDown = lastFMinCandle.getLowestPrice().subtract(lastFMinCandle.getClosingPrice()).abs()
                        .min(lastFMinCandle.getLowestPrice().subtract(lastFMinCandle.getOpenPrice()).abs());
                var candleListMin = candleHistoryService.getCandlesByFigiBetweenDateTimes(candle.getFigi(), lastFMinCandle.getDateTime(), startCandle.getDateTime(), strategy.getInterval());
                var minIntervalCandle = candleListMin.stream().reduce((first, second) ->
                        first.getLowestPrice().compareTo(second.getLowestPrice()) < 0 ? first : second
                ).orElse(null);
                var minFirstIntervalCandle = candleListMin.stream().filter(v ->
                        v.getLowestPrice().compareTo(lastFMinCandle.getLowestPrice()) < 0
                ).findFirst().orElse(null);
                Float newGreenPercentAverage = null;
                Double blueMax = null;
                Double greenMax = null;
                Boolean isMax2 = false;
                CandleDomainEntity maxAverageCandle = null;
                if (
                        minIntervalCandle != lastFMinCandle
                        && minFirstIntervalCandle != null
                        && minIntervalCandle.getLowestPrice().compareTo(lastFMinCandle.getLowestPrice()) < 0
                ) {
                    maxAverageCandle = minFirstIntervalCandle;
                    isMax2 = true;
                } else if (strategy.getReverseMaxLength() > 0) {
                    maxAverageCandle = lastFMinCandle;
                }

                if (
                        strategy.isMaxDeltaByMinMax()
                        && null != lastFMinCandleData.getMaxMaxCandleListAll()
                        && lastFMinCandleData.getMaxMaxCandleListAll().size() > 1
                ) {
                    var deltaAverage = 0.;
                    var deltaAverageDown = 0.;
                    var deltaCount = 0;
                    var stepLengthAverage = 0;
                    List<Integer> stepLengthArray = new ArrayList<Integer>();
                    annotation += " allSize=" + lastFMinCandleData.getMaxMaxCandleListAll().size();
                    for (var i = 1; i < lastFMinCandleData.getMaxMaxCandleListAll().size(); i++) {
                        if (lastFMinCandleData.getMaxMaxCandleListAll().get(i - 1).getDateTime().compareTo(lastFMinCandle.getDateTime()) > 0) {
                            continue;
                        }
                        var candleList = candleHistoryService.getCandlesByFigiBetweenDateTimes(
                                candle.getFigi(),
                                lastFMinCandleData.getMaxMaxCandleListAll().get(i).getDateTime(),
                                lastFMinCandleData.getMaxMaxCandleListAll().get(i - 1).getDateTime(),
                                strategy.getInterval()
                        );
                        annotation += " i=" + i;
                        annotation += " from=" + printDateTime(lastFMinCandleData.getMaxMaxCandleListAll().get(i).getDateTime());
                        annotation += " to=" + lastFMinCandleData.getMaxMaxCandleListAll().get(i - 1).getDateTime();
                        if (candleList.size() > 2) {
                            var maxCandleDelta = candleList.stream().reduce((first, second) ->
                                    first.getHighestPrice().compareTo(second.getHighestPrice()) > 0 ? first : second
                            ).orElse(null);
                            var curDelta  =
                                    maxCandleDelta.getHighestPrice().doubleValue()
                                            //- lastFMinCandleData.getMaxMaxCandleListAll().get(i - 1).getLowestPrice().doubleValue()
                                            - lastFMinCandleData.getMaxMaxCandleListAll().get(i).getLowestPrice().doubleValue()
                                    ;
                            var curDeltaDown  =
                                    lastFMinCandleData.getMaxMaxCandleListAll().get(i).getLowestPrice().doubleValue()
                                            //- lastFMinCandleData.getMaxMaxCandleListAll().get(i - 1).getLowestPrice().doubleValue()
                                            - lastFMinCandleData.getMaxMaxCandleListAll().get(i - 1).getLowestPrice().doubleValue()
                                    ;
                            annotation += " maxC=" + printDateTime(maxCandleDelta.getDateTime());
                            annotation += " curDelta=" + printPrice(curDelta);
                            annotation += " curDeltaDown=" + printPrice(curDeltaDown);
                            annotation += " size=" + candleList.size();
                            deltaAverage += curDelta;
                            deltaAverageDown += curDeltaDown;
                            deltaCount++;
                            stepLengthAverage += candleList.size();
                            stepLengthArray.add(candleList.size());
                        }
                    }
                    if (deltaCount > 0) {
                        delta = BigDecimal.valueOf(deltaAverage/deltaCount);
                        annotation += " delta=" + printPrice(delta);
                        waitMax2 = waitMax.add(delta.multiply(BigDecimal.valueOf(strategy.getBuyWaitMaxDeltaK())));
                        if (strategy.isWaitMaxBuyByMinMax()) {
                            if (deltaAverageDown > 0) {
                                deltaDown = BigDecimal.valueOf(deltaAverageDown / deltaCount);
                                annotation += " deltaDown=" + printPrice(deltaDown);
                                waitMaxBuy = waitMax.subtract(deltaDown.multiply(BigDecimal.valueOf(strategy.getBuyWaitMaxBuyDeltaK())));
                            } else {
                                // восходящий тренд
                                isDownDelta = false;
                                deltaDown = BigDecimal.valueOf(deltaAverageDown / deltaCount).abs();
                                annotation += " deltaDown=" + printPrice(deltaDown);
                                waitMax2 = waitMax.add(deltaDown.multiply(BigDecimal.valueOf(strategy.getBuyWaitMaxDeltaK())));
                                waitMaxBuy = waitMax.subtract(deltaDown.multiply(BigDecimal.valueOf(strategy.getBuyWaitMaxBuyDeltaK())));
                            }
                        } else {
                            waitMaxBuy = waitMax.subtract(delta.multiply(BigDecimal.valueOf(strategy.getBuyWaitMaxBuyDeltaK())));
                        }
                        stepMaxLength = (int) Math.floor((stepLengthArray.stream().mapToInt(c -> c).max().orElse(0)) * 1.2);
                        stepMinLength = (int) Math.ceil((stepLengthArray.stream().mapToInt(c -> c).min().orElse(0)) * 0.8);
                        stepAvLength = (int) Math.floor(stepLengthArray.stream().mapToInt(c -> c).average().orElse(0) / stepLengthArray.size());
                        annotation += " stepMaxLength=" + stepMaxLength;
                        annotation += " stepMinLength=" + stepMinLength;
                        annotation += " stepAvLength=" + stepAvLength;
                    }
                }
                if (null != maxAverageCandle && null == waitMax2 && !strategy.isMaxDeltaByMinMaxOnly()) {
                    blueMax = getAlligatorBlue(candle.getFigi(), maxAverageCandle.getDateTime(), strategy);
                    greenMax = getAlligatorGreen(candle.getFigi(), maxAverageCandle.getDateTime(), strategy);
                    annotation += " greenMax=" + printPrice(greenMax);
                    if (blueMax != null && greenMax != null) {
                        var averageMax = getAveragePercent(candle.getFigi(), maxAverageCandle.getDateTime(), strategy);
                        var zsMax = greenMax + (blueMax - greenMax);
                        Float newGreenPercentMax = (float) ((100.f * (zsMax - greenMax) / Math.abs(greenMax)));
                        annotation += " averageMax=" + printPrice(averageMax);
                        Float newGreenPercentAverageMax = (float) (newGreenPercentMax / averageMax);
                        annotation += " newGreenPercentAverageMax=" + printPrice(newGreenPercentAverageMax);
                        delta = delta.max(BigDecimal.valueOf(Math.abs(greenMax - blueMax) / newGreenPercentAverageMax));

                        if (strategy.getBuyWaitMaxFromGreenBlueK() > 0) {
                            delta = delta.max(BigDecimal.valueOf(strategy.getBuyWaitMaxFromGreenBlueK() * Math.max(
                                    Math.abs(greenMax - maxAverageCandle.getMedianPrice().doubleValue()),
                                    Math.abs(blueMax - maxAverageCandle.getMedianPrice().doubleValue())
                            )));
                        }

                        zs = green + (green - blue) * 1.618;
                        Float newGreenPercent = (float) ((100.f * (zs - green) / Math.abs(green)));
                        annotation += " newGreenPercent=" + printPrice(newGreenPercent);
                        annotation += " average=" + printPrice(average);
                        newGreenPercentAverage = (float) Math.abs(newGreenPercent / average);
                        annotation += " newGreenPercentAverage=" + printPrice(newGreenPercentAverage);
                    }
                    deltaDown = delta;
                    annotation += " delta=" + printPrice(delta);
                    waitMax2 = waitMax.add(delta.multiply(BigDecimal.valueOf(strategy.getBuyWaitMaxDeltaK())));
                    waitMaxBuy = waitMax.subtract(delta.multiply(BigDecimal.valueOf(strategy.getBuyWaitMaxBuyDeltaK())));
                }

                BigDecimal maxPrice = null;
                priceWanted = null;
                var isMaxPriceDown = false;
                if (strategy.getReverseMaxLength() > 0) {
                    annotation += " minLength=" + candleListMin.size();

                    var reverseMinLength = 0;
                    if (stepMinLength != null) {
                        reverseMinLength = stepMinLength;
                    }
                    var reverseMaxLength = strategy.getReverseMaxLength();
                    if (reverseMaxLength > 0 && stepMaxLength != null) {
                        reverseMaxLength = stepMaxLength * 2;
                    }
                    var reverseUpMinLength = strategy.getReverseUpMinLength();
                    if (reverseUpMinLength > 0 && stepMaxLength != null && stepMaxLength < reverseUpMinLength) {
                        //if (isDownDelta) {
                            reverseUpMinLength = stepMaxLength;
                        //} else {
                        //    reverseUpMinLength = stepMinLength / 2;
                        //}
                    }
                    annotation += " reverseMinLength=" + reverseMinLength;
                    annotation += " reverseUpMinLength=" + reverseUpMinLength;
                    annotation += " reverseMaxLength=" + reverseMaxLength;
                    if (
                            candleListMin.size() < reverseMaxLength
                            && null != waitMaxBuy
                            && candleListMin.size() >= reverseMinLength
                            && isDownDelta
                            //&& purchaseRate.compareTo(waitMaxBuy) < 0
                    ) {
                        isMaxPriceDown = true;
                        maxPrice = waitMaxBuy;
                        annotation += " maxPrice=" + printPrice(maxPrice) + " OK by ReverseLength=" + reverseMaxLength;
                        //annotation += " SELL OK by ReverseLength=" + reverseMaxLength;
                        //resBuy = true;
                    }

                    if (
                            //!resBuy
                            //&& !isMax2
                            null != waitMaxBuy
                            && minIntervalCandle.getLowestPrice().compareTo(waitMaxBuy) >= 0
                            && reverseUpMinLength > 0
                            && candleListMin.size() < reverseMaxLength
                            && candleListMin.size() > reverseUpMinLength
                            && null != waitMax2
                            && candleListMin.size() >= reverseMinLength
                            && !lastFMinCandleData.getIsfMaxCandleOver()
                            //&& purchaseRate.compareTo(waitMax2) < 0
                    ) {
                        var isOk = true;
                        if (strategy.isMinLowestPriceUnderMinSameTrend()) {
                            var blueMin = getAlligatorBlue(candle.getFigi(), lastFMinCandle.getDateTime(), strategy);
                            var greenMin = getAlligatorGreen(candle.getFigi(), lastFMinCandle.getDateTime(), strategy);
                            isOk = lastFMinCandle.getHighestPrice().doubleValue() < Math.min(blueMin, greenMin);
                            annotation += " isOK=" + isOk + " " + printPrice(lastFMinCandle.getHighestPrice()) + "<" + printPrice(Math.min(blueMin, greenMin));
                        }
                        if (isOk) {
                            isMaxPriceDown = false;
                            maxPrice = waitMax2;
                            if (strategy.isPriceWantedAsMaxPrice()) {
                                var averagePrice = candleListMin.stream().mapToDouble(c -> c.getMedianPrice().doubleValue()).average().orElse(maxPrice.doubleValue());
                                maxPrice = maxPrice.min(BigDecimal.valueOf(averagePrice));
                                annotation += " averagePrice=" + printPrice(averagePrice);
                            }
                            annotation += " maxPrice=" + printPrice(maxPrice) + " OK by ReverseMinLength=" + reverseUpMinLength;
                            //annotation += " SELL OK by ReverseMinLength=" + reverseUpMinLength;
                            //resBuy = true;
                        }
                    }

                    if (null != maxPrice) {
                        if (purchaseRate.compareTo(maxPrice) < 0) {
                            annotation += " SELL OK by ReverseLength";
                            resBuy = true;
                            if (strategy.isPriceWantedAsMaxPrice()) {
                                priceWanted = maxPrice;
                            }
                        }
                        if (!resBuy && strategy.isPriceWantedAsMaxPrice()) {
                            priceWanted = maxPrice;
                            if (candleOrig.getLowestPrice().compareTo(maxPrice) < 0) {
                                annotation += " SELL OK by Orig ReverseLength";
                                resBuy = true;
                            }
                        }
                    }
                } else if (
                        isMax2
                        && null != waitMax
                        && null != waitMaxBuy
                ){
                    if (
                            purchaseRate.compareTo(waitMax) < 0
                            && purchaseRate.compareTo(waitMaxBuy) > 0
                            && purchaseRate.doubleValue() < green
                            && waitMax.doubleValue() < greenMax
                            && waitMax2.doubleValue() > greenMax
                    ) {
                        annotation += " SELL OK by waitMax";
                        resBuy = true;
                    }
                }

                if (resBuy && strategy.isBuyMaxOnlySmaUp() && "DOWN" == trendName) {
                    resBuy = true;
                    annotation += " SKIP by trend DOWN";
                }
                if (resBuy) {
                    if (null == priceWanted) {
                        priceWanted = purchaseRate;
                        annotation += " by purchase priceWanted=" + printPrice(priceWanted);
                    }
                    var realLimitPercent = waitMax2.subtract(waitMax).abs().doubleValue() * strategy.getReverseStopLossK() * 100. / waitMax.abs().doubleValue();
                    var realLimitPrice = priceWanted.doubleValue() + realLimitPercent * priceWanted.abs().doubleValue() / 100.;
                    stopLoss = priceWanted.doubleValue() - 2 * waitMaxBuy.subtract(waitMax).abs().doubleValue();
                    if (
                            //isMaxPriceDown
                            strategy.isUpLimitPriceToWaitMax()
                            && waitMax.doubleValue() > realLimitPrice
                    ) {
                        realLimitPrice = waitMax.doubleValue();
                        realLimitPercent = 100. * (realLimitPrice - priceWanted.doubleValue()) / priceWanted.abs().doubleValue();
                    }
                    annotation += " realLimitPercent=" + printPrice(realLimitPercent);
                    annotation += " realLimitPrice=" + printPrice(realLimitPrice);
                    annotation += " stopLoss=" + printPrice(stopLoss);
                    if (!isIgnoreSkip && realLimitPercent < strategy.getBuyMinProfitPercent()) {
                        if (strategy.isDownPriceWantedToMinProfitPercent()) {
                            var isDown = true;
                            var newRealLimitPercent = strategy.getBuyMinProfitPercent();
                            if (realLimitPrice < waitMax2.doubleValue() && strategy.isUpLimitPriceToMinProfitPercent()) {
                                var realLimitPrice2 = waitMax2.doubleValue();
                                var realLimitPercent2 = 100. * (realLimitPrice2 - priceWanted.doubleValue()) / priceWanted.abs().doubleValue();
                                annotation += " TRY realLimitPercent2=" + printPrice(realLimitPercent2);
                                //if (realLimitPercent2 > strategy.getBuyMinProfitPercent().doubleValue()) {
                                    annotation += " UP LIMITPERCENT";
                                    newRealLimitPercent = (float) ((realLimitPercent2 + strategy.getBuyMinProfitPercent().doubleValue()) / 2.);
                                    isDown = false;
                                //}
                            }

                            if (isDown) {
                                var priceWantedOld = priceWanted;
                                var percentDelta = strategy.getBuyMinProfitPercent() - realLimitPercent;
                                priceWanted = BigDecimal.valueOf(priceWanted.doubleValue() - priceWanted.abs().doubleValue() * percentDelta / 100.);

                                realLimitPercent = newRealLimitPercent;
                                realLimitPrice = priceWanted.doubleValue() + realLimitPercent * priceWanted.abs().doubleValue() / 100.;
                                annotation += " UP realLimitPercent=" + printPrice(realLimitPercent);
                                annotation += " realLimitPrice=" + printPrice(realLimitPrice);
                                stopLoss -= priceWantedOld.subtract(priceWanted).abs().doubleValue();
                                annotation += " stopLoss=" + printPrice(stopLoss);
                            } else {
                                var realLimitPriceOld = realLimitPrice;

                                realLimitPercent = newRealLimitPercent;
                                realLimitPrice = priceWanted.doubleValue() + realLimitPercent * priceWanted.abs().doubleValue() / 100.;
                                annotation += " UP realLimitPercent=" + printPrice(realLimitPercent);
                                annotation += " UP realLimitPrice=" + printPrice(realLimitPrice);
                                stopLoss -= Math.abs(realLimitPriceOld - realLimitPrice);
                                annotation += " stopLoss=" + printPrice(stopLoss);
                            }
                        } else {
                            annotation += " SKIP ProfitPercent=" + strategy.getBuyMinProfitPercent();
                            resBuy = false;
                        }
                    }

                    if (resBuy && !isIgnoreSkip && newGreenPercentAverage != null && newGreenPercentAverage > strategy.getMaxGreenPercent()) {
                        annotation += " SKIP GreenPercentAverage=" + strategy.getMaxGreenPercent();
                        resBuy = false;
                    }
                    if (priceWanted.compareTo(candleOrig.getLowestPrice()) < 0) {
                        annotation += " SKIP by priceWanted " + printPrice(priceWanted) + "<" + printPrice(candleOrig.getLowestPrice());
                        resBuy = false;
                    }
                    if (priceWanted.compareTo(candleOrig.getHighestPrice()) > 0) {
                        var priceWantedDown = priceWanted.doubleValue();
                        if (strategy.getDownFromPriceWantedK() > 0) {
                            priceWantedDown = priceWanted.doubleValue() - deltaDown.doubleValue() * strategy.getDownFromPriceWantedK();
                            annotation += " priceWantedDown=" + printPrice(priceWantedDown);
                            annotation += " + deltaDown=" + deltaDown + "*" + printPrice(strategy.getDownFromPriceWantedK());
                            priceWanted = candleOrig.getHighestPrice();
                            annotation += " priceWanted=" + printPrice(priceWanted);
                        }
                        if (priceWantedDown > candleOrig.getHighestPrice().doubleValue()) {
                            annotation += " SKIP by priceWanted DOWN " + printPrice(priceWanted) + ">" + printPrice(candleOrig.getHighestPrice());
                            resBuy = false;
                        } else if (strategy.isSkipDownMinHighestPrice()) {
                            var minHighestPrice = candleListMin.stream().mapToDouble(c -> c.getHighestPrice().doubleValue()).min().orElse(candleOrig.getHighestPrice().doubleValue());
                            annotation += " minHighestPrice=" + printPrice(minHighestPrice);
                            if (priceWantedDown > minHighestPrice) {
                                annotation += " SKIP by priceWanted MIN DOWN " + printPrice(priceWanted) + ">" + printPrice(minHighestPrice);
                                resBuy = false;
                            }
                        }
                    }
                    limitPrice = realLimitPrice;
                    if (strategy.isRevMaxRev()) {
                        annotation += " SKIP by isRevMaxRev ";
                        resBuy = false;
                    }
                    if (resBuy) {
                        setOrderBigDecimalData(strategy, candle, "limitPrice", BigDecimal.valueOf(realLimitPrice));
                        setOrderBigDecimalData(strategy, candle, "limitPercent", BigDecimal.valueOf(realLimitPercent));
                        setOrderBigDecimalData(strategy, candle, "stopLoss", BigDecimal.valueOf(stopLoss));
                        setOrderBigDecimalData(strategy, candle, "priceWanted", priceWanted);
                        if (strategy.getLimitPriceDownStepLength() > 0) {
                            var downDelta = Math.abs(realLimitPrice - priceWanted.doubleValue()) * strategy.getLimitPriceDownProfitK();
                            var stepLength = strategy.getLimitPriceDownStepLength();
                            if (null != stepMaxLength && stepMaxLength <= stepLength) {
                                stepLength = stepMaxLength - 1;
                            }
                            setOrderBigDecimalData(strategy, candle, "LimitPriceStepLength", BigDecimal.valueOf(stepLength));
                            setOrderBigDecimalData(strategy, candle, "LimitPriceDownDelta", BigDecimal.valueOf(downDelta));
                        }
                        //if (strategy.isBuyMaxOnlySmaUp()) {
                        //    var stopLossUp = stopLoss - waitMaxBuy.subtract(waitMax).abs().doubleValue();
                        //    annotation += " stopLossUp=" + printPrice(stopLossUp);
                        //    setOrderBigDecimalData(strategy, candle, "stopLossUp", BigDecimal.valueOf(stopLossUp));
                        //}
                    }
                }
                priceWantedOrig = maxPrice; // для графиков
                if (strategy.isRevMaxRev() && null != waitMax && null != waitMax2) {
                    priceWanted = null;
                    limitPrice = null;
                    stopLoss = null;
                    var greenCur = Math.max(Math.max(green, blue), red);
                    if (greenCur > waitMax2.doubleValue()) {
                        priceWanted = BigDecimal.valueOf(greenCur + (waitMax2.doubleValue() - waitMaxBuy.doubleValue()));
                        limitPrice = greenCur;
                        var maxIntervalCandle = candleListMin.stream().reduce((first, second) ->
                                first.getHighestPrice().compareTo(second.getHighestPrice()) > 0 ? first : second
                        ).orElse(null);
                        annotation += " priceWanted=" + printPrice(priceWanted);
                        annotation += " maxCPrice=" + printPrice(maxIntervalCandle.getHighestPrice());
                        if (maxIntervalCandle.getHighestPrice().doubleValue() < priceWanted.doubleValue()) {
                            var priceWantedNew = BigDecimal.valueOf(waitMax2.doubleValue() + (waitMax2.doubleValue() - waitMax.doubleValue()));
                            annotation += " new priceWantedNew=" + printPrice(priceWantedNew);
                            if (priceWantedNew.doubleValue() > greenCur) {
                                priceWanted = priceWantedNew;
                                limitPrice = waitMax.doubleValue();
                                annotation += " new priceWanted=" + printPrice(priceWanted);
                            }
                        }
                        stopLoss = candle.getHighestPrice().doubleValue() + Math.abs(priceWanted.doubleValue() - limitPrice);
                    } else if (greenCur < waitMax2.doubleValue() && greenCur > waitMax.doubleValue()) {
                        var deltaU = waitMax2.doubleValue() - greenCur;
                        var deltaD = greenCur - waitMax.doubleValue();
                        annotation += " deltaU=" + printPrice(deltaU);
                        annotation += " deltaD=" + printPrice(deltaD);
                        if (deltaU < deltaD && (deltaD / deltaU) < 3) {
                            priceWanted = BigDecimal.valueOf(waitMax2.doubleValue());
                            limitPrice = waitMaxBuy.doubleValue();
                            stopLoss = candle.getHighestPrice().doubleValue() + Math.abs(priceWanted.doubleValue() - limitPrice);
                        }
                    }
                    if (null != priceWanted && candleOrig.getHighestPrice().compareTo(priceWanted) > 0) {
                        annotation += " BUY REV OK by priceWanted";
                        resBuy = true;

                        var realLimitPercent = priceWanted.subtract(BigDecimal.valueOf(limitPrice)).abs().doubleValue() * strategy.getReverseStopLossK() * 100. / priceWanted.abs().doubleValue();
                        annotation += " realLimitPercent=" + printPrice(realLimitPercent);
                        if (realLimitPercent < strategy.getBuyMinProfitPercent()) {
                            annotation += " SKIP by MinProfitPercent=" + strategy.getBuyMinProfitPercent();
                            resBuy = false;
                        }

                        var percentDown = Math.abs((waitMax.doubleValue() - waitMaxBuy.doubleValue()) / waitMax.doubleValue()) * 100.;
                        annotation += " percentDown=" + printPrice(percentDown);
                        if (percentDown < strategy.getBuyMinProfitPercent()) {
                            annotation += " SKIP by MinProfitPercent=" + strategy.getBuyMinProfitPercent();
                            resBuy = false;
                        }

                        //if (resBuy && candleListMin.size() < stepAvLength) {
                        //    annotation += " SKIP by stepAvLength=" + stepAvLength;
                        //    resBuy = false;
                        //}

                        if (null != priceWanted && priceWanted.compareTo(candleOrig.getLowestPrice()) < 0) {
                            priceWanted = candleOrig.getLowestPrice();
                            annotation += " new priceWanted=" + printPrice(priceWanted);
                        }

                        if (resBuy) {
                            setOrderBooleanData(strategy, candle, "isReverse", true);
                            setOrderBigDecimalData(strategy, candle, "limitPrice", BigDecimal.valueOf(limitPrice));
                            setOrderBigDecimalData(strategy, candle, "limitPercent", BigDecimal.valueOf(realLimitPercent));
                            setOrderBigDecimalData(strategy, candle, "stopLoss", BigDecimal.valueOf(stopLoss));
                            setOrderBigDecimalData(strategy, candle, "priceWanted", priceWanted);
                            if (strategy.getLimitPriceDownStepLength() > 0) {
                                var downDelta = Math.abs(limitPrice - priceWanted.doubleValue()) * strategy.getLimitPriceDownProfitK();
                                var stepLength = strategy.getLimitPriceDownStepLength();
                                if (null != stepMaxLength && stepMaxLength <= stepLength) {
                                    stepLength = stepMaxLength - 1;
                                }
                                setOrderBigDecimalData(strategy, candle, "LimitPriceStepLength", BigDecimal.valueOf(stepLength));
                                setOrderBigDecimalData(strategy, candle, "LimitPriceDownDelta", BigDecimal.valueOf(-downDelta));
                            }
                        }
                    }
                    priceWantedOrig = priceWanted;
                }
            }
        }

        log.trace("isShouldBuy {} {} isReverse resBuy={}", candle.getFigi(), candle.getDateTime(), resBuy);

        if (null != lastFMaxCandle && green != null && blue != null && !strategy.isReverse()) {
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
                annotation += " maxIntervalCandle=" + printDateTime(maxIntervalCandle.getDateTime());
                var blueMax = getAlligatorBlue(candle.getFigi(), maxFirstIntervalCandle.getDateTime(), strategy);
                var greenMax = getAlligatorGreen(candle.getFigi(), maxFirstIntervalCandle.getDateTime(), strategy);
                if (blueMax != null && greenMax != null) {
                    var averageMax = getAveragePercent(candle.getFigi(), maxFirstIntervalCandle.getDateTime(), strategy);
                    var zsMax = greenMax + (greenMax - blueMax);
                    Float newGreenPercentMax = (float) ((100.f * Math.abs(zsMax - greenMax) / Math.abs(greenMax)));
                    annotation += " averageMax=" + printPrice(averageMax);
                    Float newGreenPercentAverage = (float) (newGreenPercentMax / averageMax);
                    annotation += " newGreenPercentAverageMax=" + printPrice(newGreenPercentAverage);
                    delta = delta.max(BigDecimal.valueOf(Math.abs(greenMax - blueMax) / newGreenPercentAverage));
                }
                annotation += " delta=" + printPrice(delta);
                waitMax2 = waitMax.add(delta.multiply(BigDecimal.valueOf(strategy.getBuyWaitMaxDeltaK())));
                var isMax2 = maxIntervalCandle.getHighestPrice().compareTo(waitMax2) > 0;
                var isUp = currentPrice.doubleValue() > blue
                        && green > red
                        && red > blue;
                waitMaxBuy = waitMax.subtract(delta.multiply(BigDecimal.valueOf(strategy.getBuyWaitMaxBuyDeltaK())));
                if (
                        isMax2
                        && (isUp || isIgnoreSkip)
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
                } else if (isMax2 || !strategy.isBuyOnlyAfterMax2()) {
                    if (
                            currentPrice.compareTo(waitMaxBuy) < 0
                            && isUp
                    ) {
                        setOrderBigDecimalData(strategy, candle, "priceWanted", currentPrice.max(lastFMaxCandle.getClosingPrice().max(lastFMaxCandle.getOpenPrice())));
                        annotation += " OK by DOWN";
                        resBuyMin = true;
                        isIgnoreSkip = false;
                    }
                }
            }
        }

        log.trace("isShouldBuy {} {} not isReverse resBuy={}", candle.getFigi(), candle.getDateTime(), resBuy);

        if (green != null && blue != null && !strategy.isReverse()) {
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
            if (!isIgnoreSkip && !resBuyMax && currentPrice.doubleValue() > green && newGreenPercentAverage < strategy.getMinGreenPercent()) {
                annotation += " skip by percent<" + strategy.getMinGreenPercent();
                resBuy = false;
            }
            if (

                    currentPrice.doubleValue() < green
                    && (maxBuyPercentAverage != null && maxBuyPercentAverage > strategy.getMinGreenPercent())
                    && !resBuyMax
                    && !isIgnoreSkip
            ) {
                annotation += " skip by buy percent>" + strategy.getMinGreenPercent();
                resBuy = false;
            }
            if (
                    null != strategy.getMaxGreenPercent()
                    && !resBuyMax
                    && newGreenPercentAverage > strategy.getMaxGreenPercent()
                    && !isIgnoreSkip
            ) {
                annotation += " skip by percent>" + strategy.getMaxGreenPercent();
                resBuy = false;
            }
            if (
                    resBuyMax
                    && newGreenPercentAverage < strategy.getMinGreenPercent()
                    && !isIgnoreSkip
            ) {
                annotation += " skip max by percent<" + strategy.getMinGreenPercent();
                resBuy = false;
            }
        }
        if (resBuy && strategy.isBuyMaxOnlySmaUp() && !isTrendUp) {
            annotation += " SKIP max by trend down";
            resBuy = false;
        }

        log.trace("isShouldBuy {} {} after skip resBuy={}", candle.getFigi(), candle.getDateTime(), resBuy);

        AlligatorMouth curAlligatorMouth = null;
        AlligatorMouth curAlligatorMouthOrig = null;
        var alligatorMouthSizeOffset = 0;
        if (resBuy && !strategy.isReverse()) {
            var alligatorAverage = getAlligatorLengthAverage(candle.getFigi(), candle.getDateTime(), strategy);
            var lastFMaxCandleFirst = getLastFMaxCandle(candle.getFigi(), candle.getDateTime(), strategy, null).getFMaxCandle();
            annotation += " lastFMaxCandleFirst=" + printDateTime(lastFMaxCandleFirst.getDateTime());
            if (!strategy.isAlligatorMouthOffset() || lastFMaxCandleFirst.getDateTime().equals(lastFMaxCandle.getDateTime())) {
                curAlligatorMouthOrig = curAlligatorMouth = getAlligatorMouth(candle.getFigi(), candle.getDateTime(), strategy, null);
            } else {
                var lastFMaxCandlePrev = getLastFMaxCandle(candle.getFigi(), candle.getDateTime(), strategy, strategy.getFMaxCandleCountFromEnd() + 1).getFMaxCandle();
                annotation += " lastFMaxCandlePrev=" + printDateTime(lastFMaxCandlePrev.getDateTime());
                curAlligatorMouthOrig = getAlligatorMouth(candle.getFigi(), candle.getDateTime(), strategy, null);
                annotation += " MonthBeginOrig=" + printDateTime(curAlligatorMouthOrig.getCandleBegin().getDateTime());
                curAlligatorMouth = getAlligatorMouth(candle.getFigi(), candle.getDateTime(), strategy, lastFMaxCandle.getDateTime());
                alligatorMouthSizeOffset = curAlligatorMouthOrig.getSize() - curAlligatorMouth.getSize();
                setOrderBigDecimalData(strategy, candle, "lastFMaxCandleEpochSecond", BigDecimal.valueOf(
                        strategy.isSellMonthLengthFromBegin() ? beginMonthCandle.getDateTime().toEpochSecond() : lastFMaxCandle.getDateTime().toEpochSecond()
                ));
            }
            setOrderBigDecimalData(strategy, candle, "alligatorMouthSizeOffset", BigDecimal.valueOf(alligatorMouthSizeOffset));
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

        log.trace("isShouldBuy {} {} after skip AlligatorMouth resBuy={}", candle.getFigi(), candle.getDateTime(), resBuy);

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

        if (resBuy && !strategy.isReverse()) {
            var alligatorAverage = getAlligatorLengthAverage(candle.getFigi(), candle.getDateTime(), strategy);
            var orderAlligatorMouth = curAlligatorMouth;
            if (strategy.isLimitPriceFromMouthOrig()) {
                orderAlligatorMouth = curAlligatorMouthOrig;
            }
            var greenMonthBegin = getAlligatorGreen(candle.getFigi(), orderAlligatorMouth.getCandleBegin().getDateTime(), strategy);
            var startPrice = greenMonthBegin;
            annotation += " startPrice=" + printPrice(startPrice);
            Double limitPercent;
            if (strategy.getLimitPercentByCandle() > 0) {
                annotation += " limitPercentByCandle=" + printPrice(strategy.getLimitPercentByCandle());
                limitPercent = Math.max(1, (alligatorAverage.getSize()))
                        * strategy.getLimitPercentByCandle() * average;
            } else {
                var alligatorPrice = alligatorAverage.getPrice();
                annotation += " alligatorAveragePrice=" + printPrice(alligatorPrice);
                if (strategy.isLimitPercentByPriceAlligator()) {
                    alligatorPrice = alligatorAverage.getPriceAlligator();
                    annotation += " alligatorAveragePrice=" + printPrice(alligatorPrice);
                }
                var limitPercentByCandle = Math.abs(100. * alligatorPrice / alligatorAverage.getSize() / startPrice);
                annotation += " limitPercentByCandle=" + printPrice(limitPercentByCandle);
                limitPercent = Math.max(1, (alligatorAverage.getSize()))
                        * limitPercentByCandle;
            }
            annotation += " limitPercent=" + printPrice(limitPercent);
            Double profitLimit = Math.abs((startPrice.doubleValue() / 100.) * limitPercent);
            profitLimit -= delta.doubleValue() * strategy.getLimitDeltaK();
            limitPrice = startPrice.doubleValue() + profitLimit;
            Double prevLimitPrice = null;

            Float newLimitPercent = (float) ((100.f * (limitPrice.floatValue() - purchaseRate.floatValue()) / Math.abs(purchaseRate.floatValue())));
            annotation += " limitPrice=" + printPrice(limitPrice);
            annotation += " newLimitPercent=" + printPrice(newLimitPercent);
            if (strategy.getLimitPercentUp1() > 0) {
                if (newLimitPercent < 0) {
                    prevLimitPrice = limitPrice;
                    limitPrice = startPrice.doubleValue() + profitLimit * strategy.getLimitPercentUp1();
                    newLimitPercent = (float) ((100.f * (limitPrice.floatValue() - purchaseRate.floatValue()) / Math.abs(purchaseRate.floatValue())));
                    annotation += " limitPrice=" + printPrice(limitPrice);
                    annotation += " NEW newLimitPercent=" + printPrice(newLimitPercent) + "(" + strategy.getLimitPercentUp1() + ")";
                }
            }
            if (strategy.getLimitPercentUp2() > 0) {
                if (newLimitPercent < 0) {
                    prevLimitPrice = limitPrice;
                    limitPrice = startPrice.doubleValue() + profitLimit * strategy.getLimitPercentUp2();
                    newLimitPercent = (float) ((100.f * (limitPrice.floatValue() - purchaseRate.floatValue()) / Math.abs(purchaseRate.floatValue())));
                    annotation += " limitPrice=" + printPrice(limitPrice);
                    annotation += " NEW newLimitPercent=" + printPrice(newLimitPercent) + "(" + strategy.getLimitPercentUp2() + ")";
                }
            }
            if (strategy.getLimitPercentUp3() > 0) {
                if (newLimitPercent < 0) {
                    prevLimitPrice = limitPrice;
                    limitPrice = startPrice.doubleValue() + profitLimit * strategy.getLimitPercentUp3();
                    newLimitPercent = (float) ((100.f * (limitPrice.floatValue() - purchaseRate.floatValue()) / Math.abs(purchaseRate.floatValue())));
                    annotation += " limitPrice=" + printPrice(limitPrice);
                    annotation += " NEW newLimitPercent=" + printPrice(newLimitPercent) + "(" + strategy.getLimitPercentUp3() + ")";
                }
            }
            //Float newLimitPercentAverage = (float) (newLimitPercent / average);

            annotation += " origProfitPercent=" + strategy.getSellLimitCriteriaOrig().getExitProfitPercent();
            var realLimitPercent = newLimitPercent * strategy.getLimitCorrectionK();
            var realLimitPrice = (realLimitPercent * Math.abs(purchaseRate.floatValue())) / 100. + purchaseRate.floatValue();
            annotation += " realLimitPercent=" + printPrice(realLimitPercent);
            priceWanted = purchaseRate;
            if (realLimitPercent < strategy.getSellLimitCriteriaOrig().getExitProfitPercent()) {
                var percentDelta = strategy.getSellLimitCriteriaOrig().getExitProfitPercent() - realLimitPercent;
                priceWanted = BigDecimal.valueOf(purchaseRate.doubleValue() - purchaseRate.abs().doubleValue() * percentDelta / 100.);
                annotation += " percentDelta=" + printPrice(percentDelta);
                annotation += " priceWanted=" + printPrice(priceWanted);
                if (prevLimitPrice != null && priceWanted.doubleValue() < prevLimitPrice) {
                    priceWanted = BigDecimal.valueOf(prevLimitPrice);
                    annotation += " priceWanted=" + printPrice(priceWanted);
                }
            }
            if (
                    priceWanted.compareTo(candleOrig.getHighestPrice()) > 0
                    || priceWanted.compareTo(candleOrig.getLowestPrice()) < 0
            ) {
                annotation += " SKIP by priceWanted";
                resBuy = false;
            }
            if (resBuy) {
                setOrderBigDecimalData(strategy, candle, "limitPrice", BigDecimal.valueOf(realLimitPrice));
                setOrderBigDecimalData(strategy, candle, "limitPercent", BigDecimal.valueOf(realLimitPercent));
                setOrderBigDecimalData(strategy, candle, "priceWanted", purchaseRate);
            }

            if (null != strategy.getDayTimeEndTrading(candle.getDateTime())) {
                var lengthToDayEnd = getLengthToDayEnd(candle.getFigi(), candle.getDateTime(), strategy.getDayTimeEndTrading(candle.getDateTime()), strategy.getInterval());
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
        if (
                resBuy
                && strategy.isSkipBuyUnderSma()
                && priceWanted != null
                && priceWanted.doubleValue() < sma
        ) {
            annotation += " SKIP under sma";
            resBuy = false;
        }

        log.trace("isShouldBuy {} {} after skip DayEnd resBuy={}", candle.getFigi(), candle.getDateTime(), resBuy);

        if (
                strategy.isMoveStopLossByTrySellByTrend()
                || strategy.isSkipBySmaNearGreenBlue()
                || strategy.isSkipBySmaFarGreenBlue()
                || strategy.isReverse()
        ) {
            if (
                    strategy.isSkipBySmaNearGreenBlue() || strategy.isSkipBySmaFarGreenBlue()
                    && sma != null
            ) {
                var trendDelta = Math.abs(green - blue);
                var trendDeltaPercent = 100. * trendDelta / candle.getClosingPrice().abs().doubleValue();
                annotation += " trendDelta=" + printPrice(trendDelta);
                annotation += " trendDeltaPercent=" + printPrice(trendDeltaPercent);
                if (average != null && trendDeltaPercent < average) {
                    trendDeltaPercent = average;
                    trendDelta = trendDeltaPercent * candle.getClosingPrice().abs().doubleValue() / 100.;
                    annotation += " new trendDelta=" + printPrice(trendDelta);
                    annotation += " new trendDeltaPercent=" + printPrice(trendDeltaPercent);
                }
                annotation += " sma " + printPrice(sma) + " near greenBlue "
                        + printPrice((Math.min(green, blue) - trendDelta)) + ":"
                        + printPrice((Math.max(green, blue) + trendDelta));

                if (
                        strategy.isSkipBySmaNearGreenBlue()
                        && sma >= (Math.min(green, blue) - trendDelta)
                        && sma <= (Math.max(green, blue) + trendDelta)
                        && !isIgnoreSkip
                ) {
                    annotation += " SKIP by SMA near greenBlue";
                    resBuy = false;
                }
                if (
                        strategy.isSkipBySmaFarGreenBlue()
                        && (sma < (Math.min(green, blue) - trendDelta)
                        || sma > (Math.max(green, blue) + trendDelta))
                        && !isIgnoreSkip
                ) {
                    annotation += " SKIP by SMA far greenBlue";
                    resBuy = false;
                }
            }
        }

        log.trace("isShouldBuy {} {} after skip SMA near resBuy={}", candle.getFigi(), candle.getDateTime(), resBuy);

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
                    candleOrig,
                    "Date|open|high|low|close|ema2|profit|loss|limitPrice|lossAvg|deadLineTop|investBottom|investTop|smaTube|strategy"
                            + "|emaBlue1|emaRed|emaGreen|emaBlue|max|min|zs|waitMax|maxBuy|stopLoss|waitMax2|isDayEnd|smaUp|smaDown|priceWanted|trendUp|trendDown|purchaseRate"
                            + "|priceWantedOrig",
                    "{} | {} | {} | {} | {} | | {} | {} | {} | {} | ||||by {}"
                            + "| {} | {} | {} | {} | {} | {} | {} | {} | {} | {} | {} | {} | {} | {}| {}| {}| {}|"
                            + "|{}",
                    printDateTime(candleOrig.getDateTime()),
                    candleOrig.getOpenPrice(),
                    candleOrig.getHighestPrice(),
                    candleOrig.getLowestPrice(),
                    candleOrig.getClosingPrice(),
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
                    waitMax == null ? "" : waitMax,
                    waitMaxBuy == null ? "" : waitMaxBuy,
                    stopLoss == null ? "" : stopLoss,
                    waitMax2 == null ? "" : waitMax2,
                    isDayEnd ? candleOrig.getLowestPrice().subtract(candleOrig.getLowestPrice().abs().multiply(BigDecimal.valueOf(0.01))) : "",
                    smaUp != null ? smaUp : "",
                    smaDown != null ? smaDown : "",
                    priceWanted != null ? printPrice(priceWanted) : "",
                    trendName == "UP" ? sma + Math.abs(sma) * 0.001 : "",
                    trendName == "DOWN" ? sma - Math.abs(sma) * 0.001 : "",
                    priceWantedOrig != null ? printPrice(priceWantedOrig) : ""
            );
        }
        log.trace("isShouldBuy {} {} end resBuy={}", candle.getFigi(), candle.getDateTime(), resBuy);
        return resBuy;
    }

    @Override
    public boolean isShouldSell(AAlligatorStrategy strategy, CandleDomainEntity candle, BigDecimal purchaseRate) {
        log.trace("isShouldSell {} {} begin", candle.getFigi(), candle.getDateTime());
        var annotation = "";
        var res = false;

        var candlePrevList = getCandlesByFigiByLength(candle.getFigi(), candle.getDateTime(), 1, strategy.getInterval());
        var candlePrev = candlePrevList.get(0);

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
        Boolean isStopLossForce = strategy.isStopLossForce();
        if (green != null) {
            stopLoss = red;
            zs = green + (green - blue) * 1.618;
        }

        Double limitPrice = null;
        AlligatorMouthAverage alligatorAverage = null;
        AlligatorMouth curAlligatorMouth = null;

        log.trace("isShouldSell {} {} before findActiveByFigiAndStrategy res={}", candle.getFigi(), candle.getDateTime(), res);

        var order = orderService.findActiveByFigiAndStrategy(candle.getFigi(), strategy);
        var lastFMaxCandleEpochSecond = order.getDetails().getCurrentPrices().getOrDefault("lastFMaxCandleEpochSecond", null);
        OffsetDateTime lastFMaxCandleDateTime = null;
        if (null != lastFMaxCandleEpochSecond) {
            lastFMaxCandleDateTime = OffsetDateTime.ofInstant(
                    Instant.ofEpochSecond(lastFMaxCandleEpochSecond.longValue()),
                    ZoneId.systemDefault()
            );
            annotation += " lastFMaxCandleDateTime=" + printDateTime(lastFMaxCandleDateTime);
        }
        Double average = null;
        if (green != null && blue != null) {
            alligatorAverage = getAlligatorLengthAverage(candle.getFigi(), candle.getDateTime(), strategy);
            curAlligatorMouth = getAlligatorMouth(candle.getFigi(), candle.getDateTime(), strategy, lastFMaxCandleDateTime);
            annotation += " MonthBegin=" + printDateTime(curAlligatorMouth.getCandleBegin().getDateTime());
            annotation += " MonthEnd=" + printDateTime(curAlligatorMouth.getCandleEnd().getDateTime());
        }
        Integer curAlligatorLength = null;
        if (green != null && blue != null && !strategy.isReverse()) {
            var stopLossForce = blue - Math.abs(red - blue);
            Float newGreenPercent = (float) ((100.f * (zs - green) / Math.abs(green)));
            average = getAveragePercent(candle.getFigi(), candle.getDateTime(), strategy);
            annotation += " average=" + printPrice(average);
            Float newGreenPercentAverage = (float) (newGreenPercent / average);

            curAlligatorLength = curAlligatorMouth.getSize();
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
            var orderAlligatorMouth = getAlligatorMouth(candle.getFigi(), order.getPurchaseDateTime(), strategy, lastFMaxCandleDateTime);
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

            annotation += " newGreenPercent=" + printPrice(newGreenPercent);
            annotation += " newGreenPercentAverage=" + printPrice(newGreenPercentAverage);
        }

        Double smaUp = null;
        Double smaDown = null;
        var isTrendUp = true;
        var smaList = getSma(candle.getFigi(), candle.getDateTime(), strategy.getSmaLength(), strategy.getInterval(), CandleDomainEntity::getMedianPrice, 1);
        var sma = smaList != null && smaList.size() > 1 ? smaList.get(1) : null;
        var smaPrev = smaList != null && smaList.size() > 0 ? smaList.get(0) : null;
        if (sma != null && smaPrev != null) {
            isTrendUp = smaPrev <= sma;
            annotation += " isTrendUp=" + isTrendUp + " " + printPrice(smaPrev) + "<" + printPrice(sma);
            if (isTrendUp) {
                smaUp = sma;
            } else {
                smaDown = sma;
                if (strategy.getTrendUpLength() > 1) {
                    annotation += " TrendUpLength=" + strategy.getTrendUpLength();
                    var smaListPrev = getSma(candle.getFigi(), candle.getDateTime(), strategy.getSmaLength(), strategy.getInterval(), CandleDomainEntity::getMedianPrice, strategy.getTrendUpLength());
                    for (var iSma = 1; iSma < smaListPrev.size(); iSma++) {
                        var isTrendUpPrev = smaListPrev.get(iSma - 1) <= smaListPrev.get(iSma);
                        if (isTrendUpPrev) {
                            annotation += " isTrendUp=true iSma=" + iSma + " " + printPrice(smaListPrev.get(iSma - 1)) + "<=" + printPrice(smaListPrev.get(iSma));
                            isTrendUp = true;
                        }
                    }
                }
            }
        }

        log.trace("isShouldSell {} {} after average average={}", candle.getFigi(), candle.getDateTime(), res);

        if (strategy.isReverse()) {
            stopLoss = order.getDetails().getCurrentPrices().getOrDefault("stopLoss", BigDecimal.ZERO).doubleValue();
            if (isTrendUp) {
                var stopLossUp = order.getDetails().getCurrentPrices().getOrDefault("stopLossUp", null);
                if (null != stopLossUp) {
                    stopLoss = stopLossUp.doubleValue();
                }
            }
        }

        var sellLimitCriteria = strategy.getSellLimitCriteria(candle.getFigi());
        //limitPrice = order.getDetails().getCurrentPrices().getOrDefault("limitPrice", BigDecimal.ZERO).doubleValue();
        var limitPercent = order.getDetails().getCurrentPrices().getOrDefault("limitPercent", BigDecimal.ZERO);
        annotation += " limitPercent" + limitPercent;
        Float newLimitPercent = limitPercent.floatValue();
        limitPrice = (double) (purchaseRate.floatValue() + Math.abs(purchaseRate.floatValue() * newLimitPercent / 100.f));
        var downStepLength = order.getDetails().getCurrentPrices().getOrDefault("LimitPriceStepLength", BigDecimal.ZERO).intValue();
        if (downStepLength > 0) {
            var downDelta = order.getDetails().getCurrentPrices().getOrDefault("LimitPriceDownDelta", BigDecimal.ZERO).doubleValue();
            var candleList = candleHistoryService.getCandlesByFigiBetweenDateTimes(candle.getFigi(), order.getPurchaseDateTime(), candle.getDateTime(), strategy.getInterval());
            if (candleList != null) {
                var intervalNum = candleList.size() / downStepLength;
                if (intervalNum > 0) {
                    var limitPercentInit = order.getDetails().getCurrentPrices().getOrDefault("limitPercentInit", limitPercent);
                    annotation += " limitPercentInit=" + printPrice(limitPercentInit);
                    var limitPriceInit = (double) (purchaseRate.floatValue() + Math.abs(purchaseRate.doubleValue() * limitPercentInit.doubleValue() / 100.));
                    var limitPriceOld = limitPrice;
                    limitPrice = limitPriceInit - intervalNum * downDelta;
                    annotation += " limitPrice=" + printPrice(limitPrice);
                    if (intervalNum > strategy.isLimitPriceDownStepNoChange()) {
                        if (strategy.isLimitPriceDownByMinBlue()) {
                            var firstStepCandle = candleList.get(intervalNum * downStepLength - 1);
                            annotation += " firstStepCandle=" + printDateTime(firstStepCandle.getDateTime());
                            var firstStepCandleBlue = getAlligatorBlue(candle.getFigi(), firstStepCandle.getDateTime(), strategy);
                            if (null != firstStepCandleBlue) {
                                annotation += " firstStepCandleBlue=" + printPrice(firstStepCandleBlue);
                                if (limitPrice > firstStepCandleBlue) {
                                    limitPrice = firstStepCandleBlue;
                                    annotation += " new limitPrice=" + printPrice(limitPrice);
                                }
                            }
                        }
                        if (strategy.isLimitPriceDownMaxStep() > 0) {
                            var minMaxDownDelta = (limitPriceInit - stopLoss) / strategy.isLimitPriceDownMaxStep();
                            annotation += " minMaxDelta=" + printPrice(minMaxDownDelta);
                            var limitPriceByMaxStep = limitPriceInit - intervalNum * minMaxDownDelta;
                            annotation += " limitPriceByMaxStep=" + printPrice(limitPriceByMaxStep);
                            if (limitPrice > limitPriceByMaxStep) {
                                limitPrice = limitPriceByMaxStep;
                                annotation += " new limitPrice=" + printPrice(limitPrice);
                            }
                        }
                    }

                    var inDownLimit = true;
                    if (intervalNum <= strategy.isLimitPriceDownStepNoChange()) {
                        // проверим не касались ли уже лимитки пониженной
                        candleList.remove(candleList.size() - 1);
                        var maxPrice = candleList.stream().mapToDouble(c -> c.getHighestPrice().doubleValue()).max().orElse(purchaseRate.doubleValue());
                        annotation += " new maxPrice=" + printPrice(maxPrice);
                        if (maxPrice < limitPrice) {
                            annotation += " inDownLimit=FALSE";
                            inDownLimit = false;
                        }
                    }
                    if (inDownLimit) {
                        limitPercent = BigDecimal.valueOf((limitPrice - purchaseRate.doubleValue()) * 100. / purchaseRate.abs().doubleValue());
                        newLimitPercent = limitPercent.floatValue();

                        order.getDetails().getCurrentPrices().put("limitPercentInit", limitPercentInit);
                    } else {
                        limitPrice = limitPriceOld;
                    }
                }
                annotation += " downStepLength=" + downStepLength;
                annotation += " stopLossDownDelta=" + printPrice(downDelta);
                annotation += " intervalNum=" + intervalNum;
                annotation += " limitPercent=" + printPrice(limitPercent);
                annotation += " limitPrice=" + printPrice(limitPrice);
            }
        }

        //Float newLimitPercent = (float) ((100.f * (limitPrice.floatValue() - purchaseRate.floatValue()) / Math.abs(purchaseRate.floatValue())));
        //Float newLimitPercentAverage = (float) (newLimitPercent / average);

        var lastNewSellLimitBySell = order.getDetails().getCurrentPrices().getOrDefault("newSellLimitBySell", null);
        var lastNewStopLossBySell = order.getDetails().getCurrentPrices().getOrDefault("newStopLossBySell", null);
        var lastDateTimeBySell = order.getDetails().getDateTimes().getOrDefault("dateTimeBySell", null);
        if (null != lastNewStopLossBySell && lastDateTimeBySell.equals(candle.getDateTime())) {
            lastNewStopLossBySell = null; // свеча еще не закрыта
            lastDateTimeBySell = null;
            lastNewSellLimitBySell = null;
            order.getDetails().getCurrentPrices().put("newSellLimitBySell", null);
            orderService.updateDetailsCurrentPrice(order, "newStopLossBySell", null);
        }
        //annotation += " newLimitPercentAverage=" + printPrice(newLimitPercentAverage);
        annotation += " origProfitPercent=" + strategy.getSellLimitCriteriaOrig().getExitProfitPercent();

        if (lastNewSellLimitBySell != null) {
            limitPrice = lastNewSellLimitBySell.doubleValue();

            var sellLimitBySellDayEnd = order.getDetails().getCurrentPrices().getOrDefault("newSellLimitBySellDayEnd", null);
            if (null != sellLimitBySellDayEnd && null != strategy.getDayTimeEndLimitPriceByTrySell()) {
                var dayEndDateTime = strategy.getDayTimeEndLimitPriceByTrySell();
                var curDayEnd = candle.getDateTime()
                        .withHour(dayEndDateTime.getHour())
                        .withMinute(dayEndDateTime.getMinute())
                        .withSecond(dayEndDateTime.getSecond());
                annotation += " curDayEnd=" + printDateTime(curDayEnd);
                if (candle.getDateTime().compareTo(curDayEnd) >= 0) {
                    annotation += " TRY SELL day end";
                    limitPrice = sellLimitBySellDayEnd.doubleValue();
                }
            }

            newLimitPercent = (float) ((100.f * (limitPrice.floatValue() - purchaseRate.floatValue()) / Math.abs(purchaseRate.floatValue())));
            limitPercent = BigDecimal.valueOf(newLimitPercent);
            annotation += " new limitPrice=lastBySell=" + printPrice(limitPrice);
            annotation += " new newLimitPercent=" + printPrice(newLimitPercent);
        } else if (strategy.getLimitPriceDownStepLength() < 1) {
            annotation += " limitPrice=" + printPrice(limitPrice);
            annotation += " newLimitPercent=" + printPrice(newLimitPercent);
            var minProfitPercent = strategy.getSellLimitCriteriaOrig().getExitProfitPercent();
            if (null != strategy.getBuyMinProfitPercent()) {
                minProfitPercent = strategy.getBuyMinProfitPercent();
            }
            if (
                    newLimitPercent < minProfitPercent
            ) {
                newLimitPercent = strategy.getSellLimitCriteriaOrig().getExitProfitPercent();
                limitPercent = BigDecimal.valueOf(newLimitPercent);
                limitPrice = (double) (purchaseRate.floatValue() + Math.abs(purchaseRate.floatValue() * newLimitPercent / 100.f));
                annotation += " new limitPrice=" + printPrice(limitPrice);
                annotation += " new newLimitPercent=" + printPrice(newLimitPercent);
            }
        }

        if (
                true
            //&& newLimitPercent > strategy.getSellLimitCriteriaOrig().getExitProfitPercent()
            //&& newGreenPercentAverage > strategy.getSellLimitCriteriaOrig().getExitProfitPercent()
        ) {
            sellLimitCriteria.setExitProfitPercent(newLimitPercent);
            strategy.setSellLimitCriteria(candle.getFigi(), sellLimitCriteria);
            var prevLimitPercent = order.getDetails().getCurrentPrices().getOrDefault("limitPercent", null);
            if (prevLimitPercent == null || !prevLimitPercent.equals(limitPercent)) {
                orderService.updateDetailsCurrentPrice(order, "limitPercent", limitPercent);
            }
        } else {
            limitPrice = null;
        }

        var isStopLoss = false;

        if (null != lastNewStopLossBySell) {
            stopLoss = lastNewStopLossBySell.doubleValue();
            annotation += " newStopLoss=lastBySell=" + printPrice(stopLoss);
        }
        if (null != stopLoss) {
            if (!isStopLossForce && candle.getClosingPrice().doubleValue() < stopLoss) {
                annotation += " stop lost OK";
                res = true;
                isStopLoss = true;
            } else if (isStopLossForce && candle.getHighestPrice().doubleValue() < stopLoss) {
                annotation += " stop lost force OK";
                if (
                        strategy.isStopLossForcePrev()
                        && candlePrev.getHighestPrice().doubleValue() >= stopLoss
                ) {
                    annotation += " SKIP by prev";
                } else {
                    res = true;
                    isStopLoss = true;
                }
            }
        }

        if (res && strategy.isStopLossSkipByBuy()) {
            if (isShouldBuyInternal(strategy, candle, false)) {
                annotation += " skip by buy";
                res = false;
            }
        }

        log.trace("isShouldSell {} {} before sma res={}", candle.getFigi(), candle.getDateTime(), res);

        if (
                strategy.isMoveStopLossByTrySellByTrend()
                || strategy.isSkipSellSmaNearGreenBlue()
                || strategy.isReverse()
                || true
        ) {
            if (
                    (isTrendUp && strategy.isSmaNearGreenBlueIsTrendDown()
                    || strategy.isSkipSellSmaNearGreenBlue())
                    && sma != null
            ) {
                var trendDelta = Math.abs(green - blue);
                var trendDeltaPercent = 100. * trendDelta / candle.getClosingPrice().abs().doubleValue();
                annotation += " trendDelta=" + printPrice(trendDelta);
                annotation += " trendDeltaPercent=" + printPrice(trendDeltaPercent);
                if (average != null && trendDeltaPercent < average) {
                    trendDeltaPercent = average;
                    trendDelta = trendDeltaPercent * candle.getClosingPrice().abs().doubleValue() / 100.;
                    annotation += " new trendDelta=" + printPrice(trendDelta);
                    annotation += " new trendDeltaPercent=" + printPrice(trendDeltaPercent);
                }
                annotation += " sma " + printPrice(sma) + " near greenBlue "
                        + printPrice((Math.min(green, blue) - trendDelta)) + ":" + printPrice((Math.max(green, blue) + trendDelta));

                if (
                        sma >= (Math.min(green, blue) - trendDelta)
                        && sma <= (Math.max(green, blue) + trendDelta)
                ) {
                    if (strategy.isSkipSellSmaNearGreenBlue()) {
                        res = false;
                        annotation += " isSkipSell=true by near greenBlue";
                    }
                    if (strategy.isSmaNearGreenBlueIsTrendDown()) {
                        isTrendUp = false;
                        annotation += " isTrendUp=false by near greenBlue";
                    }
                }
            }
        }

        log.trace("isShouldSell {} {} before SellLimitPriceByTrySell res={}", candle.getFigi(), candle.getDateTime(), res);

        var profit = (float) ((100.f * (candle.getClosingPrice().floatValue() - purchaseRate.floatValue()) / Math.abs(purchaseRate.floatValue())));
        annotation += " profit=" + printPrice(profit);
        annotation += " alligatorMaxLength=" + (alligatorAverage.getSize() * strategy.getSkipMonthLengthKByTrySell());
        if (
                res
                && isStopLoss
                && strategy.getSellLimitPriceByTrySell() != null
                && green != null && blue != null
                && profit < strategy.getSkipProfitByTrySell()
                && curAlligatorLength != null
                && curAlligatorLength < alligatorAverage.getSize() * strategy.getSkipMonthLengthKByTrySell()
                && null == lastNewStopLossBySell
        ) {
            var startPoint = Math.max(green, blue);
            var stopLossDelta = startPoint - Math.min(candle.getClosingPrice().doubleValue(), Math.min(green, blue));
            var stopLossDeltaPercent = (double) ((100.f * (stopLossDelta) / Math.abs(startPoint)));
            annotation += " stopLossDelta=" + printPrice(stopLossDelta);
            annotation += " stopLossDeltaPercent=" + printPrice(stopLossDeltaPercent);
            if (stopLossDeltaPercent < strategy.getMinPercentDeltaByTrySell()) {
                stopLossDeltaPercent = strategy.getMinPercentDeltaByTrySell();
                stopLossDelta = Math.abs(startPoint) * stopLossDeltaPercent / 100.;
                annotation += " new min stopLossDelta=" + printPrice(stopLossDelta);
            }
            if (stopLossDeltaPercent > strategy.getMaxPercentDeltaByTrySell()) {
                stopLossDeltaPercent = strategy.getMaxPercentDeltaByTrySell();
                stopLossDelta = Math.abs(startPoint) * stopLossDeltaPercent / 100.;
                annotation += " new max stopLossDelta=" + printPrice(stopLossDelta);
            }

            var newStopLossBySell = startPoint - stopLossDelta * strategy.getSellLimitPriceByTrySell();
            var newPercentStopLossBySell = (double) ((100.f * (purchaseRate.doubleValue() - newStopLossBySell) / Math.abs(purchaseRate.doubleValue())));
            annotation += " newPercentStopLossBySell=" + printPrice(newPercentStopLossBySell);
            if (
                    !isTrendUp
                    && strategy.isMoveStopLossByTrySellByTrend()
                    && newPercentStopLossBySell > strategy.getMaxPercentStopLossByTrySell()
            ) {
                newPercentStopLossBySell = strategy.getMaxPercentStopLossByTrySell();
                newStopLossBySell = purchaseRate.doubleValue() - newPercentStopLossBySell * Math.abs(purchaseRate.doubleValue()) / 100.f;
                stopLossDelta = (startPoint - newStopLossBySell) / strategy.getSellLimitPriceByTrySell();
                annotation += " new newStopLossBySell=" + printPrice(newStopLossBySell);
                annotation += " new stopLossDelta=" + printPrice(stopLossDelta);
            }
            annotation += " startPoint=" + printPrice(startPoint);
            annotation += " stopLossDelta=" + printPrice(stopLossDelta);
            annotation += " newStopLossBySell=" + printPrice(newStopLossBySell);
            var isSellNow = false;
            if (null != strategy.getDayTimeEndLimitPriceByTrySell()) {
                var dayEndDateTime = strategy.getDayTimeEndLimitPriceByTrySell();
                var curDayEnd = candle.getDateTime()
                        .withHour(dayEndDateTime.getHour())
                        .withMinute(dayEndDateTime.getMinute())
                        .withSecond(dayEndDateTime.getSecond());
                annotation += " curDayEnd=" + printDateTime(curDayEnd);
                if (candle.getDateTime().compareTo(curDayEnd) >= 0) {
                    annotation += " SKIP by day end";
                    isSellNow = true;
                }
            }
            if (strategy.getSellLimitPriceByTrySell() != null) {
                var newSellLimitBySell = startPoint + stopLossDelta * strategy.getLimitPriceByTrySell();
                var newPercentSellLimitBySell = (double) ((100.f * (newSellLimitBySell - purchaseRate.doubleValue()) / Math.abs(purchaseRate.doubleValue())));
                annotation += " newPercentSellLimitBySell=" + printPrice(newPercentSellLimitBySell);
                annotation += " newSellLimitBySell=" + printPrice(newSellLimitBySell);
                if (
                        !isTrendUp
                        && strategy.isMoveStopLossByTrySellByTrend()
                        && newPercentSellLimitBySell > strategy.getMaxPercentLimitPriceByTrySell()
                ) {
                    newPercentSellLimitBySell = strategy.getMaxPercentLimitPriceByTrySell();
                    newSellLimitBySell = purchaseRate.doubleValue() + newPercentSellLimitBySell * Math.abs(purchaseRate.doubleValue()) / 100.f;
                    annotation += " new newSellLimitBySell=" + printPrice(newSellLimitBySell);
                }

                if (strategy.getAvgMaxCountLimitPriceByTrySell() > 0) {
                    var lastFMaxCandleData = getLastFMaxCandle(candle.getFigi(), candle.getDateTime(), strategy, strategy.getFMaxCandleCountFromEnd(), strategy.getAvgMaxCountLimitPriceByTrySell());
                    if (null != lastFMaxCandleData) {
                        var averageMax = lastFMaxCandleData.getMaxCandleList().stream().mapToDouble(c -> c.getHighestPrice().doubleValue()).average().orElse(0);
                        annotation += " averageMaxCount=" + lastFMaxCandleData.getMaxCandleList().size();
                        for (var i = 0; i < lastFMaxCandleData.getMaxCandleList().size(); i++) {
                            annotation += " i=" + i;
                            annotation += " date=" + printDateTime(lastFMaxCandleData.getMaxCandleList().get(i).getDateTime());
                        }
                        annotation += " averageMaxPrev " + printPrice(averageMax);
                        if (
                                averageMax > stopLoss
                                && strategy.isSkipSellByTrySell()
                                && !isTrendUp
                        ) {
                            annotation += " SKIP TrySell";
                            isSellNow = true;
                        } else {
                            if (averageMax < stopLoss) {
                                averageMax = (limitPrice + averageMax) / 2.;
                                annotation += " averageMaxMaxPrev=" + printPrice(averageMax);
                            }

                            var averageMaxMax = lastFMaxCandleData.getMaxCandleList().stream().mapToDouble(c -> c.getHighestPrice().doubleValue()).max().orElse(0);
                            averageMax = (averageMaxMax + averageMax) / 2.;
                            annotation += " averageMax=" + printPrice(averageMax);

                            if (averageMax < stopLoss) {
                                averageMax = (newSellLimitBySell + averageMax) / 2.;
                                annotation += " averageMaxMax=" + printPrice(averageMax);
                            }
                            if (averageMax > stopLoss) {
                                //if (averageMax < newSellLimitBySell && averageMax > purchaseRate.doubleValue()) {
                                annotation += " new SellLimitBySell";
                                newSellLimitBySell = averageMax;
                            }
                        }
                    }
                }
                if (!isSellNow) {
                    order.getDetails().getCurrentPrices().put("newSellLimitBySell", BigDecimal.valueOf(newSellLimitBySell));
                    order.getDetails().getCurrentPrices().put("newSellLimitBySellDayEnd", BigDecimal.valueOf(stopLoss));
                }
            }

            log.trace("isShouldSell {} {} before averageMinCount res={}", candle.getFigi(), candle.getDateTime(), res);

            if (!isSellNow) {
                if (strategy.getAvgMaxCountStopLossByTrySell() > 0) {
                    var lastFMaxCandleData = getLastFMinCandle(
                            candle.getFigi(),
                            order.getPurchaseDateTime(),
                            strategy,
                            strategy.getFMaxCandleCountFromEnd(),
                            strategy.getAvgMaxCountStopLossByTrySell()
                    );
                    if (null != lastFMaxCandleData && null != lastFMaxCandleData.getMaxCandleList()) {
                        var averageMin = lastFMaxCandleData.getMaxCandleList().stream().mapToDouble(c -> c.getLowestPrice().doubleValue()).average().orElse(0);
                        annotation += " averageMinCount=" + lastFMaxCandleData.getMaxCandleList().size();
                        for (var i = 0; i < lastFMaxCandleData.getMaxCandleList().size(); i++) {
                            annotation += " i=" + i;
                            annotation += " date=" + printDateTime(lastFMaxCandleData.getMaxCandleList().get(i).getDateTime());
                        }
                        annotation += " averageMin " + printPrice(averageMin);
                        //if (averageMin < newStopLossBySell) {
                        annotation += " new StopLossBySell";
                        newStopLossBySell = averageMin;
                        //}
                    }
                }
                order.getDetails().getDateTimes().put("dateTimeBySell", candle.getDateTime());
                orderService.updateDetailsCurrentPrice(order, "newStopLossBySell", BigDecimal.valueOf(newStopLossBySell));
                res = false;
            }
        }

        //if (
        //        limitPrice != null
        //        && candle.getClosingPrice().doubleValue() > limitPrice
        //) {
        //    annotation += " limit OK";
        //    res = true;
        //}

        log.trace("isShouldSell {} {} before DayEnd res={}", candle.getFigi(), candle.getDateTime(), res);

        var isDayEnd = false;
        if (!res && alligatorAverage != null && curAlligatorMouth != null) {
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

        notificationService.reportStrategyExt(
                res,
                strategy,
                candle,
                "Date|open|high|low|close|ema2|profit|loss|limitPrice|lossAvg|deadLineTop|investBottom|investTop|smaTube|strategy"
                        + "|emaBlue1|emaRed|emaGreen|emaBlue|max|min|zs|waitMax|maxBuy|stopLoss|waitMax2|isDayEnd|smaUp|smaDown|priceWanted|trendUp|trendDown|purchaseRate"
                        + "|priceWantedOrig",
                "{} | {} | {} | {} | {} | | {} | {} | {} | {} | ||||sell {}"
                        + "| {} | {} | {} | {} | {} | {} | {} ||| {}|| {}| {} | {}|||| {}|",
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
                isDayEnd ? candle.getLowestPrice().subtract(candle.getLowestPrice().abs().multiply(BigDecimal.valueOf(0.01))) : "",
                smaUp != null ? smaUp : "",
                smaDown != null ? smaDown : "",
                purchaseRate
        );
        log.trace("isShouldSell {} {} end res", candle.getFigi(), candle.getDateTime(), res);
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

    private AlligatorMouthAverage getAlligatorLengthAverage(
            String figi,
            OffsetDateTime currentDateTime,
            AAlligatorStrategy strategy
    ) {
        List<Double> ret = new ArrayList<Double>();
        List<Double> retPrice = new ArrayList<Double>();
        List<Double> retPriceAlligator = new ArrayList<Double>();
        var skipped = 0;
        String annotation = "";
        var mouthCur = getAlligatorMouth(figi, currentDateTime, strategy, null);
        var keyCur = strategy.getExtName() + figi + printDateTime(currentDateTime);
        annotation += " size=" + alligatorMouthAverageCashMap.size();
        annotation += " keyCur=" + keyCur;
        var v = getAlligatorMouthAverageFromCache(keyCur);
        if (v != null) {
            return v;
        }
        var candleList = getCandlesByFigiByLength(figi, currentDateTime, 1, strategy.getInterval());
        if (candleList != null && mouthCur.size > 1) {
            // можно в кеше поискать предыдущее значение
            var keyPrev = strategy.getExtName() + figi + printDateTime(candleList.get(0).getDateTime());
            annotation += " keyPrev=" + keyPrev;
            v = getAlligatorMouthAverageFromCache(keyPrev);
            if (v != null) {
                v.setAnnotation("from Cache " + keyPrev + " " + v.getAnnotation());
                addAlligatorMouthAverageToCache(keyCur, v);
                return v;
            }
        }
        currentDateTime = mouthCur.candleBegin.getDateTime();
        for(var i = 0; i < strategy.getMaxDeepAlligatorMouth() + skipped; i++) {
            var mouth = getAlligatorMouth(figi, currentDateTime, strategy, null);
            annotation += " i=" + i;
            annotation += " size=" + mouth.size;
            if (mouth.isFindBegin) {
                annotation += " begin=" + printDateTime(mouth.getCandleBegin().getDateTime());
            }
            if (mouth.isFindEnd) {
                annotation += " end=" + printDateTime(mouth.getCandleEnd().getDateTime());
            }
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
                    var greenBegin = getAlligatorGreen(figi, mouth.candleBegin.getDateTime(), strategy);
                    var blueBegin = getAlligatorBlue(figi, mouth.candleBegin.getDateTime(), strategy);
                    var greenEnd = getAlligatorGreen(figi, mouth.candleEnd.getDateTime(), strategy);
                    var blueEnd = getAlligatorBlue(figi, mouth.candleEnd.getDateTime(), strategy);
                    if (null != greenBegin && null != greenEnd && null != blueBegin && null != blueEnd) {
                        retPriceAlligator.add(
                                Math.max(Math.max(Math.max(greenBegin, blueBegin), greenEnd), blueEnd)
                                - Math.min(Math.min(Math.min(greenBegin, blueBegin), greenEnd), blueEnd)
                        );
                    }
                } else {
                    skipped++;
                }
            } else if (ret.size() > 0) {
                break;
            }
            currentDateTime = mouth.candleBegin.getDateTime();
        }
        var average = ret.stream().mapToDouble(a -> a).average().orElse(0);
        v = AlligatorMouthAverage.builder()
                .size((int) Math.round(Math.ceil(average)))
                .price(retPrice.stream().mapToDouble(a -> a).average().orElse(0))
                .priceAlligator(retPriceAlligator.stream().mapToDouble(a -> a).average().orElse(0))
                .annotation(annotation)
                .build();
        v.setAnnotation("orig " + v.getAnnotation());
        addAlligatorMouthAverageToCache(keyCur, v);
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
            AAlligatorStrategy strategy,
            OffsetDateTime beginDateTime
    ) {
        List<CandleDomainEntity> candleList;
        if (null == beginDateTime) {
            candleList = getCandlesByFigiByLength(figi, currentDateTime, strategy.getMaxDeep(), strategy.getInterval());
        } else {
            candleList = candleHistoryService.getCandlesByFigiBetweenDateTimes(figi, beginDateTime.minusMinutes(1), currentDateTime.minusMinutes(1), strategy.getInterval());
        }
        Boolean isUpCur = null;
        Integer size = 0;
        var resBuilder = AlligatorMouth.builder()
                .isFindBegin(false)
                .isFindEnd(false)
                .size(0);
        if (null == candleList) {
            return resBuilder.build();
        }
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
        var candleList = getCandlesByFigiByLength(figi, currentDateTime, strategy.getMaxDeep() + 1, strategy.getInterval());
        Double average = 0.0;
        var size = strategy.getMaxDeep();

        String key = "Average" + strategy.getName() + figi + currentDateTime;
        var ret = getCashedValueDouble(key);
        if (ret != null) {
            log.trace("Average value {} from cash by key {}", printPrice(ret), key);
            return ret;
        }

        var prevDateTime = candleList.get(candleList.size() - 1).getDateTime();
        String keyPrev = "Average" + strategy.getName() + figi + prevDateTime;
        var retPrev = getCashedValueDouble(keyPrev);
        log.trace("Average keys {} = {}, {} = {}", key, (ret != null ? printPrice(ret) : null), keyPrev, (retPrev != null ? printPrice(retPrev) : null));
        if (retPrev != null) {
            var iFirst = 0;
            var blue = getAlligatorBlue(figi, candleList.get(iFirst).getDateTime(), strategy);
            var green = getAlligatorGreen(figi, candleList.get(iFirst).getDateTime(), strategy);
            Double prevFirst = 0.0;
            if (blue != null && green != null) {
                prevFirst = 100 * Math.abs(blue - green) / Math.abs(green) / size;
            }
            var iLast = candleList.size() - 1;
            blue = getAlligatorBlue(figi, candleList.get(iLast).getDateTime(), strategy);
            green = getAlligatorGreen(figi, candleList.get(iLast).getDateTime(), strategy);
            Double itemLast = 0.0;
            if (blue != null && green != null) {
                itemLast = 100 * Math.abs(blue - green) / Math.abs(green) / size;
            }
            average = retPrev - prevFirst + itemLast;
            log.trace("Average value {} = {} - {} + {} from cash by prev key {}", printPrice(average), printPrice(retPrev), printPrice(prevFirst), printPrice(itemLast), keyPrev);
        } else {
            for (var i = 1; i < candleList.size(); i++) {
                var blue = getAlligatorBlue(figi, candleList.get(i).getDateTime(), strategy);
                var green = getAlligatorGreen(figi, candleList.get(i).getDateTime(), strategy);
                if (blue == null || green == null) {
                    size--;
                    continue;
                }
                var item = 100 * Math.abs(blue - green) / Math.abs(green) / size;
                average += item;
            }
        }
        addCashedValueDouble(key, average);
        log.trace("Average value saved {} = {}", key, printPrice(average));
        return average;
    }

    @Builder
    @Data
    public static class AlligatorMouthFMax {
        CandleDomainEntity fMaxCandle;
        Boolean isfMaxCandleOver;
        CandleDomainEntity beginCandle;
        List<CandleDomainEntity> maxCandleList;
        List<CandleDomainEntity> maxMaxCandleList;
        List<CandleDomainEntity> maxMaxCandleListAll;
        String annotation;
        Boolean isUpPrev;
    }

    private AlligatorMouthFMax  getLastFMaxCandle(
            String figi,
            OffsetDateTime currentDateTime,
            AAlligatorStrategy strategy,
            Integer countFromEnd
    ) {
        return getLastFMaxCandle(figi, currentDateTime, strategy, countFromEnd, 0);
    }

    private AlligatorMouthFMax  getLastFMaxCandle(
            String figi,
            OffsetDateTime currentDateTime,
            AAlligatorStrategy strategy,
            Integer countFromEnd,
            Integer countMaxCandle
    ) {
        var candleList = getCandlesByFigiByLength(figi, currentDateTime, strategy.getMaxDeep(), strategy.getInterval());
        if (candleList == null) {
            return null;
        }
        List<CandleDomainEntity> maxCandleList = new ArrayList<>();
        List<CandleDomainEntity> maxMaxCandleList = new ArrayList<>();
        var annotation = "";
        int iFindMax = 0;
        for (var i = candleList.size() - 1 - 2; i >= 2; i--) {
            var curCandleList = candleList.subList(i - 2, i + 3);
            var middleCandle = curCandleList.get(2);
            var blue = getAlligatorBlue(figi, middleCandle.getDateTime(), strategy);
            var red = getAlligatorRed(figi, middleCandle.getDateTime(), strategy);
            var green = getAlligatorGreen(figi, middleCandle.getDateTime(), strategy);
            if (
                    (blue == null
                    || !(
                        (blue < red && red < green)
                        || (
                                middleCandle.getHighestPrice().doubleValue() > red
                                && curCandleList.get(3).getHighestPrice().doubleValue() > red
                                && curCandleList.get(4).getHighestPrice().doubleValue() > red
                        )
                    ))
                    //&& countMaxCandle == 0
            ) {
                iFindMax = i;
                annotation += " first DOWN on " + i + " brg = " + (blue < red && red < green) + " or " + printDateTime(middleCandle.getDateTime()) + ":" + printDateTime(curCandleList.get(1).getDateTime()) +  "> red = " + (
                        middleCandle.getHighestPrice().doubleValue() > red
                                && curCandleList.get(3).getHighestPrice().doubleValue() > red
                                && curCandleList.get(4).getHighestPrice().doubleValue() > red
                        );
                break;
            }
            var curMaxCandle = curCandleList.stream().reduce((first, second) ->
                    first.getHighestPrice().compareTo(second.getHighestPrice()) > 0 ? first : second
            ).orElse(null);
            var isMax = middleCandle == curMaxCandle;
            if (
                    isMax
                    && (
                            middleCandle.getLowestPrice().doubleValue() > Math.max(blue, green)
                            //|| countMaxCandle > 0
                    )
            ) {
                maxCandleList.add(middleCandle);
            }
            if (countMaxCandle > 0 && maxCandleList.size() >= countMaxCandle) {
                break;
            }
        }

        CandleDomainEntity beginCandle = candleList.get(iFindMax);
        CandleDomainEntity fMaxCandle = null;
        if (
                null != countFromEnd
                && maxCandleList.size() >= countFromEnd
        ) {
            var curMaxCandleList = maxCandleList;
            for(var i = 0; i < maxCandleList.size() && curMaxCandleList.size() > 0; i++) {
                var maxCandle = curMaxCandleList.stream().reduce((first, second) ->
                        first.getHighestPrice().compareTo(second.getHighestPrice()) > 0 ? first : second
                ).orElse(null);
                maxMaxCandleList.add(maxCandle);
                var maxIndex = curMaxCandleList.indexOf(maxCandle);
                annotation += " i=" + i;
                annotation += " maxIndex=" + maxIndex;
                annotation += " size=" + curMaxCandleList.size();
                if (maxIndex == curMaxCandleList.size() - 1) {
                    break;
                }
                curMaxCandleList = curMaxCandleList.subList(maxIndex + 1, curMaxCandleList.size());
            }
            if (maxMaxCandleList.size() >= countFromEnd) {
                fMaxCandle = maxMaxCandleList.get(countFromEnd - 1);
            }
        }
        if (null == fMaxCandle && maxCandleList.size() > 0) {
            fMaxCandle = maxCandleList.get(maxCandleList.size() - 1);
        }
        if (null != fMaxCandle
                && (countMaxCandle == 0 || maxCandleList.size() >= countMaxCandle)
        ) {
            return AlligatorMouthFMax.builder()
                    .fMaxCandle(fMaxCandle)
                    .beginCandle(beginCandle)
                    .maxMaxCandleList(maxMaxCandleList)
                    .maxCandleList(maxCandleList)
                    .annotation(annotation)
                    .isUpPrev(false)
                    .build();
        }
        if (strategy.isRevMax() || countMaxCandle > 0) {
            var isDown = true;
            for (var i = iFindMax - 1; i >= 2; i--) {
                var curCandleList = candleList.subList(i - 2, i + 3);
                var middleCandle = curCandleList.get(2);
                var blue = getAlligatorBlue(figi, middleCandle.getDateTime(), strategy);
                var red = getAlligatorRed(figi, middleCandle.getDateTime(), strategy);
                var green = getAlligatorGreen(figi, middleCandle.getDateTime(), strategy);
                if (blue == null) {
                    break;
                }
                if (
                        (blue < red && red < green)
                        || (
                                middleCandle.getHighestPrice().doubleValue() > red
                                && curCandleList.get(3).getHighestPrice().doubleValue() > red
                                && curCandleList.get(4).getHighestPrice().doubleValue() > red
                        )
                ) {
                    isDown = false;
                    annotation += " UP " + i + " brg = " + (blue < red && red < green) + " or " + printDateTime(middleCandle.getDateTime()) + ":" + printDateTime(curCandleList.get(1).getDateTime()) +  "> red = " + (
                            middleCandle.getHighestPrice().doubleValue() > red
                                    && curCandleList.get(3).getHighestPrice().doubleValue() > red
                                    && curCandleList.get(4).getHighestPrice().doubleValue() > red
                    );
                } else {
                    annotation += " second DOWN on " + i + " brg = " + (blue < red && red < green) + " or " + printDateTime(middleCandle.getDateTime()) + ":" + printDateTime(curCandleList.get(1).getDateTime()) +  "> red = " + (
                            middleCandle.getHighestPrice().doubleValue() > red
                                    && curCandleList.get(3).getHighestPrice().doubleValue() > red
                                    && curCandleList.get(4).getHighestPrice().doubleValue() > red
                    );
                    if (isDown) {
                        continue;
                    } else if (
                            null != fMaxCandle
                            //&& countMaxCandle == 0
                    ) {
                        break;
                    }
                }
                var curMaxCandle = curCandleList.stream().reduce((first, second) ->
                        first.getHighestPrice().compareTo(second.getHighestPrice()) > 0 ? first : second
                ).orElse(null);
                var isMax = middleCandle == curMaxCandle;
                if (
                        isMax
                        && middleCandle.getLowestPrice().doubleValue() > Math.max(blue, green)
                ) {
                    maxCandleList.add(middleCandle);
                    if (null == fMaxCandle || fMaxCandle.getHighestPrice().compareTo(middleCandle.getHighestPrice()) < 0) {
                        fMaxCandle = middleCandle;
                    }
                }
                if (countMaxCandle > 0 && maxCandleList.size() >= countMaxCandle) {
                    break;
                }
            }
            return AlligatorMouthFMax.builder()
                    .fMaxCandle(fMaxCandle)
                    .beginCandle(beginCandle)
                    .maxMaxCandleList(maxMaxCandleList)
                    .maxCandleList(maxCandleList)
                    .annotation(annotation)
                    .isUpPrev(true)
                    .build();
            //return null;
        }
        return null;
    }

    private Boolean isTrendUp(CandleDomainEntity candle, AAlligatorStrategy strategy) {
        Double smaUp = null;
        Double smaDown = null;
        var isTrendUp = true;
        String trendName = "null";
        Double sma = null;

        var smaList = getSma(candle.getFigi(), candle.getDateTime(), strategy.getSmaLength(), strategy.getInterval(), CandleDomainEntity::getMedianPrice, 1);
        sma = smaList != null && smaList.size() > 1 ? smaList.get(1) : null;
        var smaPrev = smaList != null && smaList.size() > 0 ? smaList.get(0) : null;
        if (sma != null && smaPrev != null) {
            isTrendUp = smaPrev <= sma;
        }
        return isTrendUp;
    }

    private AlligatorMouthFMax getLastFMinCandle(
            String figi,
            OffsetDateTime currentDateTime,
            AAlligatorStrategy strategy,
            Integer countFromEnd
    ) {
        return getLastFMinCandle(figi, currentDateTime, strategy, countFromEnd, 0);
    }

    private AlligatorMouthFMax getLastFMinCandle(
            String figi,
            OffsetDateTime currentDateTime,
            AAlligatorStrategy strategy,
            Integer countFromEnd,
            Integer countMaxCandle
    ) {
        var candleList = getCandlesByFigiByLength(figi, currentDateTime, strategy.getMaxDeep(), strategy.getInterval());
        if (candleList == null) {
            return null;
        }
        List<CandleDomainEntity> minCandleList = new ArrayList<>();
        List<CandleDomainEntity> minCandleListAll = new ArrayList<>();
        List<CandleDomainEntity> minMinCandleList = new ArrayList<>();
        List<CandleDomainEntity> minMinCandleListAll = new ArrayList<>();
        var annotation = "";
        var iFindMax = 0;
        var isTrendUp = false;
        var lowestPriceNotOkCount = 0;
        var isMinLowestPriceOverAnyMinLengthCalc = false;
        var isMinLowestPriceOverAnyMinLength = false;
        Integer iLastDownMin = 0;
        var isfMaxCandleOver = false;
        for (var i = candleList.size() - 1 - 2; i >= 2; i--) {
            var curCandleList = candleList.subList(i - 2, i + 3);
            var middleCandle = curCandleList.get(2);
            var blue = getAlligatorBlue(figi, middleCandle.getDateTime(), strategy);
            var red = getAlligatorRed(figi, middleCandle.getDateTime(), strategy);
            var green = getAlligatorGreen(figi, middleCandle.getDateTime(), strategy);
            if (iFindMax > 0 && !strategy.isMaxDeltaByMinMaxAllMin()) {
                if (strategy.isMaxSameTrend()) {
                    var isTrendUpCur = isTrendUp(middleCandle, strategy);
                    if (isTrendUpCur != isTrendUp) {
                        break;
                    }
                } else {
                    break;
                }
            } else if (iFindMax == 0) {
                Boolean isLowestPriceOk = false;
                Boolean isBlueRedGreen = (blue < red && red < green);
                if (
                        null != strategy.getMinLowestPriceUnderAnyLength()
                        || null != strategy.getMinLowestPriceOverAnyMinLength()
                ) {
                    isLowestPriceOk = middleCandle.getLowestPrice().doubleValue() < red
                        || middleCandle.getLowestPrice().doubleValue() < blue
                        || middleCandle.getLowestPrice().doubleValue() < green
                    ;
                    if (!isLowestPriceOk) {
                        annotation += " " + i + " isLowestPriceOk = false";
                        if (
                                null != strategy.getMinLowestPriceUnderAnyLength()
                                && lowestPriceNotOkCount < strategy.getMinLowestPriceUnderAnyLength()
                        ) {
                            //var isTrendUpCur = isTrendUp(middleCandle, strategy);
                            //if (isTrendUpCur) {
                            annotation += " ANY LENGTH isLowestPriceOk = true " + lowestPriceNotOkCount + "<" + strategy.getMinLowestPriceUnderAnyLength();
                            isLowestPriceOk = true;
                            //}
                        }
                        if (
                                null != strategy.getMinLowestPriceOverAnyMinLength()
                        ) {
                            var isLowestPriceOkCur = isMinLowestPriceOverAnyMinLength;
                            if (!isMinLowestPriceOverAnyMinLengthCalc) {
                                isLowestPriceOkCur = true;
                                // нужно проверить интервал глубиной getMinLowestPriceOverAnyMinLength
                                var size = strategy.getMinLowestPriceOverAnyMinLength();
                                if (strategy.getMinLowestPriceOverAnyMaxLength() != null) {
                                    size = strategy.getMinLowestPriceOverAnyMaxLength() + 1;
                                }
                                for (var j = 1; j < size; j++) {
                                    if ((i - j) < 0 || (i - j) >= candleList.size()) {
                                        break;
                                    }
                                    var candleJ = candleList.get(i - j);
                                    var blueJ = getAlligatorBlue(figi, candleJ.getDateTime(), strategy);
                                    var redJ = getAlligatorRed(figi, candleJ.getDateTime(), strategy);
                                    var greenJ = getAlligatorGreen(figi, candleJ.getDateTime(), strategy);
                                    var isCandleJLowestPriceOk = candleJ.getLowestPrice().doubleValue() < redJ
                                            || candleJ.getLowestPrice().doubleValue() < blueJ
                                            || candleJ.getLowestPrice().doubleValue() < greenJ
                                    ;
                                    annotation += " " + j + "candleJ=" + printDateTime(candleJ.getDateTime());
                                    if (isCandleJLowestPriceOk && j < strategy.getMinLowestPriceOverAnyMinLength()) {
                                        annotation += " BREAK MIN j" + j + "<" + strategy.getMinLowestPriceOverAnyMinLength();
                                        // не достаточно длинный интервал над
                                        isLowestPriceOkCur = false;
                                        break;
                                    }
                                    if (
                                            !isCandleJLowestPriceOk
                                            && strategy.getMinLowestPriceOverAnyMaxLength() != null
                                            && j >= strategy.getMinLowestPriceOverAnyMaxLength()
                                    ) {
                                        annotation += " BREAK MAX j=" + j + ">=" + strategy.getMinLowestPriceOverAnyMaxLength();
                                        // слишком длинный интервал над
                                        isLowestPriceOkCur = false;
                                        break;
                                    }
                                }
                                annotation += " OverAnyMin=" + isLowestPriceOkCur;
                                isMinLowestPriceOverAnyMinLength = isLowestPriceOkCur;
                                isMinLowestPriceOverAnyMinLengthCalc = true;
                            }
                            if (!isLowestPriceOk) {
                                annotation += " isLowestPriceOkFromOkCur=" + isLowestPriceOkCur;
                                isLowestPriceOk = isLowestPriceOkCur;
                                isBlueRedGreen = false; // на это уже не смотрим
                            }
                        }
                        lowestPriceNotOkCount++;
                    } else {
                        isMinLowestPriceOverAnyMinLengthCalc = false;
                    }
                } else {
                    isLowestPriceOk = middleCandle.getLowestPrice().doubleValue() < red;
                }
                if (
                        (blue == null
                        || !(isBlueRedGreen || isLowestPriceOk))
                    //&& countMaxCandle == 0
                ) {
                    iFindMax = i;
                    if (strategy.isMaxSameTrend() || strategy.isMaxDeltaByMinMaxAllMin()) {
                        if (strategy.isMaxSameTrend()) {
                            isTrendUp = isTrendUp(middleCandle, strategy);
                            if (!isTrendUp) {
                                break;
                            }
                        }
                    } else {
                        break;
                    }
                }
            }
            var curMinCandle = curCandleList.stream().reduce((first, second) ->
                    first.getLowestPrice().compareTo(second.getLowestPrice()) < 0 ? first : second
            ).orElse(null);
            var isMin = middleCandle == curMinCandle;
            if (
                    isMin
                    && iFindMax == 0
                    && (
                            middleCandle.getHighestPrice().doubleValue() < Math.min(blue, green)
                            //|| countMaxCandle > 0
                            || (
                                    strategy.isMinLowestPriceUnderMinSameTrend()
                                    && middleCandle.getLowestPrice().doubleValue() < Math.min(blue, green)
                            )
                    )
            ) {
                minCandleList.add(middleCandle);
                //annotation += " add=" + printDateTime(middleCandle.getDateTime());
            }
            if (isMin) {
                minCandleListAll.add(middleCandle);
                if (iFindMax == 0) {
                    iLastDownMin = minCandleListAll.size();
                }
                //annotation += " addAll=" + printDateTime(middleCandle.getDateTime());
            }
            if (countMaxCandle > 0 && minCandleList.size() >= countMaxCandle) {
                break;
            }
        }

        CandleDomainEntity beginCandle = candleList.get(iFindMax);
        CandleDomainEntity fMaxCandle = null;
        annotation += " beginCandle=" + printDateTime(beginCandle.getDateTime());
        annotation += " minCandleList.size()=" + minCandleList.size();
        if (
                (null != countFromEnd && minCandleList.size() >= countFromEnd)
                //|| (strategy.getLastFMinStepMaxLength() > 0 && minCandleList.size() > 0)
        ) {
            var curMinCandleList = minCandleList;
            for(var i = 0; i < minCandleList.size() && curMinCandleList.size() > 0; i++) {
                var minCandle = curMinCandleList.stream().reduce((first, second) ->
                        first.getLowestPrice().compareTo(second.getLowestPrice()) < 0 ? first : second
                ).orElse(null);
                minMinCandleList.add(minCandle);
                var minIndex = curMinCandleList.indexOf(minCandle);
                annotation += " i=" + i;
                annotation += " minIndex=" + minIndex;
                annotation += " size=" + curMinCandleList.size();
                if (minIndex == curMinCandleList.size() - 1) {
                    annotation += " break";
                    break;
                }
                curMinCandleList = curMinCandleList.subList(minIndex + 1, curMinCandleList.size());
            }
            if (strategy.getLastFMinStepMaxLength() == 0) {
                if (minMinCandleList.size() >= countFromEnd) {
                    fMaxCandle = minMinCandleList.get(countFromEnd - 1);
                }
            }
        }
        if (strategy.getLastFMinStepMaxLength() > 0 && minMinCandleList.size() > 0) {
            annotation += " getLastFMinStepMaxLength=" + strategy.getLastFMinStepMaxLength();
            fMaxCandle = minMinCandleList.get(0);
            /*Integer minCandleLengthFromCurCandlePrev = null;
            for (var i = 0; i < minMinCandleList.size(); i++) {
                var minCandle = minMinCandleList.get(i);
                var minCandleLengthFromCurCandle = candleList.size() - 1 - candleList.indexOf(minCandle);
                annotation += " i=" + i;
                annotation += " minCandle=" + printDateTime(minCandle.getDateTime());
                annotation += " minCandleLengthFromCurCandle=" + minCandleLengthFromCurCandle;
                if (
                        minCandleLengthFromCurCandle < strategy.getLastFMinStepMaxLength()
                        || (
                            minCandleLengthFromCurCandlePrev != null
                            && minCandleLengthFromCurCandlePrev > minCandleLengthFromCurCandle
                        )
                ) {
                    fMaxCandle = minCandle;
                    minCandleLengthFromCurCandlePrev = minCandleLengthFromCurCandle;
                } else {
                    annotation += " break";
                    break;
                }
            }*/
        } else {
            if (null == fMaxCandle && minCandleList.size() > 0) {
                fMaxCandle = minCandleList.get(minCandleList.size() - 1);
            }
        }
        if (strategy.isMaxDeltaByMinMax()) {
            annotation += " minCandleListAll.size()=" + minCandleListAll.size();
            annotation += " iLastDownMin=" + iLastDownMin;
            //var curMinCandleList = minCandleListAll.subList(0, iLastDownMin);
            var curMinCandleList = minCandleListAll;
            if (null == fMaxCandle && strategy.isMaxDeltaByMinMaxAllMinUp()) {
                isfMaxCandleOver = true;
                fMaxCandle = minCandleListAll.get(0);
            }
            if (null != fMaxCandle) {
                CandleDomainEntity finalFMaxCandle = fMaxCandle;
                curMinCandleList = curMinCandleList.stream().filter(c -> c.getLowestPrice().compareTo(finalFMaxCandle.getLowestPrice()) >= 0).collect(Collectors.toList());
            }
            for(var i = 0; curMinCandleList.size() > 0; i++) {
                var minCandle = curMinCandleList.stream().reduce((first, second) ->
                        first.getLowestPrice().compareTo(second.getLowestPrice()) < 0 ? first : second
                ).orElse(null);
                minMinCandleListAll.add(minCandle);
                var minIndex = curMinCandleList.indexOf(minCandle);
                annotation += " i=" + i;
                annotation += " minIndex=" + minIndex;
                annotation += " c=" + printDateTime(minCandle.getDateTime());
                annotation += " size=" + curMinCandleList.size();
                if (minIndex == curMinCandleList.size() - 1) {
                    annotation += " break";
                    break;
                }
                curMinCandleList = curMinCandleList.subList(minIndex + 1, curMinCandleList.size());
            }
            annotation += " minMinCandleListAll.size()=" + minMinCandleListAll.size();
            if (minMinCandleListAll.size() > 1 && strategy.isMaxDeltaByMinMaxAllMinUp()) {
                if (null == fMaxCandle) {
                    isfMaxCandleOver = true;
                    fMaxCandle = minMinCandleListAll.get(0);
                }
            } else if (strategy.isMaxDeltaByMinMaxAllMinUp()) {
                minMinCandleListAll.clear();
                annotation += " CLEAR";
                // попробуем восходящие минимумы
                curMinCandleList = minCandleListAll;
                if (null == fMaxCandle && minCandleListAll.size() > 0) {
                    isfMaxCandleOver = true;
                    fMaxCandle = minCandleListAll.get(0);
                }
                if (null != fMaxCandle) {
                    CandleDomainEntity finalFMaxCandle = fMaxCandle;
                    curMinCandleList = curMinCandleList.stream().filter(c -> c.getLowestPrice().compareTo(finalFMaxCandle.getLowestPrice()) <= 0).collect(Collectors.toList());
                }
                for(var i = 0; curMinCandleList.size() > 0; i++) {
                    var maxCandle = curMinCandleList.stream().reduce((first, second) ->
                            first.getLowestPrice().compareTo(second.getLowestPrice()) > 0 ? first : second
                    ).orElse(null);
                    minMinCandleListAll.add(maxCandle);
                    var maxIndex = curMinCandleList.indexOf(maxCandle);
                    annotation += " i=" + i;
                    annotation += " maxIndex=" + maxIndex;
                    annotation += " c=" + printDateTime(maxCandle.getDateTime());
                    annotation += " size=" + curMinCandleList.size();
                    if (maxIndex == curMinCandleList.size() - 1) {
                        annotation += " break";
                        break;
                    }
                    curMinCandleList = curMinCandleList.subList(maxIndex + 1, curMinCandleList.size());
                }
            }
        }
        if (null != fMaxCandle
            && (countMaxCandle == 0 || minCandleList.size() >= countMaxCandle)
            || !(strategy.isRevMax() || countMaxCandle > 0)
        ) {
            return AlligatorMouthFMax.builder()
                    .fMaxCandle(fMaxCandle)
                    .isfMaxCandleOver(isfMaxCandleOver)
                    .beginCandle(beginCandle)
                    .maxMaxCandleList(minMinCandleList)
                    .maxMaxCandleListAll(minMinCandleListAll)
                    .maxCandleList(minCandleList)
                    .annotation(annotation)
                    .isUpPrev(false)
                    .build();
        }
        if (strategy.isRevMax() || countMaxCandle > 0) {
            annotation += " countMaxCandle=" + countMaxCandle;
            var isUp = true;
            var upCount = 1;
            for (var i = iFindMax - 1; i >= 2; i--) {
                var curCandleList = candleList.subList(i - 2, i + 3);
                var middleCandle = curCandleList.get(2);
                var blue = getAlligatorBlue(figi, middleCandle.getDateTime(), strategy);
                var red = getAlligatorRed(figi, middleCandle.getDateTime(), strategy);
                var green = getAlligatorGreen(figi, middleCandle.getDateTime(), strategy);
                if (blue == null) {
                    break;
                }
                if (
                        (blue < red && red < green)
                                || middleCandle.getLowestPrice().doubleValue() < red
                ) {
                    isUp = false;
                } else {
                    if (isUp) {
                        continue;
                    } else {
                        isUp = true;
                        upCount++;
                        if (
                            null != fMaxCandle
                            && (countMaxCandle == 0 || upCount > 3)
                        ) {
                            break;
                        }

                    }
                }
                var curMinCandle = curCandleList.stream().reduce((first, second) ->
                        first.getLowestPrice().compareTo(second.getLowestPrice()) < 0 ? first : second
                ).orElse(null);
                var isMin = middleCandle == curMinCandle;
                if (
                        isMin
                                && middleCandle.getHighestPrice().doubleValue() < Math.min(blue, green)
                ) {
                    minCandleList.add(middleCandle);
                    if (null == fMaxCandle || fMaxCandle.getLowestPrice().compareTo(middleCandle.getLowestPrice()) > 0) {
                        fMaxCandle = middleCandle;
                    }
                }
                if (countMaxCandle > 0 && minCandleList.size() >= countMaxCandle) {
                    break;
                }
            }
            return AlligatorMouthFMax.builder()
                    .fMaxCandle(fMaxCandle)
                    .beginCandle(beginCandle)
                    .maxMaxCandleList(minMinCandleList)
                    .maxCandleList(minCandleList)
                    .annotation(annotation)
                    .isUpPrev(true)
                    .build();
            //return null;
        }
        return null;
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
        return s.indexOf(".") < 0 ? s : s.replaceAll("0*$", "").replaceAll("\\.$", "").replaceFirst("(\\.[0-9]{4})[0-9]+$", "$1");
    }

    private String printDateTime(OffsetDateTime dt)
    {
        return notificationService.formatDateTime(dt).replace("+03:00", "");
    }

    private String printDateTime(ZonedDateTime dt)
    {
        return dt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME).replace("+03:00", "");
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
