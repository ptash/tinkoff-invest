package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyEma600DeadLineP1DeadLine2CrossStrategy extends BuyEma600CrossStrategy {

    public Double getDeadLinePercent() { return 1.0; }

    public Double getDeadLinePercentFromSmaSlowest() { return 2.0; }

    @Override
    public SellCriteria getSellCriteria() {
        return SellCriteria.builder().takeProfitPercent(1f).stopLossPercent(2f).build();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
