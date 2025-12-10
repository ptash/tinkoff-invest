package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRev3 extends AlligatorStrategy5MinRev2 {

    //public boolean isBuyMaxOnlySmaUp() { return false; }
    public boolean isPriceWantedAsMaxPrice() { return true; }
    public boolean isCandleOrigInMinCandleList() { return true; }

    public Double getBuyWaitMaxBuyDeltaK() { return 1.; }
    public Double getBuyWaitMaxDeltaK() { return 0.5; }
    public Double getBuyWaitMaxFromGreenBlueK() { return 0.5; }

    public boolean isEnabled() { return true; }
}
