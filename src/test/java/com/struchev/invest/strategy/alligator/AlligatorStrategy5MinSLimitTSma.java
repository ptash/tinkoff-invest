package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinSLimitTSma extends AlligatorStrategy5MinSLimitT {
    public boolean isSkipSellSmaNearGreenBlue() { return true; }
    public boolean isMoveStopLossByTrySellByTrend() { return true; }
    public boolean isBuyMaxOnlySmaUp() { return true; }
    public boolean isEnabled() { return true; }
}
