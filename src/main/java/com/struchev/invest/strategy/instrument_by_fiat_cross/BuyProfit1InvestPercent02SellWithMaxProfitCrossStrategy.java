package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitCrossStrategy extends BuyEma600DeadLineP1Profit1DeadLine2InvestPercent02CrossStrategy {

    private Map FIGIES = Map.of(
/*            "BBG00178PGX3", 6,    // VK
            "BBG005DXJS36", 5 // TCS Group (Tinkoff Bank holder)
            //"BBG000N625H8", 1 // Freedom Holding Corp/NV

            "BBG006L8G4H1", 1,   // Yandex
            "BBG00178PGX3", 6,    // VK ??
            "BBG004S68696", 10,    // Распадская
            "BBG002W2FT69", 10, // АбрауДюрсо
            "BBG00475KKY8", 2 // НОВАТЭК
*/
            /*
            "BBG005F1DK91", 2, // G1
            "BBG002NLDLV8", 2, // VIPS
            //"BBG00W0KZD98", 1,  //LI ?
            "BBG000BLY663", 1, // CROCS
            //"BBG000J3D1Y8", 6, // OraSure Technologies Inc
            "BBG0016XJ8S0", 4, // TAL Education Group
            //"BBG000GRZDV1", 1, // Strategic Education Inc
            "BBG006G2JVL2", 1, // Alibaba
            "BBG003QBJKN0", 4 // Allakos Inc
            //"BBG004NLQHL0", 2 // Fastly Inc*/

    );

    //public Map<String, Integer> getFigies() { return FIGIES; }

    public Integer getTicksMoveUp() { return 5; }
    public Double getMinPercentTubeMoveUp() { return -0.050; }

    public Double getInvestPercentFromFast() { return -1.0; }

    public Boolean isSellWithMaxProfit() { return true; }

    public Boolean isBuyInvestCrossSmaEma2() { return false; }

    public Double getMinPercentSmaSlowestMoveUp() { return -0.10; }

    public Double getMaxSmaFastCrossPercent() { return 0.005; }

    public Integer getDelayPlusBySL() { return null; }

    public Boolean isTubeTopBlur() { return false; }

    //public boolean isEnabled() { return true; }

    //public boolean isArchive() {return true;}
}
