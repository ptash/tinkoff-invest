package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitCrossStrategy extends BuyEma600DeadLineP1Profit1DeadLine2InvestPercent02CrossStrategy {

    //public Double getInvestPercentFromFast() { return -1.0; }
    public Integer getTicksMoveUp() { return 5; }
    public Double getMinPercentTubeMoveUp() { return -0.050; }

    public Double getInvestPercentFromFast() { return -1.0; }
    //public Double getInvestPercentFromFast() { return 0.2; }

    public Boolean isSellWithMaxProfit() { return true; }

    public Boolean isBuyInvestCrossSmaEma2() { return false; }

    public Double getMinPercentSmaSlowestMoveUp() { return -0.10; }

    public Double getMaxSmaFastCrossPercent() { return 0.005; }

    public Integer getDelayPlusBySL() { return null; }
    //public Integer getDelayPlusBySL() { return 4 * 60; }

    public Boolean isTubeTopBlur() { return true; }

    //public SellCriteria getSellCriteria() {
    //    return SellCriteria.builder().takeProfitPercent(1f).stopLossPercent(1f).build();
    //}

    @Override
    public boolean isEnabled() { return true; }
}
