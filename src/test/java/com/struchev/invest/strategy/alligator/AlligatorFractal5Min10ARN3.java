package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorFractal5Min10ARN3 extends AlligatorFractal5Min10AR {
    public Integer getFractalAverageNumber() { return 3; }
    public boolean isEnabled() { return true; }
}
