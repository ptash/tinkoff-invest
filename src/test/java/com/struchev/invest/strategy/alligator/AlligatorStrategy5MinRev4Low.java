package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRev4Low extends AlligatorStrategy5MinRev4 {
    public boolean isMinLowestPriceUnderMinSameTrend() { return true; }
    public boolean isEnabled() { return false; }
}
