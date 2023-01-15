package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import com.struchev.invest.strategy.AStrategy;
import com.struchev.invest.strategy.instrument_by_fiat_cross.AInstrumentByFiatCrossStrategy;
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
        return null;
        //return SellLimitCriteria.builder().exitProfitPercent(0.4f).build();
    }

    @Builder
    @Data
    public static class SellCriteria {
        // Процент (stop loss), если цена покупки падает на него, продаем
        Float stopLossPercent;
        // Процент (take profit), если цена покупки не растет на него, то даже
        Float takeProfitPercent;
        // Процент (take profit), если цена покупки растет на него, продаем в любом случае
        Float exitProfitPercent;
        Float exitProfitLossPercent;
    }

    public AInstrumentByFiatFactorialStrategy.SellCriteria getSellCriteria() {
        return SellCriteria.builder().takeProfitPercent(0.4f).stopLossPercent(0.2f)
                .exitProfitLossPercent(0.1f)
                .build();
    }

    @Builder
    @Data
    public static class BuyCriteria {
        Float takeProfitPercent;
        Float takeProfitPercentBetween;
        Integer takeProfitPercentBetweenLength;
        Float takeProfitRatio;
        Float stopLossPercent;
        Float takeLossPercentBetween;
        Integer takeLossPercentBetweenLength;
        Float takeLossRatio;
    }

    public  AInstrumentByFiatFactorialStrategy.BuyCriteria getBuyCriteria() {
        return AInstrumentByFiatFactorialStrategy.BuyCriteria.builder()
                .takeProfitPercent(0.5f)
                .takeProfitPercentBetween(1.5f)
                .takeProfitPercentBetweenLength(3)
                .takeProfitRatio(7f)
                .stopLossPercent(0.2f)
                .takeLossPercentBetween(1f)
                .takeLossPercentBetweenLength(3)
                .takeProfitRatio(5f)
                .build();
    }

    public Integer getFactorialLength() { return 20; }
    public Integer getFactorialLengthFuture() { return 40; }
    public Integer getFactorialHistoryLength() {
        return this.getFactorialLength() * 400;
    }
    public List<Integer> getFactorialSizes() { return List.of(1); };
    public Integer getFactorialBestSize() { return 2; };
    public Integer getFactorialAvgSize() { return 2; };
    public Integer getFactorialLossSize() { return 2; };

    public Float getFactorialRatioI() { return -1f; }
    public Float getFactorialRatioValue() { return 0.15f; }
}
