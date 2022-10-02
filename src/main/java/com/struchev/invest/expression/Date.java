package com.struchev.invest.expression;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public interface Date {
    static String formatDateTime(OffsetDateTime date) {
        var dateInZone = date.atZoneSimilarLocal(ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        return dateInZone.format(formatter);
        //return dateInZone.format(DateTimeFormatter.ISO_LOCAL_DATE) + " " + dateInZone.format(DateTimeFormatter.ISO_LOCAL_TIME);
    }
}
