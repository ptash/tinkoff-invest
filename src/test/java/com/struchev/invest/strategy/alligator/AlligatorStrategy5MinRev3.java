package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRev3 extends AlligatorStrategy5MinRev2 {

    //public boolean isBuyMaxOnlySmaUp() { return false; }
    public boolean isPriceWantedAsMaxPrice() { return true; }

    public boolean isEnabled() { return true; }
}
