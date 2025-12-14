package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRev5MaxLengthNull extends AlligatorStrategy5MinRev5 {
    public Integer getMinLowestPriceOverAnyMaxLength() { return null; }
    public boolean isEnabled() { return false; }
}
