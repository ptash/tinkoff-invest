package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class BuyProfit1InvestMixStrategy extends BuyEma600CrossStrategy {

    @Autowired
    private BuyProfit1InvestPercent02SellWithMaxProfitCrossSimpleStrategy investStrategy;
    @Autowired
    private BuyProfit1InvestPercent02SellWithMaxProfitCrossTubeStrategy crisisStrategy;

    public AInstrumentByFiatCrossStrategy getInvestStrategy() {
        return investStrategy;
    }

    public AInstrumentByFiatCrossStrategy getCrisisStrategy() {
        return crisisStrategy;
    }

    public Boolean isTubeAvgDeltaAdvance() {
        if (isInvestStrategy) {
            return getInvestStrategy().isTubeAvgDeltaAdvance();
        }
        return getCrisisStrategy().isTubeAvgDeltaAdvance();
    }

    public Boolean isTubeAvgDeltaSimple() {
        if (isInvestStrategy) {
            return getInvestStrategy().isTubeAvgDeltaSimple();
        }
        return getCrisisStrategy().isTubeAvgDeltaAdvance();
    }

    @Override
    public BuyCriteria getBuyCriteria() {
        if (isInvestStrategy) {
            return getInvestStrategy().getBuyCriteria();
        }
        return getCrisisStrategy().getBuyCriteria();
    }

    @Override
    public SellCriteria getSellCriteria() {
        if (isInvestStrategy) {
            return getInvestStrategy().getSellCriteria();
        }
        return getCrisisStrategy().getSellCriteria();
    }

    public Duration getDelayBySL() {
        if (isInvestStrategy) {
            return getInvestStrategy().getDelayBySL();
        }
        return getCrisisStrategy().getDelayBySL();
    }

    public Integer getDelayPlusBySL() {
        if (isInvestStrategy) {
            return getInvestStrategy().getDelayPlusBySL();
        }
        return getCrisisStrategy().getDelayPlusBySL();
    }

    public Integer getTicksMoveUp() {
        if (isInvestStrategy) {
            return getInvestStrategy().getTicksMoveUp();
        }
        return getCrisisStrategy().getTicksMoveUp();
    }

    public Double getMinPercentTubeMoveUp() {
        if (isInvestStrategy) {
            return getInvestStrategy().getMinPercentTubeMoveUp();
        }
        return getCrisisStrategy().getMinPercentTubeMoveUp();
    }

    public Double getInvestPercentFromFast() {
        if (isInvestStrategy) { return getInvestStrategy().getInvestPercentFromFast(); }
        return getCrisisStrategy().getInvestPercentFromFast();
    }
    //public Double getInvestPercentFromFast() { return 0.2; }

    public Boolean isSellWithMaxProfit() {
        if (isInvestStrategy) { return getInvestStrategy().isSellWithMaxProfit(); }
        return getCrisisStrategy().isSellWithMaxProfit();
    }

    public Boolean isBuyInvestCrossSmaEma2() {
        if (isInvestStrategy) { return getInvestStrategy().isBuyInvestCrossSmaEma2(); }
        return getCrisisStrategy().isBuyInvestCrossSmaEma2();
    }

    public Double getMinPercentSmaSlowestMoveUp() {
        if (isInvestStrategy) { return getInvestStrategy().getMinPercentSmaSlowestMoveUp(); }
        return getCrisisStrategy().getMinPercentSmaSlowestMoveUp();
    }

    public Double getMaxSmaFastCrossPercent() {
        if (isInvestStrategy) { return getInvestStrategy().getMaxSmaFastCrossPercent(); }
        return getCrisisStrategy().getMaxSmaFastCrossPercent();
    }

    public Boolean isTubeTopBlur() {
        if (isInvestStrategy) { return getInvestStrategy().isTubeTopBlur(); }
        return getCrisisStrategy().isTubeTopBlur();
    }

    public Double getDelayPlusBySLFactor() {
        if (isInvestStrategy) { return getInvestStrategy().getDelayPlusBySLFactor(); }
        return getCrisisStrategy().getDelayPlusBySLFactor();
    }

    public Boolean isTubeTopNear() {
        if (isInvestStrategy) { return getInvestStrategy().isTubeTopNear(); }
        return getCrisisStrategy().isTubeTopNear();
    }

    public Double getMinPercentTubeBottomMoveUp() {
        if (isInvestStrategy) { return getInvestStrategy().getMinPercentTubeBottomMoveUp(); }
        return getCrisisStrategy().getMinPercentTubeBottomMoveUp();
    }

    public Double getDeadLinePercent() {
        if (isInvestStrategy) { return getInvestStrategy().getDeadLinePercent(); }
        return getCrisisStrategy().getDeadLinePercent();
    }
    public Double getDeadLinePercentFromSmaSlowest() {
        if (isInvestStrategy) { return getInvestStrategy().getDeadLinePercentFromSmaSlowest(); }
        return getCrisisStrategy().getDeadLinePercentFromSmaSlowest();
    }
    public boolean isEnabled() { return true; }
}
