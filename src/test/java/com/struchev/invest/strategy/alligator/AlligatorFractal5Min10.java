package com.struchev.invest.strategy.alligator;

import com.struchev.invest.expression.Date;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class AlligatorFractal5Min10 extends AlligatorFractal5Min {
    public Float getBuyMinProfitPercent() { return .10f; }
    public Float getBuyMaxProfitPercent() { return 4.f; }

    public boolean isBuyMaxOnlySmaUp() {
        if (isRev()) {
            return false;
        }
        return true;
    }

    public boolean isBuyMaxOnlySmaDown() {
        if (isRev()) {
            return true;
        }
        return false;
    }

    public boolean isStopLossOnlyByLimit() {
        if (isRev()) {
            return true;
        }
        return true;
    }

    public boolean isPriceWantedAv() {
        if (isRev()) {
            return false;
        }
        return true;
    }

    public Double getBuyOnDownProfitPercentK() { return 0.4; }

    public Integer getTrendUpLength() { return 15; }
    public Integer getTrendUpLengthOnSell() { return 1; }
    //public boolean isStopLossByLimitAndAllMinLines() { return true; }
    //public boolean isStopLossOnDownByLimitAndAllMinLines() { return true; }

    public boolean isEnabled() { return true; }
}
