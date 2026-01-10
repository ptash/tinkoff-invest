package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorFractal5Min10 extends AlligatorFractal5Min {
    public Float getBuyMinProfitPercent() { return .10f; }
    public Float getBuyMaxProfitPercent() { return 4.f; }
}
