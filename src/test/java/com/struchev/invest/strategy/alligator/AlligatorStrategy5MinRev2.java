package com.struchev.invest.strategy.alligator;

import com.struchev.invest.expression.Date;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class AlligatorStrategy5MinRev2 extends AlligatorStrategy5MinRev {

    public boolean isBuyMaxOnlySmaUp() { return true; }
    public Integer getReverseMaxLength() { return 15; }
    public Integer getReverseUpMinLength() { return 10; }
    public Integer getTrendUpLength() { return 5; }
    public boolean isStopLossForce() { return true; }
    public boolean isStopLossForcePrev() { return true; }
    //public boolean isSmaNearGreenBlueIsTrendDown() { return true; }
    public Double getReverseStopLossK() { return 1.0; }

    public OffsetDateTime getDayTimeEndTrading(OffsetDateTime dateTime) {
        var dateInZone = Date.getDateTimeInZone(dateTime);;
        if (dateInZone.getDayOfWeek().getValue() < 6) {
            return OffsetDateTime.parse("2000-01-01T23:30:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } else {
            return OffsetDateTime.parse("2000-01-01T18:35:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }
    public OffsetDateTime getDayTimeEndBuy(OffsetDateTime dateTime) {
        var dateInZone = Date.getDateTimeInZone(dateTime);;
        if (dateInZone.getDayOfWeek().getValue() < 6) {
            return OffsetDateTime.parse("2000-01-01T22:05:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } else {
            return OffsetDateTime.parse("2000-01-01T17:10:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }

    public boolean isEnabled() { return true; }
}
