package com.struchev.invest.strategy.alligator;

import com.struchev.invest.strategy.AStrategy;
import com.struchev.invest.strategy.IStrategyShort;

import java.time.Duration;

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
}
