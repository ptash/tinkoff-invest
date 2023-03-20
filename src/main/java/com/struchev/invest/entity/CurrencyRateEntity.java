package com.struchev.invest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "currency_rate",
        indexes = {
                @Index(columnList = "from_currency, to_currency, date_time", unique = true, name = "currency_rate_date_ukey")
        })
public class CurrencyRateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_currency", nullable = false)
    private String fromCurrency;

    @Column(name = "to_currency", nullable = false)
    private String toCurrency;

    @Column(name = "date_time", nullable = false)
    private OffsetDateTime dateTime;

    @Column(name = "rate", nullable = false, scale = 8, precision = 19)
    private BigDecimal rate;
}
