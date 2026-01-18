package com.struchev.invest.strategy.alligator;

import com.struchev.invest.expression.Date;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class AlligatorFractal5Min extends AlligatorStrategy5Min {
    public boolean isAlligator() { return false; }
    public boolean isFractal() { return true; }
    //public boolean isFractalInDayTimeTrading() { return true; }

    public Integer getMaxDeep() { return 4000; }
    public Integer getMaxDeepFractal() { return 400; }

    public boolean isCandleOrigInMinCandleList() { return true; }
    public boolean isSkipBySmaFarGreenBlue() { return false; }

    public Double getMaxGreenPercent() { return 3.0; }

    public Float getBuyMinProfitPercent() { return .25f; }
    public boolean isMinProfitPercent() { return false; }

    //public boolean isStopLossByLimit() { return true; }
    public boolean isStopLossOnlyByLimit() { return true; }

    public boolean isStopLossForce() { return true; }
    public boolean isStopLossForcePrev() { return true; }

    public SellLimitCriteria getSellLimitCriteriaOrig() {
        return SellLimitCriteria.builder().exitProfitPercent(2.0f).build();
    }

    public OffsetDateTime getDayTimeEndTrading(OffsetDateTime dateTime) {
        var dateInZone = Date.getDateTimeInZone(dateTime);;
        if (dateInZone.getDayOfWeek().getValue() < 6) {
            return OffsetDateTime.parse("2000-01-01T23:30:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } else {
            // не торговать по выходным
            return OffsetDateTime.parse("2000-01-01T01:35:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }
    public OffsetDateTime getDayTimeEndBuy(OffsetDateTime dateTime) {
        var dateInZone = Date.getDateTimeInZone(dateTime);;
        if (dateInZone.getDayOfWeek().getValue() < 6) {
            return OffsetDateTime.parse("2000-01-01T22:05:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } else {
            // не торговать по выходным
            return OffsetDateTime.parse("2000-01-01T01:10:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }

    public boolean isEnabled() { return true; }
}
