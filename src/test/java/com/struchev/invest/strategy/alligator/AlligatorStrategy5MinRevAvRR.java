package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRevAvRR extends AlligatorStrategy5MinRevAvR {

    public boolean isRev() { return false; }
    public boolean isEnabled() { return true; }
}
