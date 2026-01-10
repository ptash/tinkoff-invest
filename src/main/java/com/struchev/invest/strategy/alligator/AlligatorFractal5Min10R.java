package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorFractal5Min10R extends AlligatorFractal5Min10 {
    public boolean isRev() { return true; }
}
