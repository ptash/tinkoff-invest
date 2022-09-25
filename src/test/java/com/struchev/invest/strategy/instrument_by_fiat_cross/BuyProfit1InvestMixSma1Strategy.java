package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;

@Component
public class BuyProfit1InvestMixSma1Strategy extends BuyProfit1InvestMixStrategy {

    @Autowired
    BuyProfit1InvestPercent02SellWithMaxProfitPercentFromSma1CrossSimpleStrategy investStrategy;
    @Autowired
    BuyProfit1InvestPercent02SellWithMaxProfitCrossTubeStrategy crisisStrategy;

    public boolean isEnabled() { return true; }
}
