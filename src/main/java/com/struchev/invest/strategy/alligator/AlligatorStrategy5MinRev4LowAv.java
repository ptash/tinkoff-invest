package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRev4LowAv extends AlligatorStrategy5MinRev4 {
    public boolean isMaxDeltaByMinMax() { return true; }
    public boolean isMinLowestPriceUnderMinSameTrend() { return true; }
}
