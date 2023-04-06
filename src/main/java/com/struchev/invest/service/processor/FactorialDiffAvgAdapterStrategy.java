package com.struchev.invest.service.processor;

import com.struchev.invest.strategy.instrument_by_fiat_factorial.AInstrumentByFiatFactorialStrategy;
import org.springframework.stereotype.Component;

@Component
public class FactorialDiffAvgAdapterStrategy extends AInstrumentByFiatFactorialStrategy {

    private AInstrumentByFiatFactorialStrategy strategy;
    private Float priceDiffAvgReal;
    private BuyCriteria buy;
    private SellCriteria sell;

    private SellLimitCriteria sellLimitOrig;

    public void setStrategy(AInstrumentByFiatFactorialStrategy strategy) {
        this.strategy = strategy;
    }

    public void setPriceDiffAvgReal(Float priceDiffAvgReal) {
        this.priceDiffAvgReal = priceDiffAvgReal;
        if (null == this.sellLimitOrig) {
            this.sellLimitOrig = this.strategy.getSellLimitCriteriaOrig();
        }
        if (null != this.sellLimitOrig) {
            this.strategy.setSellLimitCriteria(SellLimitCriteria.builder().exitProfitPercent(this.sellLimitOrig.getExitProfitPercent() * getPriceDiffAvg()).build());
        }
    }

    public Float getPriceDiffAvgReal()
    {
        return priceDiffAvgReal;
    }

    public Float getPriceDiffAvg() {
        return priceDiffAvgReal / this.strategy.getPriceDiffAvg();
    }

    public BuyCriteria getBuyCriteria() {
        if (buy != null) {
            return buy;
        }
        if (priceDiffAvgReal == null) {
            return strategy.getBuyCriteria();
        }
        var strategyBuy = strategy.getBuyCriteria();
        buy = strategyBuy.clone();
        if (null != strategyBuy.getProfitPercentFromBuyMinPrice()) {
            buy.setProfitPercentFromBuyMinPrice(strategyBuy.getProfitPercentFromBuyMinPrice() * getPriceDiffAvg());
        }
        if (null != strategyBuy.getProfitPercentFromBuyMaxPrice()) {
            buy.setProfitPercentFromBuyMaxPrice(strategyBuy.getProfitPercentFromBuyMaxPrice() * getPriceDiffAvg());
        }
        if (null != strategyBuy.getProfitPercentFromBuyMinPriceProfit()) {
            buy.setProfitPercentFromBuyMinPriceProfit(strategyBuy.getProfitPercentFromBuyMinPriceProfit() * getPriceDiffAvg());
        }
        if (null != strategyBuy.getProfitPercentFromBuyMaxPriceProfit()) {
            buy.setProfitPercentFromBuyMaxPriceProfit(strategyBuy.getProfitPercentFromBuyMaxPriceProfit() * getPriceDiffAvg());
        }
        if (null != strategyBuy.getOverProfitWaitFirstUnderProfitPercent()) {
            buy.setOverProfitWaitFirstUnderProfitPercent(strategyBuy.getOverProfitWaitFirstUnderProfitPercent() * getPriceDiffAvg());
        }
        if (null != strategyBuy.getOverProfitSkipWaitFirstOverProfitPercent()) {
            buy.setOverProfitSkipWaitFirstOverProfitPercent(strategyBuy.getOverProfitSkipWaitFirstOverProfitPercent() * getPriceDiffAvg());
        }
        if (null != strategyBuy.getNotLossSellPercentDiff()) {
            buy.setNotLossSellPercentDiff(strategyBuy.getNotLossSellPercentDiff() * getPriceDiffAvg());
        }
        if (strategyBuy.getCandleIntervalMinPercent() != null) {
            buy.setCandleIntervalMinPercent(strategyBuy.getCandleIntervalMinPercent() * getPriceDiffAvg());
        }
        return buy;
    }

    public SellCriteria getSellCriteria() {
        if (sell != null) {
            return sell;
        }
        if (priceDiffAvgReal == null) {
            return strategy.getSellCriteria();
        }
        var strategySell = strategy.getSellCriteria();
        sell = strategySell.clone();
        var getPriceDiffAvg = getPriceDiffAvg();
        if (strategySell.getExitLossPercent() != null) {
            sell.setExitLossPercent(Math.max(strategy.getPriceDiffAvgPercentMin(), strategySell.getExitLossPercent() * getPriceDiffAvg));
        }
        if (strategySell.getCandleIntervalMinPercent() != null) {
            sell.setCandleIntervalMinPercent(strategySell.getCandleIntervalMinPercent() * getPriceDiffAvg());
        }
        if (strategySell.getProfitPercentFromSellMinPrice() != null) {
            sell.setProfitPercentFromSellMinPrice(strategySell.getProfitPercentFromSellMinPrice() * getPriceDiffAvg());
        }
        /*
        sell.setStopLossSoftPercent(Math.max(strategy.getPriceDiffAvgPercentMin(), strategySell.getStopLossSoftPercent() * getPriceDiffAvg));
        sell.setExitLossPercent(Math.max(strategy.getPriceDiffAvgPercentMin(), strategySell.getExitLossPercent() * getPriceDiffAvg));
        sell.setTakeProfitPercent(Math.max(strategy.getPriceDiffAvgPercentMin(), strategySell.getTakeProfitPercent() * getPriceDiffAvg));
        if (strategySell.getExitProfitPercent() != null) {
            sell.setExitProfitPercent(Math.max(strategy.getPriceDiffAvgPercentMin(), strategySell.getExitProfitPercent() * getPriceDiffAvg));
        }
        if (strategySell.getExitProfitInPercentMax() != null) {
            sell.setExitProfitInPercentMax(Math.max(strategy.getPriceDiffAvgPercentMin(), strategySell.getExitProfitInPercentMax() * getPriceDiffAvg));
        }
        if (strategySell.getExitProfitInPercentMin() != null) {
            sell.setExitProfitInPercentMin(Math.max(strategy.getPriceDiffAvgPercentMin(), strategySell.getExitProfitInPercentMin() * getPriceDiffAvg));
        }
        if (strategySell.getExitProfitInPercentMaxForLoss() != null) {
            sell.setExitProfitInPercentMaxForLoss(Math.max(strategy.getPriceDiffAvgPercentMin(), strategySell.getExitProfitInPercentMaxForLoss() * getPriceDiffAvg));
        }
        if (strategySell.getTakeProfitPercentForLoss() != null) {
            sell.setTakeProfitPercentForLoss(Math.max(strategy.getPriceDiffAvgPercentMin(), strategySell.getTakeProfitPercentForLoss() * getPriceDiffAvg));
        }
        if (strategySell.getExitProfitInPercentMaxForLoss2() != null) {
            sell.setExitProfitInPercentMaxForLoss2(Math.max(strategy.getPriceDiffAvgPercentMin(), strategySell.getExitProfitInPercentMaxForLoss2() * getPriceDiffAvg));
        }
        sell.setExitProfitLossPercent(Math.max(strategy.getPriceDiffAvgPercentMin(), strategySell.getExitProfitLossPercent() * getPriceDiffAvg));
        sell.setStopLossPercent(Math.max(strategy.getPriceDiffAvgPercentMin(), strategySell.getStopLossPercent() * getPriceDiffAvg));
         */
        return sell;
    }
}
