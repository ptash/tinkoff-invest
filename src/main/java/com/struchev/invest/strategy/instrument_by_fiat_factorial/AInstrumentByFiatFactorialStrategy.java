package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import com.struchev.invest.strategy.AStrategy;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.util.List;

/**
 * Стратегии торговли, зарабатывающие на изменении стоимости торговых инструментов, относительно фиатной валюты
 * Пример: продажа/покупка акций Apple за USD
 */
public abstract class AInstrumentByFiatFactorialStrategy extends AStrategy implements Cloneable {
    /**
     * Период паузы в торговле, если продали по stop loss критерию
     * @return
     */
    @Override
    public Duration getDelayBySL() {
        return null;
    }

    public Duration getHistoryDuration() {
        return Duration.ofDays(100);
    }

    @Override
    public final Type getType() {
        return Type.instrumentFactorialByFiat;
    }

    public SellLimitCriteria getSellLimitCriteria() {
        return SellLimitCriteria.builder().exitProfitPercent(2.0f).build();
    }

    @Builder
    @Data
    public static class SellCriteria {
        // Процент (stop loss), если цена покупки падает на него, продаем
        Float stopLossSoftPercent;
        Integer stopLossSoftLength;
        Float stopLossPercent;
        Integer stopLossLength;
        // Процент (take profit), если цена покупки не растет на него, то даже
        Float takeProfitPercent;
        // Процент (take profit), если цена покупки растет на него, продаем в любом случае
        Float exitProfitPercent;
        Float exitProfitLossPercent;
        Float exitLossPercent;
        Boolean isSellUnderProfit;
    }

    public AInstrumentByFiatFactorialStrategy.SellCriteria getSellCriteria() {
        return SellCriteria.builder()
                .takeProfitPercent(0.3f)
                .stopLossPercent(0.4f)
                .stopLossLength(2)
                .stopLossSoftPercent(0.2f)
                .stopLossSoftLength(5)
                .exitProfitLossPercent(0.1f)
                .exitLossPercent(8f)
                .isSellUnderProfit(false)
                .build();
    }

    @Builder
    @Data
    public static class BuyCriteria {
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
        Double overProfitMaxPercent;

        Double profitPercentFromBuyMinPrice;
    }

    public BuyCriteria getBuyCriteria() {
        return BuyCriteria.builder()
                .takeProfitLossPercent(1.0f)

                .takeProfitPercentBetweenCloseMax(0.5f)
                .takeProfitPercentBetween(1.5f)
                .takeProfitPercentBetweenLength(3)
                .takeProfitRatio(7f)

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
                //.overProfitMaxPercent(0.2)
                .profitPercentFromBuyMinPrice(0.1)
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
}
