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
@Table(name = "contract_result",
        indexes = {
                @Index(columnList = "figi, date_time", unique = true, name = "contract_result_date_ukey")
        })
public class ContractResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "figi", nullable = false)
    private String figi;

    @Column(name = "date_time", nullable = false)
    private OffsetDateTime dateTime;

    @Column(name = "settlement_price", nullable = false, scale = 8, precision = 19)
    private BigDecimal settlementPrice;
}
