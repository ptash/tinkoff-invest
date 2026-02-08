package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorFractal5Min10AAN3O extends AlligatorFractal5Min10AAN3 {
    public Integer getFractalLength() { return 10; }

    public boolean isBuyMaxOnlySmaUp() {
        if (isRev()) {
            return false;
        }
        return true;
    }

    public boolean isBuyMaxOnlySmaDown() {
        if (isRev()) {
            return true;
        }
        return false;
    }

    public boolean isEnabled() { return true; }
}
