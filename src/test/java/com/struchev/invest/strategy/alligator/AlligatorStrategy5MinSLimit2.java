package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy5MinSLimit2 extends AlligatorStrategy5MinSLimit {
    public Double getSkipProfitByTrySell() { return 0.2; }

    public Double getMinPercentDeltaByTrySell() { return 0.15; }
    public Double getMaxPercentDeltaByTrySell() { return 0.3; }

    public Double getLimitPercentByCandle() { return .6 * 0.375 / 1.4; }
    public Double getLimitPriceByTrySell() { return 1.8; }

    public boolean isEnabled() { return true; }
}
