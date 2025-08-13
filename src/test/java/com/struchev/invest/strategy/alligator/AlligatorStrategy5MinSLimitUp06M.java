package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinSLimitUp06M extends AlligatorStrategy5MinSLimitUp06 {
    public Integer getAvgMaxCountLimitPriceByTrySell() {return 5; }
    public Integer getAvgMaxCountStopLossByTrySell() {return 5; }

    public boolean isEnabled() { return true; }
}
