package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRevAvLS extends AlligatorStrategy5MinRevAvL {
    public Integer getBuySkipDownLength() { return 4; }
    public Integer getLimitPriceDownStepLength() { return 0; }
}
