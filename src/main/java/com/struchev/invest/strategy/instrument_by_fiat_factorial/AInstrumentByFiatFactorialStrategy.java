package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import com.struchev.invest.strategy.AStrategy;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Стратегии торговли, зарабатывающие на изменении стоимости торговых инструментов, относительно фиатной валюты
 * Пример: продажа/покупка акций Apple за USD
 */
public abstract class AInstrumentByFiatFactorialStrategy extends AStrategy implements Cloneable {
    String extName;

    /**
     * Период паузы в торговле, если продали по stop loss критерию
     * @return
     */
    @Override
    public Duration getDelayBySL() {
        return null;
    }

    @Override
    public String getExtName() {
        return extName == null ? super.getName() : extName;
    }

    public void setExtName(String name) {
        this.extName = name;
    }

    public AInstrumentByFiatFactorialStrategy clone() {
        try {
            return (AInstrumentByFiatFactorialStrategy)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public Duration getHistoryDuration() {
        return Duration.ofDays(100);
    }

    @Override
    public final Type getType() {
        return Type.instrumentFactorialByFiat;
    }

    private SellLimitCriteria sellLimit;
    private Map<String, SellLimitCriteria> sellLimitMap = new HashMap<>();

    public SellLimitCriteria getSellLimitCriteriaOrig() {
        return SellLimitCriteria.builder().exitProfitPercent(2.0f).build();
    }

    public SellLimitCriteria getSellLimitCriteria() {
        if (null == this.sellLimit) {
            this.sellLimit = this.getSellLimitCriteriaOrig();
        }
        return this.sellLimit;
    }

    public SellLimitCriteria getSellLimitCriteria(String figi) {
        if (null == this.getSellLimitCriteria()) {
            return null;
        }
        if (!this.sellLimitMap.containsKey(figi)) {
            this.sellLimitMap.put(figi, SellLimitCriteria.builder().exitProfitPercent(this.getSellLimitCriteria().getExitProfitPercent()).build());
        }
        return this.sellLimitMap.get(figi);
    }

    public void setSellLimitCriteria(String figi, SellLimitCriteria sellLimit) {
        this.sellLimitMap.put(figi, sellLimit);
    }

    public interface CandleIntervalInterface {
        Integer getCandleMinLength();
        Integer getCandleMaxLength();
        Integer getCandleMaxInterval();
        Integer getCandleMaxIntervalLess();
        Integer getCandleUpMiddleLength();
        Integer getCandleUpLength();
        Float getCandleIntervalMinPercent();
        String getCandleInterval();
        Boolean isCandleIntervalTargetDirection(BigDecimal openPrice, BigDecimal closePrice);
        Boolean isCandleIntervalReverseDirection(BigDecimal openPrice, BigDecimal closePrice);
    }
    @Builder
    @Data
    public static class SellCriteria implements Cloneable, CandleIntervalInterface {

        public SellCriteria clone() {
            try {
                return (SellCriteria)super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
        // Процент (stop loss), если цена покупки падает на него, продаем
        Float stopLossSoftPercent;
        Integer stopLossSoftLength;
        Float stopLossPercent;
        Integer stopLossLength;
        // Процент (take profit), если цена покупки не растет на него, то даже
        Float takeProfitPercent;
        // Процент (take profit), если цена покупки растет на него, продаем в любом случае
        Float exitProfitPercent;
        Boolean isExitProfitInPercentMaxMax;
        Float exitProfitInPercentMax;
        Float exitProfitInPercentMin;
        Float exitProfitInPercentMaxForLoss;
        Float takeProfitPercentForLoss;
        Float exitProfitInPercentMaxForLoss2;
        Integer exitProfitInPercentMaxLoopIgnoreSize;
        Float exitProfitLossPercent;
        Float exitLossPercent;
        Boolean isSellUnderProfit;
        Integer sellUnderLossLength;

        Integer sellDownLength;
        Integer sellUpLength;

        Float profitPercentFromSellMinPrice;
        Float profitPercentFromSellMaxPrice;
        Integer profitPercentFromSellMinPriceLength;

        Integer candleMinLength;
        Integer candleMaxLength;
        Integer candleMaxInterval;
        Integer candleMaxIntervalLess;
        Integer candleUpMiddleLength;
        Integer candleUpLength;
        Float candleIntervalMinPercent;
        Float candleOnlyUpProfitMinPercent;
        Float candleOnlyUpStopLossPercent;
        Float candleProfitMinPercent;
        Float candlePriceMinFactor;
        Float candleExitProfitInPercentMax;
        Integer candleTrySimple;
        Integer candleUpPointLength;
        Integer candleUpMaxLength;
        Integer candleUpSkipLength;
        Float candleUpSkipDownBetweenFactor;
        Float candleUpMiddleFactor;
        Integer CandleUpMiddleFactorMinBegin;

        Integer downAfterUpSize;

        Boolean isOnlyStopLoss;


        String candleInterval;

        public Boolean isCandleIntervalTargetDirection(BigDecimal openPrice, BigDecimal closePrice) {
            return openPrice.compareTo(closePrice) > 0;
        }
        public Boolean isCandleIntervalReverseDirection(BigDecimal openPrice, BigDecimal closePrice) {
            return openPrice.compareTo(closePrice) <= 0;
        }
    }

    public SellCriteria getSellCriteria() {
        return SellCriteria.builder()
                .takeProfitPercent(0.3f)
                .exitProfitLossPercent(0.1f)
                .exitProfitInPercentMax(null)

                .stopLossPercent(0.4f)
                .stopLossLength(2)

                .stopLossSoftLength(5)
                .stopLossSoftPercent(0.2f)

                .exitLossPercent(4f)
                .isSellUnderProfit(false)
                .sellUnderLossLength(3)
                .isExitProfitInPercentMaxMax(false)
                .exitProfitInPercentMaxForLoss(null)
                .exitProfitInPercentMaxLoopIgnoreSize(0)
                .downAfterUpSize(1)
                .CandleUpMiddleFactorMinBegin(0)
                .isOnlyStopLoss(false)
                .candleInterval("1min")
                .build();
    }

    @Builder
    @Data
    public static class BuyCriteria implements Cloneable, CandleIntervalInterface {
        public BuyCriteria clone() {
            try {
                return (BuyCriteria)super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

        Float takeProfitLossPercent;
        Float takeProfitPercentBetween;
        Float takeProfitPercentBetweenCloseMax;
        Integer takeProfitPercentBetweenLength;
        Float takeProfitRatio;
        Float stopLossPercent;
        Float takeLossPercentBetween;
        Integer takeLossPercentBetweenLength;
        Float takeLossRatio;
        Float takeLossRatioMax;

        Double splashProfitRatio;
        Double splashProfitPercentMin;
        Double splashLossRatio;
        Double splashLossPercentMax;
        Boolean isAllUnderLoss;
        Boolean isOverProfit;
        Boolean isOverProfitWaitFirstUnderProfit;
        Double overProfitWaitFirstUnderProfitPercent;
        Double overProfitSkipWaitFirstOverProfitPercent;
        Double overProfitMaxPercent;
        Integer overProfitSkipIfUnderLossPrev;
        Integer overProfitSkipIfSellPrev;

        Integer skipIfOutPrevLength;
        Integer overProfitSkipIfOverProfitLength;
        Integer overProfitSkipIfOverProfitLengthError;

        Integer underLostWaitCandleEndInMinutes;

        Double profitPercentFromBuyMinPrice;
        Boolean isCurPriceMinMax;
        Integer profitPercentFromBuyMinPriceLength;
        Float profitPercentFromBuyMinPriceRelativeTop;
        Float profitPercentFromBuyMinPriceRelativeTopMin;
        Float profitPercentFromBuyMinPriceRelativeMin;
        Float profitPercentFromBuyMinPriceRelativeMax;
        Boolean isProfitPercentFromBuyMinPriceRelativeMaxMax;
        Double profitPercentFromBuyMinPriceProfit;
        Double profitPercentFromBuyMaxPrice;
        Double profitPercentFromBuyMaxPriceProfit;
        Double profitPercentFromBuyMaxPriceProfitSecond;
        // true - покупать с поиском лучшей
        Boolean isProfitPercentFromBuyPriceTop;
        Boolean isProfitPercentFromBuyPriceTopSecond;
        Boolean isAllOverProfit;
        Double allOverProfitSecondPercent;

        Integer notLossSellLength;
        Float notLossSellPercent;
        Float notLossSellPercentDiff;

        Float notLossBuyUnderPercent;

        Integer candleMinLength;
        Integer candleMaxLength;
        Integer candleMaxInterval;
        Integer candleMaxIntervalLess;
        Integer candleUpMiddleLength;
        Integer candleUpLength;
        Float candleIntervalMinPercent;

        Float candlePriceMinFactor;
        Float candlePriceMaxFactor;
        Float candlePriceMinMinFactor;
        Float candlePriceMinMaxFactor;
        Float candleMinFactor;
        Float candleMaxFactor;
        Float candleMinFactorCandle;
        Float candleProfitMinPercent;
        Float candleOnlyUpBetweenPercent;
        Float candleOnlyUpBetweenPointsPercent;
        Integer candleOnlyUpPointLength;
        Integer candleOnlyUpLength;
        Integer candleUpDownSkipCount;
        Integer candleUpOrDownMinCount;
        Integer candleUpDownMinCount;
        Integer candleUpDownSkipLength;
        Float candleUpDownSkipDeviationPercent;
        Integer candleUpSkipLength;
        Float candleUpMinFactor;
        Float candleUpMaxFactor;
        Float candleUpMinSizePercent;
        Boolean isCandleUpAny;
        Float candleUpMinFactorAny;
        Float candleUpMaxFactorAny;

        Integer candleDownMinMinPointLength;
        Integer candleDownMinMinMaxLength;

        Integer candleDownPointSize;

        Integer candleDownPointPointLengthSize;
        Integer candleDownPointPointLength;
        Integer candleDownPointLength;

        Integer candleUpPointLength;
        Integer candleUpMinLength;

        Integer candleUpSellPointLength;
        Integer candleUpSellMinLength;

        String candleInterval;

        Integer emaLength;

        Boolean isOnlyUp;
        Boolean isDownWithLimits;
        Float downStopLossFactor;
        Float isUpMaxPercentSeePrevSize;

        public Boolean isCandleIntervalTargetDirection(BigDecimal openPrice, BigDecimal closePrice) {
            return openPrice.compareTo(closePrice) <= 0;
        }
        public Boolean isCandleIntervalReverseDirection(BigDecimal openPrice, BigDecimal closePrice) {
            return openPrice.compareTo(closePrice) > 0;
        }
    }

    public BuyCriteria getBuyCriteria() {
        return BuyCriteria.builder()
                .takeProfitLossPercent(1.0f)

                .takeProfitPercentBetweenCloseMax(0.5f)
                //.takeProfitPercentBetween(1.5f)
                .takeProfitPercentBetweenLength(3)
                //.takeProfitRatio(7f)

                .stopLossPercent(0.2f)
                //.takeLossPercentBetween(1f)
                .takeLossPercentBetweenLength(3)
                //.takeLossRatio(5f)
                .takeLossRatioMax(2f)

                .splashProfitRatio(1.66)
                .splashProfitPercentMin(1.5)
                .splashLossRatio(2.0)
                //.splashLossPercentMax(0.2)
                .isAllUnderLoss(false)
                .isOverProfit(false)
                .isOverProfitWaitFirstUnderProfit(false)
                //.overProfitMaxPercent(0.2)
                .overProfitSkipIfUnderLossPrev(0)
                .isCurPriceMinMax(false)
                .profitPercentFromBuyMinPrice(null)
                .profitPercentFromBuyMinPriceLength(1)
                .profitPercentFromBuyMaxPrice(null)
                .isProfitPercentFromBuyPriceTop(true)
                .isProfitPercentFromBuyPriceTopSecond(true)
                .isAllOverProfit(false)
                .isProfitPercentFromBuyMinPriceRelativeMaxMax(false)
                .overProfitSkipIfOverProfitLengthError(0)
                .notLossSellLength(1)
                .notLossSellPercent(0.1f)
                .notLossSellPercentDiff(0.5f)
                .notLossBuyUnderPercent(0f)
                .isCandleUpAny(false)
                .isOnlyUp(false)
                .isDownWithLimits(false)
                .candleInterval("1min")
                .candleUpOrDownMinCount(0)
                .candleUpDownMinCount(2)
                .build();
    }

    public Integer getFactorialLength() { return 20; }
    public Integer getFactorialLengthFuture() { return 4; }
    public Integer getFactorialHistoryLength() {
        return this.getFactorialLength() * 200;
    }
    public List<Integer> getFactorialSizes() { return List.of(1); };
    public Integer getFactorialBestSize() { return 3; };
    public Integer getFactorialAvgSize() { return 3; };
    public Boolean isFactorialAvgMaxMin() { return false; };
    public Boolean isFactorialAvgByMiddle() { return false; };
    public Integer getFactorialDownAvgSize() { return 4; };
    public Integer getFactorialLossIgnoreSize() { return 3; };

    public Float getFactorialRatioI() { return -1f; }
    public Float getFactorialRatioValue() { return 0.15f; }
    public Float getFactorialRatioTime() { return 0.15f; }
    public Float getFactorialRatioOpen() { return 0.10f; }
    public Float getFactorialRatioCandle() { return 0.15f; }
    public Float getFactorialRatioCandleMax() { return 0.10f; }

    public String getFactorialInterval() { return "1hour"; }
    public String getInterval() { return "1min"; }

    public Integer getPriceDiffAvgLength() { return null; }

    public Float getPriceDiffAvg() { return 2f; }

    public Float getPriceDiffAvgPercentMin() { return 0.1f; }
}
