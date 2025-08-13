package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class AlligatorStrategy5MinSLimitUp06M extends AlligatorStrategy5MinSLimitUp06 {
    public Integer getAvgMaxCountLimitPriceByTrySell() {return 5; }
    public Integer getAvgMaxCountStopLossByTrySell() {return 5; }
    public OffsetDateTime getDayTimeEndLimitPriceByTrySell() { return OffsetDateTime.parse("2000-01-01T19:00:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME); }

    public boolean isEnabled() { return true; }
}
