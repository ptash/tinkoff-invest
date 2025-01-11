package com.struchev.invest.strategy.alligator;

import com.struchev.invest.strategy.AStrategy;
import com.struchev.invest.strategy.IStrategyShort;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

public abstract class AAlligatorStrategy extends AStrategy implements Cloneable, IStrategyShort {
    @Override
    public final Type getType() {
        return Type.alligator;
    }

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

    String extName;

    @Override
    public String getExtName() {
        return extName == null ? super.getName() : extName;
    }

    public void setExtName(String name) {
        this.extName = name;
    }

    public IStrategyShort clone() {
        try {
            return (AAlligatorStrategy)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public String getInterval() { return "5min"; }

    public Integer getSmaBlueLength() { return 13; }
    public Integer getSmaRedLength() { return 8; }
    public Integer getSmaGreenLength() { return 5; }

    public Integer getSmaBlueOffset() { return 8; }
    public Integer getSmaRedOffset() { return 5; }
    public Integer getSmaGreenOffset() { return 3; }

    public Integer getMaxDeep() { return 100; }
    public Integer getMaxDeepAlligatorMouth() { return 10; }
    public Integer getAlligatorMouthAverageMinSize() { return 4; }
    public Boolean isAlligatorMouthAverageLikeCur() { return true; }

    public Double getMinGreenPercent() { return 1.0; }

    public Double getMaxGreenPercent() { return 2.0; }

    public Double getSellSkipCurAlligatorLengthDivider() { return 3.0; }

    public Double getLimitPercentByCandle() { return 0.375 / 1.4; }

    public OffsetDateTime getDayTimeEndTrading() { return null; }
    public OffsetDateTime getDayTimeEndBuy() { return null; }

    private SellLimitCriteria sellLimit;
    private Map<String, SellLimitCriteria> sellLimitMap = new HashMap<>();

    public SellLimitCriteria getSellLimitCriteriaOrig() {
        return SellLimitCriteria.builder().exitProfitPercent(2.0f).build();
    }

    public SellLimitCriteria getSellLimitCriteria() {
        return this.sellLimit;
    }

    public SellLimitCriteria getSellLimitCriteria(String figi) {
        if (null == this.getSellLimitCriteriaOrig()) {
            return null;
        }
        if (!this.sellLimitMap.containsKey(figi)) {
            this.sellLimitMap.put(figi, SellLimitCriteria.builder().build());
        }
        return this.sellLimitMap.get(figi);
    }

    public void setSellLimitCriteria(String figi, SellLimitCriteria sellLimit) {
        this.sellLimitMap.put(figi, sellLimit);
    }
}
