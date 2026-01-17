package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorFractal5Min10AA extends AlligatorFractal5Min10A {
    public boolean isFractalMinMaxInOneOnlyOnBuy() { return false; }
    public boolean isFractalMinMaxInOneOnlyDeltaOne() { return false; }
    public Integer getMaxDeep() { return 2000; }
}
