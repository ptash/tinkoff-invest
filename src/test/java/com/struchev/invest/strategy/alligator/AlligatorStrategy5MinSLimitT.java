package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinSLimitT extends AlligatorStrategy5MinSLimit {
    public Double getMaxPercentStopLossByTrySell() { return 0.5; } // чуть менее рисковано stop loss
    public Double getMaxPercentLimitPriceByTrySell() { return 0.1; }
    public boolean isMoveStopLossByTrySellByTrend() { return false; }
    public boolean isSmaNearGreenBlueIsTrendDown() { return true; }
    public boolean isSkipSellSmaNearGreenBlue() { return true; }
    public boolean isSkipBySmaNearGreenBlue() { return true; }
    public boolean isEnabled() { return true; }
}
