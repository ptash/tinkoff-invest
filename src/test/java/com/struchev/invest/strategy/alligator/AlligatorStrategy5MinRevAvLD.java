package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRevAvLD extends AlligatorStrategy5MinRevAvL {
    public Integer getLimitPriceDownStepLength() { return 30; }
    public boolean isEnabled() { return false; }
}
