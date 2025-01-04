package com.struchev.invest.strategy;

import lombok.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
public abstract class AStrategy {
    @Autowired
    private StrategySettings config;
    /**
     * Карта FIGI: количество бумаг для торговли
     *
     * @return
     */
    public Map<String, Integer> getFigies() {
        return config.getFigies(this.getName());
    }

    public boolean isEnabled() {
        return config.isEnabled(this.getName());
    }

    public boolean isArchive() {
        return config.isArchive(this.getName());
    }

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

    public String getExtName() {
        return this.getClass().getSimpleName();
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
        instrumentCrossByFiat("Инструмент с каналами за фиат"),

        instrumentFactorialByFiat("Инструмент с факториалами за фиат"),
        alligator("Инструмент с аллигатором м факториалами за фиат");

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
    public AStrategy.SellLimitCriteria getSellLimitCriteria(String figi) { return null; }

    public abstract Type getType();

    public Duration getHistoryDuration()
    {
        return Duration.ofDays(0);
    }
}
