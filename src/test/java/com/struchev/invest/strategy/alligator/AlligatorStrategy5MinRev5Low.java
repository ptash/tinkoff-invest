package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRev5Low extends AlligatorStrategy5MinRev5 {
    public boolean isMinLowestPriceUnderMinSameTrend() { return true; }
    public boolean isEnabled() { return true; }
}
