package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy1hour extends AlligatorStrategy {
    public String getInterval() { return "1hour"; }

    public Double getBuyWaitMaxDeltaK() {return 0.0; }
    public Double getBuyWaitMaxBuyDeltaK() {return 1.0; }
    public Double getLimitPercentByCandle() { return 2. * 0.375 / 1.4; }

    public boolean isEnabled() { return false; }
}
