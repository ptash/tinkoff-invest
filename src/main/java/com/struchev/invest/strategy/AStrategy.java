package com.struchev.invest.strategy;

import com.struchev.invest.strategy.instrument_by_fiat_cross.AInstrumentByFiatCrossStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

public abstract class AStrategy {

    /**
     * Карта FIGI: количество бумаг для торговли
     *
     * @return
     */
    public abstract Map<String, Integer> getFigies();

    /**
     * Количество бумаг для торговли заданным figi
     *
     * @param figi
     * @return
     */
    public final Integer getCount(String figi) {
        return getFigies().get(figi);
    }

    public final boolean isSuitableByFigi(String figi) {
        return getFigies().containsKey(figi);
    }

    public String getName() {
        return this.getClass().getSimpleName();
    }

    public boolean isEnabled() {
        return false;
    }

    public boolean isArchive() {
        return false;
    }

    public abstract Duration getDelayBySL();

    public Integer getDelayPlusBySL() { return null; }

    public Boolean isCheckBook() { return true; }

    public Double getDelayPlusBySLFactor() { return 0.; }

    public String getInterval() { return "1min"; }

    public BigDecimal getPriceError() { return BigDecimal.valueOf(0.002); }

    @AllArgsConstructor
    public enum Type {
        instrumentByFiat("Инструмент за фиат"),
        instrumentByInstrument("Инструмент за инструмент"),
        instrumentCrossByFiat("Инструмент с каналами за фиат");

        @Getter
        String title;
    }

    @Builder
    @Data
    public static class SellLimitCriteria {
        // Процент (take profit), если цена покупки растет на него, продаем в любом случае
        Float exitProfitPercent;
    }

    public AStrategy.SellLimitCriteria getSellLimitCriteria() { return null; }

    public abstract Type getType();

    public Duration getHistoryDuration()
    {
        return Duration.ofDays(0);
    }
}
