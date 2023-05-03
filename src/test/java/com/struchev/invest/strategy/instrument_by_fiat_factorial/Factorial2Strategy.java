package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial2Strategy extends FactorialStrategy {
    public Integer getFactorialAvgSize() { return 8; };

    public Boolean isFactorialAvgByMiddle() { return true; };

    public AInstrumentByFiatFactorialStrategy.SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();
        sell.setIsSellUnderProfit(true);
        return sell;
    }
    public boolean isEnabled() { return true; }
}
