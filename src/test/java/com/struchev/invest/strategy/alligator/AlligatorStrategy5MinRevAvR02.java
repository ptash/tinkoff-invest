package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRevAvR02 extends AlligatorStrategy5MinRevAvR {

    public Double getStopLossMaxDownLengthK() { return 0.20; }
    public Double getLimitPriceMaxDownLengthK() { return 0.20; }
    public boolean isEnabled() { return false; }
}
