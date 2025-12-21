package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinRevAv extends AlligatorStrategy5MinRev4 {
    public boolean isMaxDeltaByMinMax() { return true; }
    public boolean isMaxDeltaByMinMaxOnly() { return true; }
    public Boolean isUpLimitPriceToWaitMax() {return true; }
    public Boolean isWaitMaxBuyByMinMax() {return true; }

    //==========

    public Integer getLimitPriceDownStepLength() { return 30; }
    public Double getLimitPriceDownProfitK() { return 0.15; }

    public Integer getMinLowestPriceOverAnyMinLength() { return 7; }
    public Integer getMinLowestPriceOverAnyMaxLength() { return 14; }
    public Boolean isDownPriceWantedToMinProfitPercent() {return false; }

    public Integer getLastFMinStepMaxLength() { return 25; }

    public boolean isEnabled() { return true; }
}
