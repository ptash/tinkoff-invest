package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyEma600DeadLineP1Profit1DeadLine2InvestPercent02CrossStrategy extends BuyEma600DeadLineP1DeadLine2InvestPercent02DelaySLNullCrossStrategy {
    private Map FIGIES = Map.of(
            //"BBG005F1DK91", 1, // G1
            "BBG0016XJ8S0", 4, // TAL Education Group
            "BBG000GRZDV1", 1, // Strategic Education Inc
            "BBG006G2JVL2", 1, // Alibaba
            "BBG001KS9450", 1, // 2U Inc
            "BBG003QBJKN0", 1, // Allakos Inc
            "BBG004NLQHL0", 1, // Fastly Inc
            "BBG005DXJS36", 1 // TCS Group (Tinkoff Bank holder)
            //"BBG002NLDLV8", 2 // VIPS
    );

    public Map<String, Integer> getFigies() {
        return FIGIES;
    }
    public Boolean isBuyInvestCrossSmaEma2() { return true; }
    public Double getDelayPlusBySLFactor() { return 1.0; }

    public Double getMinPercentTubeMoveUp() { return -0.020; }

    @Override
    public boolean isEnabled() { return true; }
}
