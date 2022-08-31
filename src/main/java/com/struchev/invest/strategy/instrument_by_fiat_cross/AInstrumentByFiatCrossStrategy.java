package com.struchev.invest.strategy.instrument_by_fiat_cross;

import com.struchev.invest.strategy.AStrategy;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * Стратегии торговли, зарабатывающие на изменении стоимости торговых инструментов, относительно фиатной валюты
 * Пример: продажа/покупка акций Apple за USD
 */
public abstract class AInstrumentByFiatCrossStrategy extends AStrategy {
    /**
     * Период паузы в торговле, если продали по stop loss критерию
     * @return
     */
    @Override
    public Duration getDelayBySL() {
        return Duration.ofHours(25);
    }

    public Duration getHistoryDuration() {
        return Duration.ofDays(255);
    }

    public String getInterval() {
        return "1min";
    }

    /**
     * Количество бар у самой медленной SMA
     * @return
     */
    public Integer getSmaSlowestLength() {
        return 200;
    }

    /**
     * Количество бар у медленной SMA
     * @return
     */
    public Integer getSmaSlowLength() {
        return 50;
    }

    /**
     * Количество бар у быстрой SMA
     * @return
     */
    public Integer getSmaFastLength() {
        return 20;
    }

    /**
     * Количество бар у быстрой EMA
     * @return
     */
    public Integer getEmaFastLength() {
        return 20;
    }

    @Builder
    @Data
    public static class BuyCriteria {
        // Процент (перцентиль), если цена за указанный период падает ниже него, покупаем
        Integer lessThenPercentile;
    }

    public abstract AInstrumentByFiatCrossStrategy.BuyCriteria getBuyCriteria();

    @Builder
    @Data
    public static class SellCriteria {
        // Процент (stop loss), если цена покупки падает на него, продаем
        Float stopLossPercent;
        // Процент (перцентиль), если цена за указанный период падает ниже него, продаем
        Integer stopLossPercentile;
        // Процент (take profit), если цена покупки растет на него, продаем
        Float takeProfitPercent;
        // Процент (take profit, перцентиль), если цена за указанный период растет выше него, продаем
        Integer takeProfitPercentile;
    }

    public abstract AInstrumentByFiatCrossStrategy.SellCriteria getSellCriteria();

    @Override
    public final Type getType() {
        return Type.instrumentCrossByFiat;
    }
}
