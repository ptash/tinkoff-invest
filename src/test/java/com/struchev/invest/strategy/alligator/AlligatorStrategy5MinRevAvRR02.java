package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRevAvRR02 extends AlligatorStrategy5MinRevAvR02 {

    public boolean isRev() { return false; }
    public boolean isEnabled() { return false; }
}
