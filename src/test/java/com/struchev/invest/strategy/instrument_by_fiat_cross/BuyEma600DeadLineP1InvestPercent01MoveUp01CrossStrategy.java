package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

@Component
public class BuyEma600DeadLineP1InvestPercent01MoveUp01CrossStrategy extends BuyEma600DeadLineP1CrossStrategy {
    public Double getInvestPercentFromFast() { return 0.1; }

    public Double getMinInvestMoveUp() { return 0.01; }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
