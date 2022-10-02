package com.struchev.invest.strategy.instrument_by_fiat_cross;

import com.struchev.invest.strategy.AStrategy;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Стратегии торговли, зарабатывающие на изменении стоимости торговых инструментов, относительно фиатной валюты
 * Пример: продажа/покупка акций Apple за USD
 */
public abstract class AInstrumentByFiatCrossStrategy extends AStrategy implements Cloneable {
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

    /**
     * Количество бар у коридорной SMA
     * @return
     */
    public Integer getSmaTubeLength() {
        return 2000;
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

    public Integer getAvgTubeLength() {
        return getSmaTubeLength();
    }

    public Integer getAvgLength() {
        return getSmaFastLength();
    }
    /**
     * Количество бар у быстрой EMA
     * @return
     */
    public Integer getEmaFastLength() {
        return 20;
    }

    /**
     * Процент вверх от smaFast, выше цены от короторого не покупаем
     * @return
     */
    public Double getDeadLinePercent() { return 0.0; }

    /**
     * Процент вверх от smaFast, выше цены от короторого не покупаем
     * @return
     */
    public Double getDeadLinePercentFromSmaSlowest() { return 5.0; }

    public Double getInvestPercentFromSmaSlowest() { return getDeadLinePercentFromSmaSlowest(); }

    /**
     * Максимально допустимый процент изменения при пересечения с smaFast
     * @return
     */
    public Double getMaxSmaFastCrossPercent() { return 0.5; }

    public Integer getTicksMoveUp() { return 2; }
    public Double getMinPercentMoveUp() { return 0.01; }
    public Double getMinPercentTubeMoveUp() { return -0.009; }
    public Double getMinPercentSmaSlowestMoveUp() { return -0.009; }

    public Double getPercentMoveUpError() { return 0.01; }
    public Double getMinPercentTubeBottomMoveUp() { return 1.000; }

    public Double getInvestPercentFromFast() { return -1.0; }
    public Double getMinInvestMoveUp() { return -10.0; }

    public Boolean isTubeTopNear() { return true; }

    public Boolean isTubeTopBlur() { return false; }

    public Boolean allowBuyUnderSmaTube() { return true; }

    public Boolean isBuyInvestCrossSmaEma2() { return false; }

    public Boolean isSellWithMaxProfit() { return false; }
    public Boolean isSellEma2UpOnBottom() { return true; }
    public Boolean isTubeAvgDeltaAdvance() { return false; }
    public Boolean isTubeAvgDeltaAdvance2() { return false; }
    public Boolean isTubeAvgDeltaAdvance3() { return false; }
    public Boolean isTubeAvgDeltaSimple() { return false; }

    public Boolean isNotCellIfBuy() { return false; }

    public Double getTubeAvgAdvanceDown() { return 0.5; }

    private Map<String, AInstrumentByFiatCrossStrategy> investStrategyMap = new HashMap<>();
    protected Boolean isInvestStrategy = false;
    public void setInvestStrategy(Boolean isInvestStrategy) {
        this.isInvestStrategy = isInvestStrategy;
    }

    public Object clone() throws CloneNotSupportedException { return super.clone(); }

    public AInstrumentByFiatCrossStrategy getFigiStrategy(String figi) {
        if (!investStrategyMap.containsKey(figi)) {
            try {
                investStrategyMap.put(figi, (AInstrumentByFiatCrossStrategy)this.clone());
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
        return investStrategyMap.get(figi);
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
        // Процент (take profit), если цена покупки не растет на него, то даже
        Float takeProfitPercent;
        // Процент (take profit), если цена покупки растет на него, продаем в любом случае
        Float exitProfitPercent;
    }

    public abstract AInstrumentByFiatCrossStrategy.SellCriteria getSellCriteria();

    @Override
    public final Type getType() {
        return Type.instrumentCrossByFiat;
    }
}
