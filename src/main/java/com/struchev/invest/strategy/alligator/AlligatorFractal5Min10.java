package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorFractal5Min10 extends AlligatorFractal5Min {
    public Float getBuyMinProfitPercent() { return .10f; }
    public Float getBuyMaxProfitPercent() { return 4.f; }

    public boolean isBuyMaxOnlySmaUp() {
        return false;
    }

    public boolean isBuyMaxOnlySmaDown() {
        return false;
    }

    public boolean isStopLossOnlyByLimit() {
        if (isRev()) {
            return true;
        }
        return true;
    }

    public boolean isPriceWantedAv() {
        if (isRev()) {
            return false;
        }
        return true;
    }

    //public Double getBuyOnDownProfitPercentK() { return 0.4; }

    public Integer getTrendUpLength() { return 15; }
    public Integer getTrendUpLengthOnSell() { return 1; }
}
