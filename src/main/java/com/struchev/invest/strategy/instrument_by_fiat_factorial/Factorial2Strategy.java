package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class Factorial2Strategy extends AInstrumentByFiatFactorialStrategy {

    public Integer getFactorialAvgSize() { return 8; };

    public Boolean isFactorialAvgByMiddle() { return true; };

    public AInstrumentByFiatFactorialStrategy.SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();
        sell.setIsSellUnderProfit(true);
        return sell;
    }
}
