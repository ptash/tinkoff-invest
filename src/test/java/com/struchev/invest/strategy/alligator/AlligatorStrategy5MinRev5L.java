package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRev5L extends AlligatorStrategy5MinRev5 {
    public Integer getReverseLongMinLength() { return 30; }
    public Integer getReverseLongMinAvLength() { return 5; }
    public boolean isEnabled() { return true; }
}
