package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRev4 extends AlligatorStrategy5MinRev3 {
    public Boolean isUpLimitPriceToMinProfitPercent() {return true; }
    public Double getDownFromPriceWantedK() { return 1.; }
    public Integer getLimitPriceDownStepLength() { return 15; }
    public Double getLimitPriceDownProfitK() { return 0.25; }
    public Integer getMinLowestPriceUnderAnyLength() { return 2; }
    public Integer getReverseUpMinLength() { return 5; }
    public boolean isEnabled() { return true; }
}
