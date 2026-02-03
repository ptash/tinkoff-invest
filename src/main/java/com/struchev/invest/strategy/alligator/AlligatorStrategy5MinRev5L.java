package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRev5L extends AlligatorStrategy5MinRev5 {
    public Integer getReverseLongMinLength() { return 35; }
    public Integer getReverseLongMinAvLength() { return 5; }
    //public Integer getReverseSellLongMinMinLength() { return 3; }
    public Integer getReverseBuyLongMinMinLength() { return 3; }
    public Integer getReverseBuyMinMinLength() { return 3; }
    public Integer getLimitPriceDownStepLength() { return 0; }
    public Boolean isDownToMinProfitPercentStopLossByPercent() {return true; }
    public Integer getTrendUpLength() { return 10; }
    public Integer getUpLimitPriceToAvOpenCloseLength() { return 20; }
    public Double getUpLimitPriceToAvOpenCloseK() { return 2.; }
}
