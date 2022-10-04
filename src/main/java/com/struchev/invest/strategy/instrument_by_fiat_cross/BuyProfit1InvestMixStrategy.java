package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class BuyProfit1InvestMixStrategy extends ABuyEma600CrossStrategy {

    private Map FIGIES = Map.of(
            //"BBG00178PGX3", 12    // VK

            /*
            "BBG000J3D1Y8", 6, // OraSure Technologies Inc
            "BBG005F1DK91", 2, // G1
            //"BBG004NLQHL0", 2, // Fastly Inc
            //"BBG000BLY663", 1, // CROCS // в архив
            "BBG0016XJ8S0", 7    // TAL Education Group*/
    );

    public Map<String, Integer> getFigies() {
        return FIGIES;
    }

    @Autowired
    private BuyProfit1InvestPercent02SellWithMaxProfitCrossSimpleStrategy investStrategy;

    @Qualifier("buyProfit1InvestPercent02SellWithMaxProfitCrossTubeStrategy")
    @Autowired
    private BuyProfit1InvestPercent02SellWithMaxProfitCrossTubeStrategy crisisStrategy;

    public Integer getAvgLength() {
        if (isInvestStrategy) {
            return getInvestStrategy().getAvgLength();
        }
        return getCrisisStrategy().getAvgLength();
    }

    public Boolean isTubeAvgDeltaAdvance2() {
        if (isInvestStrategy) {
            return getInvestStrategy().isTubeAvgDeltaAdvance2();
        }
        return getCrisisStrategy().isTubeAvgDeltaAdvance2();
    }

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
    public AInstrumentByFiatCrossStrategy.BuyCriteria getBuyCriteria() {
        if (isInvestStrategy) {
            return getInvestStrategy().getBuyCriteria();
        }
        return getCrisisStrategy().getBuyCriteria();
    }

    @Override
    public AInstrumentByFiatCrossStrategy.SellCriteria getSellCriteria() {
        if (isInvestStrategy) {
            return getInvestStrategy().getSellCriteria();
        }
        return getCrisisStrategy().getSellCriteria();
    }

    public SellLimitCriteria getSellLimitCriteria() {
        if (isInvestStrategy) {
            return getInvestStrategy().getSellLimitCriteria();
        }
        return getCrisisStrategy().getSellLimitCriteria();
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
    public boolean isEnabled() { return false; }
    public boolean isArchive() {return false;}
}
