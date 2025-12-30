package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRevAvR extends AlligatorStrategy5MinRevAv {

    public boolean isRevMaxRev() { return true; }

    public boolean isBuyMaxOnlySmaUp() { return false; }

    public boolean isStopLossForce() { return false; }
    public boolean isEnabled() { return true; }
}
