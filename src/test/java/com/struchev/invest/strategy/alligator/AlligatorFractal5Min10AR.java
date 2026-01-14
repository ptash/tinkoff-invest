package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorFractal5Min10AR extends AlligatorFractal5Min10A {
    public boolean isRev() { return true; }
    public boolean isEnabled() { return true; }
}
