package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class AlligatorStrategy5Min extends AlligatorStrategy {
    public String getInterval() { return "5min"; }

    public boolean isEnabled() { return true; }

    public Integer getMaxDeep() { return 500; }
    public Integer getAlligatorMouthAverageMinSize() { return 20; }
    public Double getSellSkipCurAlligatorLengthDivider() { return 2.0; }

    public SellLimitCriteria getSellLimitCriteriaOrig() {
        return SellLimitCriteria.builder().exitProfitPercent(1.0f).build();
    }

    public OffsetDateTime getDayTimeEndTrading() { return OffsetDateTime.parse("2000-01-01T23:30:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME); }

    //public Double getLimitPercentByCandle() { return -1.; }
}
