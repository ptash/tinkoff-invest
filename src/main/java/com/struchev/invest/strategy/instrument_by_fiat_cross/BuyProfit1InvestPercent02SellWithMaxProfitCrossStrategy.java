package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitCrossStrategy extends BuyEma600DeadLineP1Profit1DeadLine2InvestPercent02CrossStrategy {

    private Map FIGIES = Map.of(
            //"BBG005F1DK91", 1, // G1
            "BBG000BLY663", 4, // CROCS
            "BBG0016XJ8S0", 4, // TAL Education Group
            //"BBG000GRZDV1", 1, // Strategic Education Inc
            "BBG006G2JVL2", 1, // Alibaba
            "BBG001KS9450", 3, // 2U Inc
            "BBG003QBJKN0", 4, // Allakos Inc
            "BBG004NLQHL0", 2, // Fastly Inc
            "BBG005DXJS36", 1 // TCS Group (Tinkoff Bank holder)
            //"BBG002NLDLV8", 2 // VIPS

    );

    public Map<String, Integer> getFigies() {
        return FIGIES;
    }

    public Integer getTicksMoveUp() { return 5; }
    public Double getMinPercentTubeMoveUp() { return -0.009; }

    public Double getInvestPercentFromFast() { return -1.0; }

    public Boolean isSellWithMaxProfit() { return true; }

    public Boolean isBuyInvestCrossSmaEma2() { return false; }

    public Double getMinPercentSmaSlowestMoveUp() { return -0.10; }

    public Double getMaxSmaFastCrossPercent() { return 0.005; }

    public Integer getDelayPlusBySL() { return null; }

    @Override
    public boolean isEnabled() { return true; }

    public boolean isArchive() {
        return false;
    }
}
