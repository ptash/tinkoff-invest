package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRev4L extends AlligatorStrategy5MinRev4 {
    public Integer getReverseLongMinLength() { return 40; }
    public Integer getReverseLongMinAvLength() { return 5; }
    public Double getReverseLongMinAvAdK() { return 0.6; }
    public Integer getReverseBuyLongMinMinLength() { return 3; }
    public boolean isEnabled() { return true; }
}
