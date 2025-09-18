package com.struchev.invest.strategy.sma;

import com.struchev.invest.expression.Date;
import com.struchev.invest.strategy.AStrategy;
import com.struchev.invest.strategy.IStrategyShort;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public abstract class ASmaStrategy extends AStrategy implements Cloneable, IStrategyShort {
    @Override
    public final Type getType() {
        return Type.sma;
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
            return (ASmaStrategy)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public String getInterval() { return "5min"; }

    public Integer getSmaLength() { return 13 * 10; }
    public Integer getMaxDeep() { return 300; }
    public Integer getMinLineStep() { return 30; }
    public Integer getMinErrStep() { return 15; }

    private SellLimitCriteria sellLimit;
    private Map<String, SellLimitCriteria> sellLimitMap = new HashMap<>();

    public SellLimitCriteria getSellLimitCriteriaOrig() {
        return SellLimitCriteria.builder().exitProfitPercent(0.6f).build();
    }

    public Double getSellLimitPercentForUnderStop() { return 0.3; }

    public Double getDiffFromSmaStandard() { return 1.0; }

    public Double getStopPercentK() { return 2.0; }

    public Integer getDeepForMaxLimitPrice() { return 100; }

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

    //public OffsetDateTime getDayTimeEndTrading(OffsetDateTime dateTime) { return null; }
    //public OffsetDateTime getDayTimeEndBuy(OffsetDateTime dateTime) { return null; }

    public OffsetDateTime getDayTimeEndTrading(OffsetDateTime dateTime) {
        var dateInZone = Date.getDateTimeInZone(dateTime);;
        if (dateInZone.getDayOfWeek().getValue() < 6) {
            return OffsetDateTime.parse("2000-01-01T23:30:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } else {
            return OffsetDateTime.parse("2000-01-01T18:35:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }
    public OffsetDateTime getDayTimeEndBuy(OffsetDateTime dateTime) {
        var dateInZone = Date.getDateTimeInZone(dateTime);;
        if (dateInZone.getDayOfWeek().getValue() < 6) {
            return OffsetDateTime.parse("2000-01-01T22:05:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } else {
            return OffsetDateTime.parse("2000-01-01T18:10:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }

    /*
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
    public Double getLimitPercentUp1() { return 1.618; }
    public Double getLimitPercentUp2() { return 2.618; }
    public Double getLimitPercentUp3() { return 4.236; }

    public boolean isAlligatorMouthOffset() { return true; }
    public Double getLimitCorrectionK() { return 1.0; }
    public Double getLimitDeltaK() { return 0.5; }
    public Boolean isLimitPercentByPriceAlligator() { return false; }

    //public OffsetDateTime getDayTimeEndTrading(OffsetDateTime dateTime) { return null; }
    //public OffsetDateTime getDayTimeEndBuy(OffsetDateTime dateTime) { return null; }

    public Integer getFMaxCandleCountFromEnd() { return null; }

    public Boolean isLimitPriceFromMouthOrig() { return false; }

    public Double getBuyWaitMaxDeltaK() {return 1.0; }
    public Double getBuyWaitMaxBuyDeltaK() {return 1.0; }

    public boolean isMoveStopLossByTrySellByTrend() { return false; }
    public boolean isSmaNearGreenBlueIsTrendDown() { return false; }
    public boolean isSkipBySmaNearGreenBlue() { return false; }
    public boolean isSkipBySmaFarGreenBlue() { return false; }
    public boolean isBuyOnlyAfterMax2() { return false; }
    public boolean isBuyMaxOnlySmaUp() { return false; }
    public boolean isSkipSellSmaNearGreenBlue() { return false; }

    public boolean isReverse() { return false; }
    public boolean isRevMax() { return false; }
    public Float getBuyMinProfitPercent() { return null; }
    public Double getReverseStopLossK() { return 1.5; }


    //public Integer getSmaLength() { return 13 * 10; }
    public Double getSkipProfitByTrySell() { return 0.; }
    public Double getMinPercentDeltaByTrySell() { return 0.3; }
    public Double getMaxPercentDeltaByTrySell() { return 1.0; }
    public Double getMaxPercentStopLossByTrySell() { return 5.0; } // этого значения не достигнем
    public Double getMaxPercentLimitPriceByTrySell() { return 5.0; } // этого значения не достигнем
    public Integer getAvgMaxCountLimitPriceByTrySell() {return 0; }
    public Integer getAvgMaxCountStopLossByTrySell() {return 0; }
    public OffsetDateTime getDayTimeEndLimitPriceByTrySell() { return null; }
    public boolean isSkipSellByTrySell() {return false; }
    public Double getLimitPriceByTrySell() { return null; }
    public Double getSellLimitPriceByTrySell() { return null; }

    public boolean isSellMonthLengthFromBegin() { return false; }
    public Double getSkipMonthLengthKByTrySell() { return 10.; }
     */
}
