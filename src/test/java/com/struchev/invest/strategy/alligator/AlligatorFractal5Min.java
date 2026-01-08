package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorFractal5Min extends AlligatorStrategy5Min {
    public boolean isAlligator() { return false; }
    public boolean isFractal() { return true; }

    public Integer getMaxDeep() { return 2000; }
    public Integer getMaxDeepFractal() { return 200; }

    public boolean isCandleOrigInMinCandleList() { return true; }
    public boolean isSkipBySmaFarGreenBlue() { return false; }

    public Double getMaxGreenPercent() { return 3.0; }

    public Float getBuyMinProfitPercent() { return .15f; }

    public SellLimitCriteria getSellLimitCriteriaOrig() {
        return SellLimitCriteria.builder().exitProfitPercent(2.0f).build();
    }
    public boolean isEnabled() { return true; }
}
