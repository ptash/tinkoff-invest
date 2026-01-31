package com.struchev.invest.strategy.alligator;

import com.struchev.invest.entity.CandleDomainEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.function.Function;

@Component
public class AlligatorStrategy5MinRev4L extends AlligatorStrategy5MinRev4 {
    public Integer getReverseLongMinLength() { return 40; }
    public Integer getReverseLongMinAvLength() { return 5; }
    public Double getReverseLongMinAvAdK() { return 0.6; }
    public Integer getReverseSellLongMinMinLength() { return 3; }
    public Integer getLimitPriceDownStepLength() { return 0; }
    //public Function<? super CandleDomainEntity, ? extends BigDecimal> getReverseLongMinKeyExtractor() { return CandleDomainEntity::getLowestPrice; }
    public boolean isEnabled() { return true; }
}
