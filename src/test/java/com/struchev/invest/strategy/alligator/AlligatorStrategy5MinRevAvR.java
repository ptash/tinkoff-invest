package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRevAvR extends AlligatorStrategy5MinRevAv {

    public boolean isRevMaxRev() { return true; }
    public boolean isRev() { return true; }

    public boolean isBuyMaxOnlySmaUp() { return false; }

    public boolean isStopLossForce() { return false; }
    public boolean isStopLossByLimit() { return true; }
    public Integer getLimitPriceMaxK() { return 4; }
    public Integer getLimitPriceDownStepLength() { return 0; }
    public boolean isEnabled() { return false; }
}
