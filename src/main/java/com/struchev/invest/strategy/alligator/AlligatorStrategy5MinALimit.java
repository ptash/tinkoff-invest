package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinALimit extends AlligatorStrategy5Min {
    public Double getLimitPercentByCandle() { return -1.; }
}
