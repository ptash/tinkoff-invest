package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinSLimitT extends AlligatorStrategy5MinSLimit {
    public Double getMaxPercentStopLossByTrySell() { return 0.5; } // чуть менее рисковано stop loss
    public Double getMaxPercentLimitPriceByTrySell() { return 0.2; }
    public boolean isMoveStopLossByTrySellByTrend() { return true; }
    public boolean isEnabled() { return true; }
}
