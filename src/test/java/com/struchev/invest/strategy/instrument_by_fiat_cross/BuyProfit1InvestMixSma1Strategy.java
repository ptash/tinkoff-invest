package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;

@Component
public class BuyProfit1InvestMixSma1Strategy extends BuyEma600CrossStrategy {

    @Autowired
    BuyProfit1InvestPercent02SellWithMaxProfitPercentFromSma1CrossSimpleStrategy investStrategy;
    @Autowired
    BuyProfit1InvestPercent02SellWithMaxProfitCrossTubeStrategy crisisStrategy;

    public Boolean isTubeAvgDeltaAdvance() {
        if (isInvestStrategy) {
            return investStrategy.isTubeAvgDeltaAdvance();
        }
        return crisisStrategy.isTubeAvgDeltaAdvance();
    }

    public Boolean isTubeAvgDeltaSimple() {
        if (isInvestStrategy) {
            return investStrategy.isTubeAvgDeltaSimple();
        }
        return crisisStrategy.isTubeAvgDeltaAdvance();
    }

    @Override
    public BuyCriteria getBuyCriteria() {
        if (isInvestStrategy) {
            return investStrategy.getBuyCriteria();
        }
        return crisisStrategy.getBuyCriteria();
    }

    @Override
    public SellCriteria getSellCriteria() {
        if (isInvestStrategy) {
            return investStrategy.getSellCriteria();
        }
        return crisisStrategy.getSellCriteria();
    }

    public Duration getDelayBySL() {
        if (isInvestStrategy) {
            return investStrategy.getDelayBySL();
        }
        return crisisStrategy.getDelayBySL();
    }

    public Integer getDelayPlusBySL() {
        if (isInvestStrategy) {
            return investStrategy.getDelayPlusBySL();
        }
        return crisisStrategy.getDelayPlusBySL();
    }

    public Integer getTicksMoveUp() {
        if (isInvestStrategy) {
            return investStrategy.getTicksMoveUp();
        }
        return crisisStrategy.getTicksMoveUp();
    }

    public Double getMinPercentTubeMoveUp() {
        if (isInvestStrategy) {
            return investStrategy.getMinPercentTubeMoveUp();
        }
        return crisisStrategy.getMinPercentTubeMoveUp();
    }

    public Double getInvestPercentFromFast() {
        if (isInvestStrategy) { return investStrategy.getInvestPercentFromFast(); }
        return crisisStrategy.getInvestPercentFromFast();
    }
    //public Double getInvestPercentFromFast() { return 0.2; }

    public Boolean isSellWithMaxProfit() {
        if (isInvestStrategy) { return investStrategy.isSellWithMaxProfit(); }
        return crisisStrategy.isSellWithMaxProfit();
    }

    public Boolean isBuyInvestCrossSmaEma2() {
        if (isInvestStrategy) { return investStrategy.isBuyInvestCrossSmaEma2(); }
        return crisisStrategy.isBuyInvestCrossSmaEma2();
    }

    public Double getMinPercentSmaSlowestMoveUp() {
        if (isInvestStrategy) { return investStrategy.getMinPercentSmaSlowestMoveUp(); }
        return crisisStrategy.getMinPercentSmaSlowestMoveUp();
    }

    public Double getMaxSmaFastCrossPercent() {
        if (isInvestStrategy) { return investStrategy.getMaxSmaFastCrossPercent(); }
        return crisisStrategy.getMaxSmaFastCrossPercent();
    }

    public Boolean isTubeTopBlur() {
        if (isInvestStrategy) { return investStrategy.isTubeTopBlur(); }
        return crisisStrategy.isTubeTopBlur();
    }

    public Double getDelayPlusBySLFactor() {
        if (isInvestStrategy) { return investStrategy.getDelayPlusBySLFactor(); }
        return crisisStrategy.getDelayPlusBySLFactor();
    }

    public Boolean isTubeTopNear() {
        if (isInvestStrategy) { return investStrategy.isTubeTopNear(); }
        return crisisStrategy.isTubeTopNear();
    }

    public Double getMinPercentTubeBottomMoveUp() {
        if (isInvestStrategy) { return investStrategy.getMinPercentTubeBottomMoveUp(); }
        return crisisStrategy.getMinPercentTubeBottomMoveUp();
    }

    public Double getDeadLinePercent() {
        if (isInvestStrategy) { return investStrategy.getDeadLinePercent(); }
        return crisisStrategy.getDeadLinePercent();
    }
    public Double getDeadLinePercentFromSmaSlowest() {
        if (isInvestStrategy) { return investStrategy.getDeadLinePercentFromSmaSlowest(); }
        return crisisStrategy.getDeadLinePercentFromSmaSlowest();
    }
    public boolean isEnabled() { return true; }
}
