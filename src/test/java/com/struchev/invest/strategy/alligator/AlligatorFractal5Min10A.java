package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorFractal5Min10A extends AlligatorFractal5Min10 {
    public boolean isFractalMinMaxInOne() { return true; }
    public boolean isFractalMinMaxInOneOnlyOnBuy() { return true; }
    public boolean isFractalMinMaxInOneOnlyDeltaOne() { return true; }

    public boolean isEnabled() { return true; }
}
