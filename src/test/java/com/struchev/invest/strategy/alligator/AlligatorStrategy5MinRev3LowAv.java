package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRev3LowAv extends AlligatorStrategy5MinRev3Low {
    public boolean isMaxDeltaByMinMax() { return true; }
    public boolean isEnabled() { return true; }
}
