package com.struchev.invest.strategy.alligator;

import com.struchev.invest.expression.Date;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class AlligatorFractal5MinR extends AlligatorFractal5Min {
    public boolean isRev() { return true; }
    public boolean isEnabled() { return true; }
}
