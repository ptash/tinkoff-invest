package com.struchev.invest.service.processor;

import com.struchev.invest.strategy.instrument_by_fiat_factorial.AInstrumentByFiatFactorialStrategy;
import org.springframework.stereotype.Component;

@Component
public class FactorialDiffAvgAdapterStrategy extends AInstrumentByFiatFactorialStrategy {

    private AInstrumentByFiatFactorialStrategy strategy;
    private Float priceDiffAvgReal;
    private BuyCriteria buy;

    public void setStrategy(AInstrumentByFiatFactorialStrategy strategy) {
        this.strategy = strategy;
    }

    public void setPriceDiffAvgReal(Float priceDiffAvgReal) {
        this.priceDiffAvgReal = priceDiffAvgReal;
    }

    public Float getPriceDiffAvg() {
        return priceDiffAvgReal / this.strategy.getPriceDiffAvg();
    }

    public BuyCriteria getBuyCriteria() {
        if (buy != null) {
            return buy;
        }
        var strategyBuy = strategy.getBuyCriteria();
        buy = strategyBuy.clone();
        buy.setProfitPercentFromBuyMinPrice(strategyBuy.getProfitPercentFromBuyMinPrice() * getPriceDiffAvg());
        buy.setProfitPercentFromBuyMinPrice(strategyBuy.getProfitPercentFromBuyMinPrice() * getPriceDiffAvg());
        buy.setProfitPercentFromBuyMaxPrice(strategyBuy.getProfitPercentFromBuyMaxPrice() * getPriceDiffAvg());
        buy.setProfitPercentFromBuyMinPriceProfit(strategyBuy.getProfitPercentFromBuyMinPriceProfit() * getPriceDiffAvg());
        buy.setProfitPercentFromBuyMaxPriceProfit(strategyBuy.getProfitPercentFromBuyMaxPriceProfit() * getPriceDiffAvg());
        buy.setIsAllUnderLoss(true);
        buy.setIsAllOverProfit(true);
        buy.setIsOverProfitWaitFirstUnderProfit(true);
        buy.setOverProfitWaitFirstUnderProfitPercent(strategyBuy.getOverProfitWaitFirstUnderProfitPercent() * getPriceDiffAvg());
        buy.setOverProfitSkipWaitFirstOverProfitPercent(strategyBuy.getOverProfitSkipWaitFirstOverProfitPercent() * getPriceDiffAvg());
        buy.setOverProfitSkipIfUnderLossPrev(3);

        buy.setUnderLostWaitCandleEndInMinutes(5);
        return buy;
    }
}
