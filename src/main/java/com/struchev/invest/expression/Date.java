package com.struchev.invest.expression;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public interface Date {
    static String formatDateTime(OffsetDateTime date) {
        var dateInZone = date.atZoneSameInstant(ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        return dateInZone.format(formatter);
        //return dateInZone.format(DateTimeFormatter.ISO_LOCAL_DATE) + " " + dateInZone.format(DateTimeFormatter.ISO_LOCAL_TIME);
    }

    static String formatDateTimeWithTimeZone(OffsetDateTime date) {
        var dateInZone = date.atZoneSameInstant(ZoneId.systemDefault());
        return dateInZone.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    static String formatDateTimeToHour(OffsetDateTime date) {
        var dateInZone = date.atZoneSameInstant(ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH");
        return dateInZone.format(formatter);
    }
}
