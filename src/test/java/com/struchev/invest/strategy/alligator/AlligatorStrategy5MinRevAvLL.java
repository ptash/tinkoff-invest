package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRevAvLL extends AlligatorStrategy5MinRevAvL {
    //public Integer getLimitPriceDownStepLength() { return 30; }
    public boolean isStopLossByLimit() { return true; }
    public boolean isEnabled() { return false; }
}
