package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRevAvL extends AlligatorStrategy5MinRevAv {
    public Integer getLimitPriceMaxK() { return 4; }
}
