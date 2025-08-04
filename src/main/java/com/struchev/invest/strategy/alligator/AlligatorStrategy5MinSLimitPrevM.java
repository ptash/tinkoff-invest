package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinSLimitPrevM extends AlligatorStrategy5MinSLimit {
    public boolean isRevMax() { return true; }
    public boolean isSellMonthLengthFromBegin() { return true; }
    public Double getSkipMonthLengthKByTrySell() { return 1.6; }
    public boolean isBuyMaxOnlySmaUp() { return true; }
}
