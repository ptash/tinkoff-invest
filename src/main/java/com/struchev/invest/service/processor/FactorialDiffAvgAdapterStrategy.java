package com.struchev.invest.service.processor;

import com.struchev.invest.strategy.instrument_by_fiat_factorial.AInstrumentByFiatFactorialStrategy;
import org.springframework.stereotype.Component;

@Component
public class FactorialDiffAvgAdapterStrategy extends AInstrumentByFiatFactorialStrategy {

    private AInstrumentByFiatFactorialStrategy strategy;
    private Float priceDiffAvgReal;
    private BuyCriteria buy;
    private SellCriteria sell;

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
        buy.setOverProfitWaitFirstUnderProfitPercent(strategyBuy.getOverProfitWaitFirstUnderProfitPercent() * getPriceDiffAvg());
        buy.setOverProfitSkipWaitFirstOverProfitPercent(strategyBuy.getOverProfitSkipWaitFirstOverProfitPercent() * getPriceDiffAvg());
        return buy;
    }

    public SellCriteria getSellCriteria() {
        if (sell != null) {
            return sell;
        }
        var strategySell = strategy.getSellCriteria();
        sell = strategySell.clone();
        var getPriceDiffAvg = getPriceDiffAvg();
        sell.setTakeProfitPercent(Math.max(strategy.getPriceDiffAvgPercentMin(), strategySell.getTakeProfitPercent() * getPriceDiffAvg));
        sell.setExitProfitLossPercent(Math.max(strategy.getPriceDiffAvgPercentMin(), strategySell.getExitProfitLossPercent() * getPriceDiffAvg));
        sell.setStopLossPercent(Math.max(strategy.getPriceDiffAvgPercentMin(), strategySell.getStopLossPercent() * getPriceDiffAvg));
        sell.setStopLossSoftPercent(Math.max(strategy.getPriceDiffAvgPercentMin(), strategySell.getStopLossSoftPercent() * getPriceDiffAvg));
        sell.setExitLossPercent(Math.max(strategy.getPriceDiffAvgPercentMin(), strategySell.getExitLossPercent() * getPriceDiffAvg));
        return sell;
    }

    public SellLimitCriteria getSellLimitCriteria() {
        var s = strategy.getSellLimitCriteria();
        if (s == null) {
            return null;
        }
        return SellLimitCriteria.builder().exitProfitPercent(s.getExitProfitPercent() * getPriceDiffAvg()).build();
    }
}
