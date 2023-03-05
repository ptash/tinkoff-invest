package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial3TopAllCloseStrategy extends Factorial3TopAllStrategy {
    public SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();
        sell.setIsExitProfitInPercentMaxMax(false);
        return sell;
    }

    public Boolean isEnable() { return false; }
}
