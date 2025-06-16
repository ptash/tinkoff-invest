package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinALimit extends AlligatorStrategy5Min {
    public boolean isEnabled() { return false; }
    public Double getLimitPercentByCandle() { return -1.; }
    //public Boolean isLimitPercentByPriceAlligator() { return true; }
}
