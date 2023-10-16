package com.struchev.invest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "candle_strategy_result",
        indexes = {
                @Index(columnList = "strategy"),
                @Index(columnList = "figi"),
                @Index(columnList = "date_time"),
                @Index(columnList = "interval"),
                @Index(columnList = "strategy, figi, date_time, interval", unique = true)
        })
public class CandleStrategyResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strategy", nullable = false)
    private String strategy;

    @Column(name = "figi", nullable = false)
    private String figi;

    @Column(name = "date_time", nullable = false)
    private OffsetDateTime dateTime;

    @Column(name = "interval", nullable = false)
    private String interval;

    @Type(type = "jsonb")
    @Column(name = "details", columnDefinition = "jsonb")
    private OrderDetails details;
    public OrderDetails getDetails() {
        if (details == null) {
            details = OrderDetails.builder().build();
        }
        return details;
    }
}
