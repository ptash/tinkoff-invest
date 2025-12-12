package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRev4 extends AlligatorStrategy5MinRev3 {
    //public Boolean isUpLimitPriceToMinProfitPercent() {return true; }
    public Double getDownFromPriceWantedK() { return 1.; }
    public Integer getLimitPriceDownStepLength() { return 10; }
    public Integer getMinLowestPriceUnderAnyLength() { return 2; }
    public boolean isEnabled() { return true; }
}
