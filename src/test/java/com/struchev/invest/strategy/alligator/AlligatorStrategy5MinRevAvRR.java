package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRevAvRR extends AlligatorStrategy5MinRevAvR {

    public boolean isRevMaxRevRev() { return true; }
    public boolean isEnabled() { return true; }
}
