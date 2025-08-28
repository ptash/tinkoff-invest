package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinALimit extends AlligatorStrategy5Min {
    public Double getLimitPercentByCandle() { return -1.; }

    public Double getLimitPercentUp1() { return -1.; }
    public Double getLimitPercentUp2() { return -1.; }
    public Double getLimitPercentUp3() { return -1.; }

    public boolean isAlligatorMouthOffset() { return false; }

    public Double getMinGreenPercent() { return 0.5; }

    public boolean isRevMax() { return true; }

    public Double getLimitDeltaK() { return 1.0; }

    public boolean isEnabled() { return false; }
}
