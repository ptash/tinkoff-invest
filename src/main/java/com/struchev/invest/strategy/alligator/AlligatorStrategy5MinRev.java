package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRev extends AlligatorStrategy5Min {
    public boolean isReverse() { return true; }
    public boolean isAlligator() { return false; }
    public boolean isSkipBySmaFarGreenBlue() { return false; }

    public Double getMaxGreenPercent() { return 3.0; }

    public Float getBuyMinProfitPercent() { return .15f; }

    public SellLimitCriteria getSellLimitCriteriaOrig() {
        return SellLimitCriteria.builder().exitProfitPercent(2.0f).build();
    }
}
