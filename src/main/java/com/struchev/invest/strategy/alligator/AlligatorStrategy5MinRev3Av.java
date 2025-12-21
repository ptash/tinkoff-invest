package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRev3Av extends AlligatorStrategy5MinRev3 {
    public boolean isMaxDeltaByMinMax() { return true; }
}
