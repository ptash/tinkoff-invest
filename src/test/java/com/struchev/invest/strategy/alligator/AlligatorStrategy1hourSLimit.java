package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy1hourSLimit extends AlligatorStrategy1hour {
    public Double getSkipProfitByTrySell() { return 0.5; }
    public Double getLimitPriceByTrySell() { return 1.; }
    public Double getSellLimitPriceByTrySell() { return 3.0; }
}
