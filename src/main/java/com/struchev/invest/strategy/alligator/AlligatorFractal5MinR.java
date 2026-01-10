package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorFractal5MinR extends AlligatorFractal5Min {
    public boolean isRev() { return true; }
}
