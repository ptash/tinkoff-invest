package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorFractal5Min10RN3K1 extends AlligatorFractal5Min10RN3 {
    public boolean isSkipFractalK() { return true; }
    public boolean isEnabled() { return true; }
}
