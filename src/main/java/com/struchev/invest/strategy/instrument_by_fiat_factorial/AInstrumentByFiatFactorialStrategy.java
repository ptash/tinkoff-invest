package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import com.struchev.invest.strategy.AStrategy;
import com.struchev.invest.strategy.instrument_by_fiat_cross.AInstrumentByFiatCrossStrategy;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        return SellLimitCriteria.builder().exitProfitPercent(0.7f).build();
    }

    public AInstrumentByFiatCrossStrategy.SellCriteria getSellCriteria() {
        return AInstrumentByFiatCrossStrategy.SellCriteria.builder().takeProfitPercent(0.5f).stopLossPercent(0.5f).build();
    }

    @Builder
    @Data
    public static class BuyCriteria {
        Float takeProfitPercent;
        Float stopLossPercent;
    }

    public  AInstrumentByFiatFactorialStrategy.BuyCriteria getBuyCriteria() {
        return AInstrumentByFiatFactorialStrategy.BuyCriteria.builder().takeProfitPercent(1f).stopLossPercent(0.2f).build();
    }

    public Integer getFactorialLength() { return 50; }
    public Integer getFactorialHistoryLength() {
        return this.getFactorialLength() * 200;
    }
    public List<Integer> getFactorialSizes() { return List.of(1, 2); };
}
