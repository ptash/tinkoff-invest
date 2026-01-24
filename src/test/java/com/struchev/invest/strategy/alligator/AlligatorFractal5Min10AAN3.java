package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorFractal5Min10AAN3 extends AlligatorFractal5Min10AA {
    public Integer getFractalAverageNumber() { return 3; }
    public Double getBuyProfitPercentK() { return 0.4; }

    public boolean isEnabled() { return true; }
}
