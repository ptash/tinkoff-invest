package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRev4LowAv extends AlligatorStrategy5MinRev4Low {
    public boolean isMaxDeltaByMinMax() { return true; }
    public boolean isEnabled() { return true; }
}
