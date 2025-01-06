package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5Min extends AlligatorStrategy {
    public String getInterval() { return "5min"; }

    public boolean isEnabled() { return true; }

    public Integer getMaxDeep() { return 500; }
    public Integer getAlligatorMouthAverageMinSize() { return 20; }
    public Double getSellSkipCurAlligatorLengthDivider() { return 2.0; }
}
