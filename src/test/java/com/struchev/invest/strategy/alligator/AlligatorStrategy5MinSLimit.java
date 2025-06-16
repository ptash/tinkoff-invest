package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinSLimit extends AlligatorStrategy5Min {
    public Double getLimitPriceByTrySell() { return 1.; }
    public Double getSellLimitPriceByTrySell() { return 2.; }
}
