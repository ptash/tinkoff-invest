package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinSLimitUp06 extends AlligatorStrategy5MinSLimit {
    public SellLimitCriteria getSellLimitCriteriaOrig() {
        return SellLimitCriteria.builder().exitProfitPercent(0.6f).build();
    }

    public Double getSkipProfitByTrySell() { return 0.2; }

    public Double getMinPercentDeltaByTrySell() { return 0.15; }
    public Double getMaxPercentDeltaByTrySell() { return 0.3; }
    public Double getLimitPriceByTrySell() { return 1.8; }

    public boolean isBuyMaxOnlySmaUp() { return true; }

    public boolean isEnabled() { return true; }
}
