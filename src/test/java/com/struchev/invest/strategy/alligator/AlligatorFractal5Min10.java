package com.struchev.invest.strategy.alligator;

import com.struchev.invest.expression.Date;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class AlligatorFractal5Min10 extends AlligatorFractal5Min {
    public Float getBuyMinProfitPercent() { return .10f; }
    public Float getBuyMaxProfitPercent() { return 4.f; }
    public Integer getFractalAverageNumber() { return 3; }

    public boolean isEnabled() { return true; }
}
