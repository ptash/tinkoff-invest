package com.struchev.invest.service.processor;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.service.candle.CandleHistoryService;
import com.struchev.invest.service.notification.NotificationService;
import com.struchev.invest.strategy.AStrategy;
import com.struchev.invest.strategy.instrument_by_fiat_cross.AInstrumentByFiatCrossStrategy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

    private Integer cashSize = 1000;
    private Map<String, Double> smaCashMap = new HashMap<>();

    @Getter
    private final Map<String, BigDecimal> currentPrices = new ConcurrentHashMap<>();

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
                                         AInstrumentByFiatCrossStrategy strategy, Boolean isDygraphs) {
        OffsetDateTime currentDateTime = candle.getDateTime();
        String figi = candle.getFigi();

        var smaTube = getSma(figi, currentDateTime, strategy.getSmaTubeLength(), strategy.getInterval(), keyExtractor, strategy.getTicksMoveUp());

        var smaSlowest = getSma(figi, currentDateTime, strategy.getSmaSlowestLength(), strategy.getInterval(), keyExtractor, strategy.getTicksMoveUp());

        var smaSlow = getSma(figi, currentDateTime, strategy.getSmaSlowLength(), strategy.getInterval(), keyExtractor);

        var smaFast = getSma(figi, currentDateTime, strategy.getSmaFastLength(), strategy.getInterval(), keyExtractor);

        var emaFast = getEma(figi, currentDateTime, strategy.getEmaFastLength(), strategy.getInterval(), keyExtractor);

        var ema2 = getEma(figi, currentDateTime, 2, strategy.getInterval(), keyExtractor);

        List<Double> avgDelta;
        if (strategy.isTubeAvgDeltaAdvance()) {
            //avgDelta = calculateTubeAvgDelta(figi, currentDateTime, strategy, keyExtractor);
            avgDelta = calculateAvgDeltaAdvance(figi, currentDateTime, strategy, keyExtractor, emaFast, smaSlow);
        } else if (strategy.isTubeAvgDeltaSimple()) {
            avgDelta = calculateAvgDeltaSimple(figi, currentDateTime, strategy, keyExtractor, emaFast, smaSlow);
        } else {
            avgDelta = calculateAvgDelta(figi, currentDateTime, strategy, keyExtractor);
        }

        if (null == avgDelta || null == smaTube || null == smaSlowest || null == smaSlow || null == ema2 || null == emaFast) {
            log.info("There is not enough data for the interval: currentDateTime = {}, {}, {}, {}, {}, {}", currentDateTime, smaTube, smaSlowest, smaSlow, smaFast, emaFast);
            return false;
        }

        var deadLine = calcDeadLineTop(strategy, smaTube, smaSlowest, smaFast);
        var deadLineBottom = BigDecimal.valueOf(deadLine.get(0));
        var deadLineTopDecimal = deadLine.get(1);
        var deadLineTop = BigDecimal.valueOf(deadLine.get(1));
        var tubeTopToBy = BigDecimal.valueOf(deadLine.get(2));
        var tubeTopToInvest = BigDecimal.valueOf(deadLine.get(3));
        var ema2Cur = BigDecimal.valueOf(ema2.get(ema2.size() - 1));

        //var price = ema2Cur.min(candle.getClosingPrice().min(candle.getOpenPrice()));
        var price = candle.getClosingPrice();
        var deltaMin = deadLineTop.subtract(deadLineBottom).divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP).max(BigDecimal.valueOf(0.01));

        var annotation = "";
        Boolean isBottomLevels = false;
        if (strategy.isSellWithMaxProfit() && !strategy.isTubeAvgDeltaAdvance()
                && emaFast.get(emaFast.size() - 1) <= smaFast.get(smaFast.size() - 1)
                && smaFast.get(smaFast.size() - 1) <= smaSlow.get(smaSlow.size() - 1)
                && smaSlow.get(smaSlow.size() - 1) <= smaSlowest.get(smaSlowest.size() - 1)
                && price.compareTo(BigDecimal.valueOf(emaFast.get(emaFast.size() - 1))) < 0
        ) {
            annotation += " bottom levels";
            var delta = Math.min(
                    smaFast.get(smaFast.size() - 1) - emaFast.get(emaFast.size() - 1),
                    smaSlow.get(smaSlow.size() - 1) - smaFast.get(smaFast.size() - 1)
            );
            var deltaMinBottom = deltaMin;//deadLineTop.subtract(deadLineBottom).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP).max(BigDecimal.valueOf(0.01));
            delta = Math.min(delta, smaSlowest.get(smaSlowest.size() - 1) - smaSlow.get(smaSlow.size() - 1));
            if (deltaMinBottom.compareTo(BigDecimal.valueOf(delta)) < 0) {
                annotation += " deltaMin < delta";
                deadLineBottom = deadLineBottom.subtract(deadLineTop.subtract(deadLineBottom).multiply(BigDecimal.valueOf(1.2f)));
                isBottomLevels = true;
            } else {
                annotation += " deltaMin < delta: " + deltaMinBottom + " < " + delta;
            }
        }

        Boolean isInTube = false;
        if (strategy.isSellWithMaxProfit() && !strategy.isTubeAvgDeltaAdvance()) {
            if (price.compareTo(BigDecimal.valueOf(avgDelta.get(0))) < 0) {
                isInTube = true;
                tubeTopToBy = BigDecimal.valueOf(avgDelta.get(0));
                annotation += " new t = " + avgDelta.get(0);
            } else if (false) {
                var delta = Math.min(
                        Math.abs(emaFast.get(emaFast.size() - 1) - smaSlow.get(smaSlow.size() - 1)),
                        Math.abs(smaSlow.get(smaSlow.size() - 1) - smaFast.get(smaFast.size() - 1))
                );
                var deltaFast = Math.abs(emaFast.get(emaFast.size() - 1) - smaFast.get(smaFast.size() - 1));
                var tubeSizeFast = deltaMin;
                var tubeSize = deltaMin.multiply(BigDecimal.valueOf(2));
                annotation += " t: (" + deltaFast + " < " + tubeSizeFast + "; " + delta + " < " + tubeSize + ")";
                if (tubeSize.compareTo(BigDecimal.valueOf(delta)) > 0
                        && tubeSizeFast.compareTo(BigDecimal.valueOf(deltaFast)) > 0
                    //&& smaSlow.get(smaSlow.size() - 1) < smaFast.get(smaFast.size() - 1)
                    //&& getPercentMoveUp(smaSlow) > -strategy.getPercentMoveUpError()
                ) {
                    isInTube = true;
                    if (tubeTopToBy.compareTo(BigDecimal.ZERO) < 0) {
                        //tubeTopToBy = deadLineBottom.add(deltaMin.divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP));
                        //tubeTopToBy = deadLineBottom.add(deltaMin);
                        if (smaSlow.get(smaSlow.size() - 1) < smaFast.get(smaFast.size() - 1)) {
                            tubeTopToBy = BigDecimal.valueOf(smaSlow.get(smaSlow.size() - 1));
                        } else {
                            tubeTopToBy = deadLineBottom.add(deltaMin);
                        }
                        annotation += " new tft = " + tubeTopToBy;
                    } else {
                        annotation += " = t";
                    }
                }
            }
        } else {
            isInTube = true;
        }

        Boolean result = price.compareTo(deadLineTop) < 0 && price.compareTo(deadLineBottom) > 0;
        annotation += " " + result;
        Double investTubeBottom = null;
        Double smaSlowDelta = null;
        if (strategy.isTubeAvgDeltaAdvance()) {
            isInTube = false;
            var investTop = BigDecimal.valueOf(avgDelta.get(1) + 3 * avgDelta.get(3));
            var tubeSize = avgDelta.get(1) - avgDelta.get(0);
            var expectProfit = (price.doubleValue() * strategy.getSellCriteria().getTakeProfitPercent()) / 100.0;
            //var expectTubeTop = avgDelta.get(1) + strategy.getEmaFastLength() * ((emaFast.get(emaFast.size() - 1) - emaFast.get(0)) / emaFast.size() - (smaFast.get(smaFast.size() - 1) - smaFast.get(0)) / smaFast.size());
            var expectTubeTop = avgDelta.get(1) + strategy.getEmaFastLength() * ((smaSlowest.get(smaSlowest.size() - 1) - smaSlowest.get(0)) / smaSlowest.size());
            var expectTubeSize = expectTubeTop - avgDelta.get(0);
            annotation += " profit: " + tubeSize + ", " + expectTubeSize + ">" + expectProfit;
            if (tubeSize > expectProfit && expectTubeSize > expectProfit) {
                annotation += "=t";
                if (avgDelta.get(3) > 0 && price.compareTo(BigDecimal.valueOf(avgDelta.get(0))) < 0) {
                    //annotation += " moveUp: " + getPercentMoveUp(smaTube) + " + " + getPercentMoveUp(smaSlowest) + " + " + getPercentMoveUp(smaSlow) + " >= 0";
                    //var p = getPercentMoveUp(smaTube) + getPercentMoveUp(smaSlowest);
                    //annotation += " moveUp: " + p;
                    //if (p <= strategy.getMinPercentTubeMoveUp() || p >= - strategy.getPriceError().doubleValue()) {
                    isInTube = true;
                    tubeTopToBy = BigDecimal.valueOf(avgDelta.get(0));
                    annotation += " new t = " + tubeTopToBy;
                    //}
            /*} else if (avgDelta.get(3) > 0 && price.compareTo(investTop) > 0) {
                isInTube = true;
                tubeTopToBy = investTop;
                annotation += " new tt = " + tubeTopToBy;*/
                }
            }
            result = false;
        }
        if ((strategy.isTubeAvgDeltaAdvance() && isInTube) || (!strategy.isTubeAvgDeltaAdvance() && (result || isInTube))) {
            var isTubeTopToBy = false;
            annotation += " " + price + " < ttb=" + tubeTopToBy;
            if (tubeTopToBy.compareTo(BigDecimal.ZERO) > 0 && (price.compareTo(tubeTopToBy) < 0 || strategy.isTubeAvgDeltaAdvance())) {
                if (getPercentMoveUp(smaTube) >= strategy.getMinPercentTubeMoveUp()) {
                    isTubeTopToBy = true;
                    result = true;
                    annotation += " tTB true " + getPercentMoveUp(smaTube) + " >= " + strategy.getMinPercentTubeMoveUp() + " (" + smaTube + ")";
                } else {
                    annotation += " tTB false " + getPercentMoveUp(smaTube) + " >= " + strategy.getMinPercentTubeMoveUp() + " (" + smaTube + ")";
                }
            }
            if (isTubeTopToBy) {
            } else if (strategy.isSellWithMaxProfit()) {
                result = false;
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
        } else {
            result = false;
        }
        Boolean isTubeAvgDeltaResult = false;
        if (strategy.isTubeAvgDeltaAdvance()) {
            isTubeAvgDeltaResult = result;
            result = true;
        }
        if (!result) {
            if (strategy.isBuyInvestCrossSmaEma2() && smaTube.get(smaTube.size() - 1) < smaSlowest.get(smaSlowest.size() - 1)
                    && isCrossover(smaTube, ema2)) {
                annotation += " smaTube/ema2";
                result = true;
            } else if (!strategy.isSellWithMaxProfit() && price.compareTo(deadLineBottom) < 0) {
                annotation += " deadLineBottom " + getPercentMoveUp(smaTube) + " (" + smaTube + ") : >= " + strategy.getMinPercentTubeBottomMoveUp();
                if (getPercentMoveUp(smaTube) >= strategy.getMinPercentTubeBottomMoveUp()) {
                    annotation += " = true ";
                    result = true;
                }
            }
            Boolean isInvestLevels = false;
            if (!result && strategy.isSellWithMaxProfit()
                    && smaFast.get(smaFast.size() - 1) > smaSlow.get(smaSlow.size() - 1)
                    && smaSlow.get(smaSlow.size() - 1) > deadLineTopDecimal
                    && deadLineTopDecimal > smaSlowest.get(smaSlowest.size() - 1)
                    && smaSlow.get(smaSlow.size() - 1) > smaSlowest.get(smaSlowest.size() - 1)
                    && smaSlowest.get(smaSlowest.size() - 1) > smaTube.get(smaTube.size() - 1)
                    && getPercentMoveUp(smaSlowest) > 0
            ) {
                isInvestLevels = true;
                annotation += " investL";
                var delta = Math.min(
                        smaSlow.get(smaSlow.size() - 1) - deadLineTopDecimal,
                        deadLineTopDecimal - smaSlowest.get(smaSlowest.size() - 1)
                );
                delta = Math.min(delta, smaSlowest.get(smaSlowest.size() - 1) - smaTube.get(smaTube.size() - 1));
                //var delta = smaSlow.get(smaSlow.size() - 1) - smaSlowest.get(smaSlowest.size() - 1);
                if (deltaMin.compareTo(BigDecimal.valueOf(delta)) < 0) {
                    annotation += " dMin < d";
                    var smaFastBlur = smaFast.get(smaFast.size() - 1);
//                    if (ema2.get(ema2.size() - 1) > smaFastBlur) {
//
//                    }
                    if (price.compareTo(BigDecimal.valueOf(smaFastBlur)) < 0) {
                        annotation += " p < sFast";
                        if (isCrossover(smaFast, ema2)) {
                            annotation += " smaFast/ema2 " + getCrossPercent(smaFast, ema2) + " < " + strategy.getMaxSmaFastCrossPercent();
                            if (getCrossPercent(smaFast, ema2) < strategy.getMaxSmaFastCrossPercent()) {
                                annotation += " = true";
                                result = true;
                            }
                        }
                        annotation += " eFast/sFast = " + isCrossover(emaFast, smaFast) + ": " + getPercentMoveUp(ema2) + " > " + getPercentMoveUp(emaFast) + " >= " + getPercentMoveUp(smaFast) + " > -" + strategy.getPercentMoveUpError();
                        if (!result && (isCrossover(emaFast, smaFast) && (
                                getPercentMoveUp(ema2) > strategy.getPercentMoveUpError()
                                && getPercentMoveUp(smaFast) > -strategy.getPercentMoveUpError()
                                && getPercentMoveUp(emaFast) >= getPercentMoveUp(smaFast)))) {
                            annotation += " eFast/sFast";
                            result = true;
                        } else if (price.compareTo(BigDecimal.valueOf(smaSlow.get(smaSlow.size() - 1))) < 0) {
                            annotation += " p < sSlow";
                            var deadLineInvest = deadLineTopDecimal + (smaSlow.get(smaSlow.size() - 1) - deadLineTopDecimal) * 0.75;
                            annotation += " p < sSlow " + getPercentMoveUp(ema2) + ": (" + deadLineTop + " > " + price + " > " + deadLineInvest;
                            if (getPercentMoveUp(ema2) > 0 && (price.compareTo(BigDecimal.valueOf(deadLineInvest)) > 0 || price.compareTo(deadLineTop) < 0)) {
                                annotation += " = true";
                                result = true;
                            }
                        }
                    } else {
                        annotation += " price < smaFast";
                        //if (isCrossover(ema2, smaFast)) {
                        //    annotation += " ema2/smaFast";
                        //    result = true;
                        //}
                    }
                } else {
                    annotation += " delta = false (" + deltaMin + " < " + delta + ")";
                }
            }
            if (!result && !isInvestLevels && strategy.isSellWithMaxProfit()
                    && emaFast.get(emaFast.size() - 1) > smaFast.get(smaFast.size() - 1)
                    && smaFast.get(smaFast.size() - 1) > smaSlow.get(smaSlow.size() - 1)
                    && smaSlow.get(smaSlow.size() - 1) > smaSlowest.get(smaSlowest.size() - 1)
                    //&& smaSlow.get(smaSlow.size() - 1) > smaTube.get(smaTube.size() - 1)
                    && deadLineTop.compareTo(BigDecimal.valueOf(smaSlow.get(smaSlow.size() - 1))) < 0
                    && price.compareTo(BigDecimal.valueOf(emaFast.get(emaFast.size() - 1))) > 0
            ) {
                annotation += " invest rocket";
                var delta = Math.min(
                        emaFast.get(emaFast.size() - 1) - smaFast.get(smaFast.size() - 1),
                        smaFast.get(smaFast.size() - 1) - smaSlow.get(smaSlow.size() - 1)
                        );
                delta = Math.min(delta, smaSlow.get(smaSlow.size() - 1) - smaSlowest.get(smaSlowest.size() - 1));
                if (deltaMin.compareTo(BigDecimal.valueOf(delta)) < 0) {
                    annotation += " deltaMin < delta";
                    result = true;
                } else {
                    annotation += " deltaMin < delta: " + deltaMin + " < " + delta;
                }
            }
            if (!result && isBottomLevels
            ) {
                if (price.compareTo(deadLineBottom) < 0) {
                    annotation += " deadLineBottom " + getPercentMoveUp(smaSlowest) + " (" + smaSlowest + ") : >= " + strategy.getMinPercentSmaSlowestMoveUp();
                    if (getPercentMoveUp(smaSlowest) >= strategy.getMinPercentSmaSlowestMoveUp()) {
                        annotation += " = true";
                        result = true;
                    }
                } else {
                    annotation += " false price: " + price + " >= " + deadLineBottom;
                }
            }

            if (!result && strategy.getInvestPercentFromFast() > 0 && tubeTopToInvest.compareTo(BigDecimal.ZERO) > 0 && price.compareTo(tubeTopToInvest) > 0) {
                Double smaFastCur = smaFast.get(smaFast.size() - 1);
                Double emaFastCur = emaFast.get(emaFast.size() - 1);
                Double smaSlowCur = smaSlow.get(smaSlow.size() - 1);
                var investMaxPercent = strategy.getInvestPercentFromFast();
                smaSlowDelta = smaSlowCur + smaSlowCur * strategy.getDeadLinePercent() / 100.;
                investTubeBottom = smaFastCur - smaFastCur * investMaxPercent / 100.;
                Double investTubeTop = smaFastCur + smaFastCur * investMaxPercent / 100.;
                //investBottom = investTubeBottom - smaFastCur * investMaxPercent * 2. / 100.;
                var percentMoveUp = getPercentMoveUp(ema2);
                if (smaFastCur < smaSlowDelta && ema2.get(0) < smaFastCur) {
                    if (percentMoveUp > strategy.getMinInvestMoveUp() && emaFastCur < investTubeTop && emaFastCur > investTubeBottom) {
                        annotation += " invest smaFast/smaSlow";
                        result = true;
                    } else {
                        annotation += " percentMoveUp (" + (percentMoveUp > strategy.getMinInvestMoveUp()) + ") = " + percentMoveUp + " > " + strategy.getMinInvestMoveUp();
                    }
                }
            }
        }
        //if (!result && strategy.isSellWithMaxProfit() && ema2Cur.compareTo(deadLineBottom) < 0 && isInTube) {
        //    result = true;
        //    annotation += " under Tube = true";
        //}
        if (result && !strategy.allowBuyUnderSmaTube()
                && price.compareTo(BigDecimal.valueOf(smaTube.get(smaTube.size() - 1))) < 0
                //&& price.compareTo(BigDecimal.valueOf(smaSlow.get(smaSlow.size() - 1))) < 0
        ) {
            result = false;
            annotation += " allowBuyUnderSmaTube = false";
        }

        if (strategy.isTubeAvgDeltaAdvance()) {
            result = isTubeAvgDeltaResult;
        }

        //var smaFastest = getSma(figi, currentDateTime, strategy.getSmaFastLength() / 2, strategy.getInterval(), keyExtractor);
        //var smaFastest2 = getSma(figi, currentDateTime, strategy.getSmaFastLength() / 4, strategy.getInterval(), keyExtractor);

        if (!isDygraphs) {
            return result;
        }

        reportLog(
                strategy,
                figi,
                "{} | {} | {} | {} | {} | {} | {} | | {} | {} | {} | {} | {} | {} |by {}|{}|{}|{}|{}",
                notificationService.formatDateTime(currentDateTime),
                smaSlowest.get(1),
                smaSlow.get(1),
                smaFast.get(1),
                emaFast.get(1),
                ema2.get(1),
                price,
                candle.getClosingPrice(),
                deadLineBottom,
                deadLineTop,
                avgDelta.get(0), //smaSlowDelta == null ? "" : smaSlowDelta,
                avgDelta.get(1), //investTubeBottom == null ? "" : investTubeBottom,
                smaTube.get(smaTube.size() - 1),
                annotation,
                avgDelta.get(2),
                avgDelta.get(2) - avgDelta.get(3),
                avgDelta.get(2) + avgDelta.get(3),
                candle.getOpenPrice()
        );

        return result;
    }

    private Boolean calculateCellCriteria(CandleDomainEntity candle,
                                         Function<? super CandleDomainEntity, ? extends BigDecimal> keyExtractor,
                                         AInstrumentByFiatCrossStrategy strategy, BigDecimal profitPercent, Boolean isDygraphs) {
        OffsetDateTime currentDateTime = candle.getDateTime();
        String figi = candle.getFigi();

        var smaTube = getSma(figi, currentDateTime, strategy.getSmaTubeLength(), strategy.getInterval(), keyExtractor);

        var smaSlowest = getSma(figi, currentDateTime, strategy.getSmaSlowestLength(), strategy.getInterval(), keyExtractor);

        var smaSlow = getSma(figi, currentDateTime, strategy.getSmaSlowLength(), strategy.getInterval(), keyExtractor);

        var ema2 = getEma(figi, currentDateTime, 2, strategy.getInterval(), keyExtractor);

        var emaFast = getEma(figi, currentDateTime, strategy.getEmaFastLength(), strategy.getInterval(), keyExtractor);

        var smaFast = getSma(figi, currentDateTime, strategy.getSmaFastLength(), strategy.getInterval(), keyExtractor);

        List<Double> avgDelta;
        if (strategy.isTubeAvgDeltaAdvance()) {
            //avgDelta = calculateTubeAvgDelta(figi, currentDateTime, strategy, keyExtractor);
            avgDelta = calculateAvgDeltaAdvance(figi, currentDateTime, strategy, keyExtractor, emaFast, smaSlow);
        } else if (strategy.isTubeAvgDeltaSimple()) {
            avgDelta = calculateAvgDeltaSimple(figi, currentDateTime, strategy, keyExtractor, emaFast, smaSlow);
        } else {
            avgDelta = calculateAvgDelta(figi, currentDateTime, strategy, keyExtractor);
        }

        if (null == avgDelta || null == smaTube || null == smaSlowest || null == smaSlow || null == ema2 || null == emaFast) {
            log.info("There is not enough data for the interval: currentDateTime = {}, {}, {}, {}, {}, {}", currentDateTime, smaTube, smaSlowest, smaSlow, smaFast, emaFast);
            return false;
        }

        var deadLine = calcDeadLineTop(strategy, smaTube, smaSlowest, smaFast);
        var deadLineBottom = BigDecimal.valueOf(deadLine.get(0));
        var deadLineTop = BigDecimal.valueOf(deadLine.get(1));
        var tubeTopToInvest = BigDecimal.valueOf(deadLine.get(3));
        var price = BigDecimal.valueOf(ema2.get(ema2.size() - 1)).min(candle.getClosingPrice().min(candle.getOpenPrice()));

        Boolean result = false;
        String annotation = "";
        if (false && strategy.isSellWithMaxProfit() && profitPercent.compareTo(BigDecimal.ZERO) > 0) {
            if (price.compareTo(BigDecimal.valueOf(smaSlowest.get(smaSlowest.size() - 1))) > 0
                    || (strategy.isSellEma2UpOnBottom() && price.compareTo(BigDecimal.valueOf(smaFast.get(smaFast.size() - 1))) > 0)
            ) {
                annotation += " crossover in plus";
                result = isCrossover(ema2, emaFast) || isCrossover(emaFast, ema2);
                if (result) {
                    annotation += " = t";
                }
            } else if (strategy.isSellEma2UpOnBottom()) {
                annotation += " crossover in emaFast/ema2";
                result = isCrossover(emaFast, ema2);
                if (result) {
                    annotation += " = t";
                }
            } else {
                annotation += " crossover in ema2/emaFast";
                result = isCrossover(ema2, emaFast);
                if (result) {
                    annotation += " = t";
                }
            }
        } else if (strategy.isTubeAvgDeltaAdvance() || strategy.isNotCellIfBuy()) {
            if (price.compareTo(BigDecimal.valueOf(avgDelta.get(0))) < 0 || price.compareTo(BigDecimal.valueOf(avgDelta.get(1))) > 0) {
                annotation += " isTubeAvgDelta";
                result = true;
            }
        } else {
            result = isCrossover(smaSlowest, smaSlow)
                    || isCrossover(smaSlow, emaFast)
                    || isCrossover(ema2, emaFast)
                    || isCrossover(emaFast, ema2)
                    || isCrossover(ema2, smaSlowest)
            ;
            if (result) {
                annotation += " crossover in minus";
            }
            if (!result && strategy.isSellWithMaxProfit() && price.compareTo(deadLineBottom) < 0) {
                annotation += " price under deadLine: " + price + " < " + deadLineBottom;
                result = true;
            }
        }
        if (!(strategy.isTubeAvgDeltaAdvance() || strategy.isNotCellIfBuy())) {
            if (!result && strategy.isSellWithMaxProfit() && profitPercent.compareTo(BigDecimal.ZERO) > 0) {
                if (price.compareTo(BigDecimal.valueOf(avgDelta.get(0))) > 0) {
                    annotation += " > avg";
                    result = true;
                }
            } else {
                if (price.compareTo(BigDecimal.valueOf(avgDelta.get(1))) < 0) {
                    annotation += " < avg";
                    result = true;
                }
            }
        }

        //var smaFastest = getSma(figi, currentDateTime, strategy.getSmaFastLength() / 2, strategy.getInterval(), keyExtractor);
        //var smaFastest2 = getSma(figi, currentDateTime, strategy.getSmaFastLength() / 4, strategy.getInterval(), keyExtractor);

        if (!isDygraphs) {
            return result;
        }

        reportLog(
                strategy,
                figi,
                "{} | {} | {} | {} | {} | {} | | {} | {} | {} | {} |{}|{}| {} | cell {}|{}|{}|{}|{}",
                notificationService.formatDateTime(currentDateTime),
                smaSlowest.get(1),
                smaSlow.get(1),
                smaFast.get(1),
                emaFast.get(1),
                ema2.get(1),
                price,
                candle.getClosingPrice(),
                deadLineBottom,
                deadLineTop,
                avgDelta.get(0),
                avgDelta.get(1), //investTubeBottom == null ? "" : investTubeBottom,
                smaTube.get(smaTube.size() - 1),
                annotation,
                avgDelta.get(2),
                avgDelta.get(2) - avgDelta.get(3),
                avgDelta.get(2) + avgDelta.get(3),
                candle.getOpenPrice()
        );

        return result;
    }

    private List<Double> calcDeadLineTop(AInstrumentByFiatCrossStrategy strategy, List<Double> smaTube, List<Double> smaSlowest, List<Double> smaFast)
    {
        var smaTubeCur = smaTube.get(smaTube.size() - 1);

        var smaSlowestCur = smaSlowest.get(smaSlowest.size() - 1);
        var deadLineTop = smaFast.get(smaFast.size() - 1);
        Double b100 = 100.0;
        deadLineTop = deadLineTop + deadLineTop * strategy.getDeadLinePercent() / b100;
        var deadLineBottom = smaSlowestCur;
        //deadLineTop = Math.min(deadLineTop, smaSlowestCur + smaSlowestCur * strategy.getDeadLinePercentFromSmaSlowest() / b100);
        //deadLineBottom = deadLineBottom - deadLineBottom * strategy.getDeadLinePercentFromSmaSlowest() / b100;

        var tubeMaxPercent = strategy.getDeadLinePercentFromSmaSlowest() / 2.0;
        var deltaTubePercent = ((smaTubeCur - smaSlowestCur) * 100.0)/smaTubeCur;
        Double tubeTopToBy = -1.;
        Double tubeTopToInvest = -1.;
        if (strategy.isTubeTopBlur() && tubeMaxPercent > 0 && Math.abs(deltaTubePercent) < tubeMaxPercent * 2) {
            var delta = smaSlowestCur * tubeMaxPercent * 2 / 100.0;
            var factor = (smaTubeCur - smaSlowestCur) / delta;
            //if (deltaTubePercent < 0f) {
            //    factor = -1f * factor;
            //}
            if (factor != 0f) {
                factor = factor / Math.abs(factor) * Math.sqrt(Math.abs(factor));
            }
            var tubeTopToByBlur = smaSlowestCur - delta * factor;
            if (tubeTopToByBlur < deadLineTop) {
                deadLineTop = tubeTopToByBlur;
                tubeTopToBy = tubeTopToByBlur;
            }
            deadLineTop = Math.min(deadLineTop, smaSlowestCur - delta * factor);
        } else {
            //var deadLineTopFromSmaSlowest = smaSlowestCur + smaSlowestCur * strategy.getDeadLinePercentFromSmaSlowest() / b100;
            deadLineTop = Math.min(deadLineTop, smaSlowestCur + smaSlowestCur * strategy.getDeadLinePercentFromSmaSlowest() / b100);
        }
        if (Math.abs(deltaTubePercent) < tubeMaxPercent) {
            // рядом
            if (strategy.isTubeTopNear()) {
                var tubeTopToByNear = smaTubeCur - ((smaTubeCur * tubeMaxPercent) / 100);
                if (deadLineTop > tubeTopToByNear) {
                    deadLineTop = tubeTopToByNear;
                    tubeTopToBy = tubeTopToByNear;
                }
            }
        } else if (deltaTubePercent > 0) {
            // средняя выше
            var deadLineTopInBotton = Math.min(smaSlowestCur - ((smaSlowestCur * tubeMaxPercent * 2)/ 100), smaSlowestCur - (smaTubeCur - smaSlowestCur));
            if (deadLineTopInBotton < deadLineTop) {
                deadLineTop = deadLineTopInBotton;
                tubeTopToBy = deadLineTopInBotton;
            }
        } else {
            // средняя ниже
            deadLineTop = Math.min(deadLineTop, Math.max(
                    smaTubeCur + (smaTubeCur * tubeMaxPercent * 3) / 100,
                    smaSlowestCur + (smaTubeCur * strategy.getDeadLinePercent()) / 100
            ));
            tubeTopToInvest = deadLineTop;
        }
        //if (deadLineBottom >= deadLineTop) {
            deadLineBottom = deadLineTop - deadLineTop * strategy.getDeadLinePercentFromSmaSlowest() / b100;
        //}
        if (strategy.isSellWithMaxProfit() && getPercentMoveUp(smaSlowest) < strategy.getMinPercentSmaSlowestMoveUp()) {
            deadLineTop = deadLineTop - (deadLineTop - deadLineBottom) / 2;
            if (tubeTopToBy > 0) {
                    tubeTopToBy = deadLineTop;
            }
        }
        //if (strategy.isSellWithMaxProfit() && tubeTopToBy > 0 && deltaTubePercent > tubeMaxPercent) {
        //    // средняя выше
        //    tubeTopToBy = tubeTopToBy - (tubeTopToBy - deadLineBottom) / 2;
        //}
        return List.of(deadLineBottom, deadLineTop, tubeTopToBy, tubeTopToInvest);
    }

    private List<Double> calculateTubeAvgDelta(String figi, OffsetDateTime currentDateTime, AInstrumentByFiatCrossStrategy strategy, Function<? super CandleDomainEntity, ? extends BigDecimal> keyExtractor)
    {
        var lengthTube = strategy.getAvgTubeLength();
        var lengthAvg = strategy.getAvgLength();
        var candleList = getCandlesByFigiByLength(figi, currentDateTime, lengthTube + 1, strategy.getInterval());
        if (candleList == null) {
            return null;
        }
        var candle = candleList.get(candleList.size() - 1);
        candleList.remove(candleList.size() - 1);

        BigDecimal min = keyExtractor.apply(candleList.get(0));
        BigDecimal max = keyExtractor.apply(candleList.get(0));
        Integer beginIMax = 0;
        Integer beginIMin = 0;
        for (var i = 1; i < lengthTube - lengthTube / 2; i++) {
            if (keyExtractor.apply(candleList.get(i)).compareTo(min) < 0) {
                min = keyExtractor.apply(candleList.get(i));
                beginIMin = i;
            }
            if (keyExtractor.apply(candleList.get(i)).compareTo(max) > 0) {
                max = keyExtractor.apply(candleList.get(i));
                beginIMax = i;
            }
        }
        Integer beginI = Math.max(beginIMax, beginIMin);
        beginI = Math.max(beginI - lengthAvg / 2, 0);
        beginI = Math.min(beginI, lengthTube - 2 * lengthAvg);
        BigDecimal beginMin = keyExtractor.apply(candleList.get(beginI));
        BigDecimal beginMax = keyExtractor.apply(candleList.get(beginI));
        Integer endIMax = lengthTube - 1;
        Integer endIMin = lengthTube - 1;
        BigDecimal endMin = keyExtractor.apply(candleList.get(endIMax));
        BigDecimal endMax = keyExtractor.apply(candleList.get(endIMin));
        for (var i = 1; i < lengthAvg; i++) {
            if (keyExtractor.apply(candleList.get(beginI + i)).compareTo(beginMin) < 0) {
                beginIMin = beginI + i;
                beginMin = keyExtractor.apply(candleList.get(beginIMin));
            }
            if (keyExtractor.apply(candleList.get(beginI + i)).compareTo(beginMax) > 0) {
                beginIMax = beginI + i;
                beginMax = keyExtractor.apply(candleList.get(beginIMax));
            }
            if (keyExtractor.apply(candleList.get(lengthTube - 1 - i)).compareTo(endMin) < 0) {
                endIMin = lengthTube - 1 - i;
                endMin = keyExtractor.apply(candleList.get(endIMin));
            }
            if (keyExtractor.apply(candleList.get(lengthTube - 1 - i)).compareTo(endMax) > 0) {
                endIMax = lengthTube - 1 - i;
                endMax = keyExtractor.apply(candleList.get(endIMax));
            }
        }

        BigDecimal avgDeltaPlus = endMax.subtract(beginMax.subtract(endMax).divide(BigDecimal.valueOf(endIMax - beginIMax), 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(lengthTube - endIMax)));
        BigDecimal avgDeltaMinus = endMin.subtract(beginMin.subtract(endMin).divide(BigDecimal.valueOf(endIMin - beginIMin), 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(lengthTube - endIMin)));
        var delta = avgDeltaPlus.subtract(avgDeltaMinus).divide(BigDecimal.valueOf(4), 8, RoundingMode.HALF_UP).abs();
        BigDecimal avg = avgDeltaMinus.add(delta).add(delta);

        if (avgDeltaMinus.compareTo(avgDeltaPlus) > 0) {
            var s = avgDeltaMinus;
            avgDeltaMinus = avgDeltaPlus;
            avgDeltaPlus = avgDeltaMinus;
            delta = BigDecimal.valueOf(-1);
        } else if (avg.divide(keyExtractor.apply(candle), 8, RoundingMode.HALF_UP).compareTo(BigDecimal.valueOf(0.01)) < 0) {
            avgDeltaMinus = avgDeltaMinus.subtract(delta);
            avgDeltaPlus = avgDeltaPlus.add(delta);
            delta = BigDecimal.valueOf(-1);
        }
        return List.of(
                avgDeltaMinus.doubleValue(),
                avgDeltaPlus.doubleValue(),
                avg.doubleValue(),
                delta.doubleValue()
        );
    }

    private List<Double> calculateAvgDelta(
            String figi,
            OffsetDateTime currentDateTime,
            AInstrumentByFiatCrossStrategy strategy,
            Function<? super CandleDomainEntity, ? extends BigDecimal> keyExtractor
    ) {
        var length = strategy.getAvgLength();
        var smaFastAvg = getSma(figi, currentDateTime, length, strategy.getInterval(), keyExtractor, length * 2);
        var smaFast2Avg = getSma(figi, currentDateTime, 2, strategy.getInterval(), keyExtractor, length * 2);
        if (smaFastAvg == null || smaFast2Avg == null) {
            return null;
        }
        Double avgDelta = 0.;
        Double avgDeltaAbs = 0.;
        List<Double> avgList = new ArrayList<Double>();
        List<Double> avgListMPlus = new ArrayList<Double>();
        List<Double> avgListMMinus = new ArrayList<Double>();
        //List<Double> avgListD = new ArrayList<Double>();
        for (var i = 0; i < length; i++) {
            var point = i + length;
            Double a = 0.;
            Double aPlus = 0.;
            Double aMinus = 0.;
            Integer aPlusNumber = 0;
            Integer aMinusNumber = 0;
            for (var j = 0; j < length; j++) {
                var delta = smaFast2Avg.get(point - j) - smaFastAvg.get(point - j);
                a += delta;
                if (delta >= 0) {
                    aPlus += delta;
                    aPlusNumber++;
                } else {
                    aMinus += delta;
                    aMinusNumber++;
                }
                //aAbs += Math.abs(smaFast2Avg.get(point - j) - smaFastAvg.get(point - j));
            }
            if (i == (length - 1)) {
                avgDelta = a / length;
            }
            var aCur = smaFastAvg.get(point) + a / length;
            avgList.add(aCur);
            avgListMPlus.add(aPlusNumber > 0 ? (aPlus / aPlusNumber) : 0.);
            avgListMMinus.add(aMinusNumber > 0 ? (aMinus / aMinusNumber) : 0.);
        }

        for (var i = 0; i < length; i++) {
            var point = i + length;
            Double avgDPlus = 0.;
            Double avgDMinus = 0.;
            Integer aPlusNumber = 0;
            Integer aMinusNumber = 0;
            for (var j = 0; j < length; j++) {
                var delta = smaFast2Avg.get(point - j) - smaFastAvg.get(point - j);
                if (delta > 0) {
                    avgDPlus += (delta - avgListMPlus.get(i)) * (delta - avgListMPlus.get(i));
                    aPlusNumber++;
                } else {
                    avgDMinus += (delta - avgListMMinus.get(i)) * (delta - avgListMMinus.get(i));
                    aMinusNumber++;
                }
            }
            if (aPlusNumber > 0) {
                avgDPlus = Math.sqrt(avgDPlus / aPlusNumber); // среднее квадратичное отклонение средней и smaFast
            }
            if (aMinusNumber > 0) {
                avgDMinus = Math.sqrt(avgDMinus / aMinusNumber); // среднее квадратичное отклонение средней и smaFast
            }

            Double a = 0.;
            var num = 0;
            for (var j = 0; j < length; j++) {
                var delta = smaFast2Avg.get(point - j) - smaFastAvg.get(point - j);
                if (delta > 0) {
                    if (delta <= avgListMPlus.get(i) + avgDPlus) {
                        a += delta;
                        num++;
                    }
                } else {
                    if (delta >= avgListMMinus.get(i) - avgDMinus) {
                        a += delta;
                        num++;
                    }
                }
            }
            var aCur = avgList.get(i);
            if (num > 0) {
                aCur = smaFastAvg.get(point) + a / num;
                avgList.set(i, aCur);
                if (i == (length - 1)) {
                    avgDelta = a / num;
                }
            }
            var smaFast2Cur = smaFast2Avg.get(point);
            var delta = Math.abs(smaFast2Cur - aCur);
            avgDeltaAbs += delta;

        }
        avgDeltaAbs = avgDeltaAbs / length;

        Double d = 0.;
        for (var i = 0; i < length; i++) {
            var point = i + length;
            var delta = Math.abs(smaFast2Avg.get(point) - avgList.get(i));
            d += (delta - avgDeltaAbs) * (delta - avgDeltaAbs);
        }
        d = Math.sqrt(d / length); // среднее квадратичное отклонение дельты от средней
        var avgDeltaAbsD = 0.;
        var num = 0;
        for (var i = 0; i < length; i++) {
            var point = i + length;
            var delta = Math.abs(smaFast2Avg.get(point) - avgList.get(i));
            //if (delta >= d) {
            if (delta >= avgDeltaAbs + d) {
                avgDeltaAbsD += delta;
                num++;
            }
        }
        if (num > 0) {
            avgDeltaAbsD = avgDeltaAbsD / num;
            avgDeltaAbs = avgDeltaAbsD;
        }
        var smaFastCur = smaFastAvg.get(smaFastAvg.size() - 1);
        var avg = smaFastCur + avgDelta;
        return List.of(avg - avgDeltaAbs, avg + avgDeltaAbs, avg, d, smaFastCur, getPercentMoveUp(avgList));
    }

    private List<Double> calculateAvgDeltaSimple(String figi,
                                                 OffsetDateTime currentDateTime,
                                                 AInstrumentByFiatCrossStrategy strategy,
                                                 Function<? super CandleDomainEntity, ? extends BigDecimal> keyExtractor,
                                                 List<Double> emaFast,
                                                 List<Double> smaSlow
    ) {
        var res = calculateAvgDelta(figi, currentDateTime, strategy, keyExtractor);
        var bottom = res.get(0);
        var top = res.get(1);
        var avg = res.get(2);
        var d = res.get(3);
        var ema = emaFast.get(emaFast.size() - 1);
        var avgListPercentMoveUp = res.get(5);
        var error = d * 0.05;
        var isMoveUp = avgListPercentMoveUp > strategy.getPercentMoveUpError();
        if (isMoveUp) {
            bottom += d;
        } else {
            top -= d;
            bottom -= d;
        }
        if (ema < avg - d) {
            bottom -= d;
        }
        if (ema < avg - d + error
                && ema < avg - error
                && !isMoveUp
        ) {
            bottom -= d;
            if (ema < avg - d) {
                bottom -= (avg - d - ema);
            }
        }
        return List.of(bottom, top, avg, d);
    }

    private List<Double> calculateAvgDeltaAdvance(
            String figi,
            OffsetDateTime currentDateTime,
            AInstrumentByFiatCrossStrategy strategy,
            Function<? super CandleDomainEntity, ? extends BigDecimal> keyExtractor,
            List<Double> emaFast,
            List<Double> smaSlow
    ) {
        var res = calculateAvgDelta(figi, currentDateTime, strategy, keyExtractor);
        var bottom = res.get(0);
        var top = res.get(1);
        var avg = res.get(2);
        var d = res.get(3);
        var smaFastCur = res.get(4);
        var avgListPercentMoveUp = res.get(5);
        var isMoveUp = avgListPercentMoveUp > strategy.getPercentMoveUpError()
                && getPercentMoveUp(emaFast) > -strategy.getPercentMoveUpError();
        if (isMoveUp) {
            bottom += d;
        } else if (!isMoveUp) {
            top -= d;
            bottom -= d;
        }
        var ema = emaFast.get(emaFast.size() - 1);
        var smaSlowCur = smaSlow.get(smaSlow.size() - 1);
        var error = d * 0.05;
        if (/*ema < avg - d + error
                && */ema < avg - error
        ) {
            top = avg + d + error;
            if (!isMoveUp) {
                bottom -= d;
                if (ema < avg - d) {
                    bottom -= (avg - d - ema);
                }
            }
        }

        if (ema < avg - d - error) {
            top = avg + d + error;
            bottom -= avg - bottom;
            bottom = Math.min(smaSlowCur, bottom) - error;

        } else if (ema > avg + error && isMoveUp) {
            top += top - avg;
            bottom = avg - d + error;
            //if (smaSlowCur > smaFastCur) {
            //    bottom = Math.min(bottom, smaFastCur - (smaSlowCur - smaFastCur));
            //}
        }
        if (smaSlowCur > smaFastCur && (smaSlowCur - smaFastCur) > d) {
            top = avg + error;
        }
        if (smaSlowCur < smaFastCur && (smaFastCur - smaSlowCur) > d) {
            bottom = Math.max(bottom, avg - error);
        }
        return List.of(bottom, top, avg, d);
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

    private synchronized Double getCashedValue(String indent)
    {
        return null;
        /*
        if (smaCashMap.containsKey(indent)) {
            //log.info("getCashedValue {} = {}", indent, smaCashMap.get(indent));
            return smaCashMap.get(indent);
        }
        //log.info("getCashedValue no value by {}", indent);
        return null;*/
    }

    private synchronized void addCashedValue(String indent, Double v)
    {
        /*
        if (smaCashMap.size() > cashSize) {
            smaCashMap.clear();
        }
        smaCashMap.put(indent, v);
        */
        //log.info("addCashedValue {} = {}", indent, v);
    }

    private String getMethodKey(Function<? super CandleDomainEntity, ? extends BigDecimal> keyExtractor)
    {
        return Integer.toHexString(keyExtractor.hashCode());
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
            var indent = "sma" + figi + notificationService.formatDateTime(candleList.get(i).getDateTime()) + interval + length + getMethodKey(keyExtractor);
            var smaCashed = getCashedValue(indent);
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
            var indent = "sma" + figi + notificationService.formatDateTime(candleListPrev.get(candleListPrev.size() - 1).getDateTime()) + interval + length + getMethodKey(keyExtractor);
            addCashedValue(indent, smaPrev);
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
                currentDateTime, 1 + 1, interval);
        if (candleList == null) {
            return null;
        }
        var indent = "ema" + figi + notificationService.formatDateTime(candleList.get(1).getDateTime()) + interval + length + getMethodKey(keyExtractor);
        var ema = getCashedValue(indent);
        var indentPrev = "ema" + figi + notificationService.formatDateTime(candleList.get(0).getDateTime()) + interval + length + getMethodKey(keyExtractor);
        var emaPrev = getCashedValue(indentPrev);

        if (ema == null || emaPrev == null) {
            candleList = getCandlesByFigiByLength(figi,
                    currentDateTime, length + 1, interval);
            if (candleList == null) {
                return null;
            }
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
        return candleHistoryService.getCandlesByFigiByLength(figi,
                currentDateTime, length, interval);
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
        currentPrices.put("buyClosingPrice", candle.getClosingPrice());
        currentPrices.put("buyOpenPrice", candle.getOpenPrice());
        return calculateBuyCriteria(candle,
                CandleDomainEntity::getClosingPrice, strategy, true);
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
        if (profitPercent.compareTo(BigDecimal.ZERO) > 0) {
            profitPercent = profitPercent.subtract(strategy.getPriceError().multiply(BigDecimal.valueOf(100)));
        } else {
            profitPercent = profitPercent.add(strategy.getPriceError().multiply(BigDecimal.valueOf(100)));
        }

        if (sellCriteria.getStopLossPercent() != null && profitPercent.floatValue() < -2 * sellCriteria.getStopLossPercent()) {
            return true;
        }

        if (sellCriteria.getExitProfitPercent() != null
                && profitPercent.floatValue() > sellCriteria.getExitProfitPercent()
        ) {
            return true;
        }

        //if (strategy.isNotCellIfBuy() && calculateBuyCriteria(candle, CandleDomainEntity::getClosingPrice, strategy, false)) {
        //    return false;
        //}

        if (!calculateCellCriteria(candle, CandleDomainEntity::getClosingPrice, strategy, profitPercent, true)) {
            return false;
        }


        // profit % > take profit %, profit % > 0.1%
        if (sellCriteria.getTakeProfitPercent() != null
                && profitPercent.floatValue() > sellCriteria.getTakeProfitPercent()
                && strategy.getPriceError().compareTo(profitPercent) < 0
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
                "Date|smaSlowest|smaSlow|smaFast|emaFast|ema2|bye|sell|position|deadLineBottom|deadLineTop|investBottom|investTop|smaTube|strategy|average|averageBottom|averageTop|openPrice",
                format,
                arguments
        );
    }
}
