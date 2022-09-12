package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

@Component
public class BuyEma600DeadLineP1Profit1DeadLine2InvestPercent02CrossStrategy extends BuyEma600DeadLineP1DeadLine2InvestPercent02DelaySLNullCrossStrategy {

    //public Double getDeadLinePercent() { return 1.0; }

    //public Double getDeadLinePercentFromSmaSlowest() { return 2.0; }

    //public Double getInvestPercentFromFast() { return 0.3; }

    //@Override
    //public SellCriteria getSellCriteria() {
    //    return SellCriteria.builder().takeProfitPercent(0.5f).stopLossPercent(0.75f).exitProfitPercent(1f).build();
    //}

    //public Duration getDelayBySL() {
    //    return null;
    //}
    //public Integer getDelayPlusBySL() {
    //    return Long.valueOf(Duration.ofHours(4).getSeconds()).intValue();
    //}

    public Boolean isBuyInvestCrossSmaEma2() { return true; }
    public Double getDelayPlusBySLFactor() { return 1.0; }

    public Double getMinPercentTubeMoveUp() { return -0.020; }

    @Override
    public boolean isEnabled() { return true; }
}
