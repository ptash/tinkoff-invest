package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitCrossTube3Strategy extends BuyProfit1InvestPercent02SellWithMaxProfitCrossTube2Strategy {

    private Map FIGIES = Map.of(
            //"BBG005DXJS36", 1 // TCS Group (Tinkoff Bank holder)
    );

    public Map<String, Integer> getFigies() {
        return FIGIES;
    }
    public Boolean isTubeAvgDeltaAdvance3() { return true; }

    //public Integer getAvgLength() {return getSmaFastLength();}
    public Double getMinPercentMoveUp() { return 0.02; }

    public Double getPercentMoveUpError() { return 0.01; }

    public Integer getDelayPlusBySL() { return 30; }

    public boolean isEnabled() { return false; }
    public boolean isArchive() { return true; }
}
