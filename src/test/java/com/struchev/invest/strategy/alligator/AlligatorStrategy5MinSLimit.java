package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinSLimit extends AlligatorStrategy5Min {
    public Double getSkipProfitByTrySell() { return 0.5; }
    public Double getLimitPriceByTrySell() { return 1.; }
    public Double getSellLimitPriceByTrySell() { return 3.0; }
    //public boolean isBuyOnlyAfterMax2() { return true; }
    //public boolean isBuyMaxOnlySmaUp() { return true; }

    public boolean isEnabled() { return false; }
}
