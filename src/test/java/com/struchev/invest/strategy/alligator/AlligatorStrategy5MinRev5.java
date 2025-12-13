package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRev5 extends AlligatorStrategy5MinRev4 {
    public Integer getLimitPriceDownStepLength() { return 8; }
    public Double getLimitPriceDownProfitK() { return 0.13; }
    public Integer getLastFMinStepMaxLength() { return 25; }
    public boolean isEnabled() { return true; }
}
