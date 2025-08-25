package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class AlligatorStrategy5MinSLimitUp06TSma extends AlligatorStrategy5MinSLimitUp06 {
    public boolean isSkipSellSmaNearGreenBlue() { return true; }
    public boolean isMoveStopLossByTrySellByTrend() { return true; }
    public boolean isBuyMaxOnlySmaUp() { return true; }
    public boolean isEnabled() { return true; }
}
