package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinSLimitT extends AlligatorStrategy5MinSLimit {
    public Double getMaxPercentStopLossByTrySell() { return 2.0; } // чуть менее рисковано stop loss
    public boolean isMoveStopLossByTrySellByTrend() { return true; }
}
