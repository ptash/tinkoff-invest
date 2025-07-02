package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinSLimit03 extends AlligatorStrategy5MinSLimit {
    public Double getLimitPercentByCandle() { return -1.; }
    /*
    public SellLimitCriteria getSellLimitCriteriaOrig() {
        return SellLimitCriteria.builder().exitProfitPercent(0.3f).build();
    }

    public Double getSkipProfitByTrySell() { return 0.1; }

    public Double getMinPercentDeltaByTrySell() { return 0.7; }
    public Double getMaxPercentDeltaByTrySell() { return 0.15; }

    public Double getLimitCorrectionK() { return .4; }
     */

    public boolean isEnabled() { return false; }
}
