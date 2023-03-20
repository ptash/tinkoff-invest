package com.struchev.invest.entity;

import com.struchev.invest.service.dictionary.InstrumentService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "instrument",
        indexes = {
                @Index(columnList = "figi", unique = true, name = "instrument_figi_ukey"),
        })
public class InstrumentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "figi", nullable = false)
    private String figi;

    @Column(name = "tiket")
    private String tiket;

    @Column(name = "name")
    private String name;

    @Column(name = "type")
    private InstrumentService.Type type;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "moex_code")
    private String moexCode;

    @Column(name = "price_increment", scale = 8, precision = 19)
    private BigDecimal priceIncrement;

    @Column(name = "price_increment_rate_currency")
    private String priceIncrementRateCurrency;

    @Column(name = "price_increment_rate", scale = 8, precision = 19)
    private BigDecimal priceIncrementRate;
}
