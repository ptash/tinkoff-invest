package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorFractal5Min10AARN3 extends AlligatorFractal5Min10AAN3 {
    public boolean isRev() { return true; }
    //public boolean isStopLossOnlyByLimit() { return true; }
    //public boolean isBuyMaxOnlySmaUp() { return false; }
    //public boolean isBuyMaxOnlySmaDown() { return true; }
    //public boolean isPriceWantedAv() { return false; }
    public boolean isEnabled() { return true; }
}
