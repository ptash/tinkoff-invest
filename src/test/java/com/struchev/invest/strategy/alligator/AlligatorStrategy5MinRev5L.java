package com.struchev.invest.strategy.alligator;

import com.struchev.invest.entity.CandleDomainEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.function.Function;

@Component
public class AlligatorStrategy5MinRev5L extends AlligatorStrategy5MinRev5 {
    public Integer getReverseLongMinLength() { return 35; }
    public Integer getReverseLongMinAvLength() { return 5; }
    public Integer getReverseSellLongMinMinLength() { return 3; }
    public Integer getReverseBuyLongMinMinLength() { return 3; }
    public Integer getReverseBuyMinMinLength() { return 3; }
    public Integer getLimitPriceDownStepLength() { return 0; }
    public Boolean isDownToMinProfitPercentStopLossByPercent() {return true; }
    public Integer getTrendUpLength() { return 10; }
    public Integer getUpLimitPriceToAvOpenCloseLength() { return 20; }
    public Double getUpLimitPriceToAvOpenCloseK() { return 2.; }
    //public Function<? super CandleDomainEntity, ? extends BigDecimal> getReverseLongMinKeyExtractor() { return CandleDomainEntity::getLowestPrice; }
    public boolean isEnabled() { return true; }
}
