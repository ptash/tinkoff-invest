package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRev2Low extends AlligatorStrategy5MinRev2 {
    public boolean isMinLowestPriceUnderMinSameTrend() { return true; }
    public boolean isPriceWantedAsMaxPrice() { return true; }
    public boolean isEnabled() { return false; }
}
