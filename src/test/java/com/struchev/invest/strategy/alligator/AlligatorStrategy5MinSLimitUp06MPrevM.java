package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class AlligatorStrategy5MinSLimitUp06MPrevM extends AlligatorStrategy5MinSLimitUp06M {
    public boolean isRevMax() { return true; }
    //public boolean isSellMonthLengthFromBegin() { return true; }
    //public Double getSkipMonthLengthKByTrySell() { return 1.6; }
    //public boolean isBuyMaxOnlySmaUp() { return true; }
    public boolean isEnabled() { return false; }
}
