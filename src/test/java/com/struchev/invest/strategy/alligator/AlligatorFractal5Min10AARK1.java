package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorFractal5Min10AARK1 extends AlligatorFractal5Min10AAR {
    public boolean isSkipFractalK() { return true; }
    public boolean isEnabled() { return true; }
}
