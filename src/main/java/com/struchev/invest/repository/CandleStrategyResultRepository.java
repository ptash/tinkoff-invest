package com.struchev.invest.repository;

import com.struchev.invest.entity.CandleStrategyResultEntity;
import com.struchev.invest.entity.OrderDomainEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface CandleStrategyResultRepository extends JpaRepository<CandleStrategyResultEntity, Long> {
    List<CandleStrategyResultEntity> findByFigi(String figi, Sort var1);
    List<CandleStrategyResultEntity> findByStrategy(String strategy, Sort var1);

    List<CandleStrategyResultEntity> findByStrategyAndFigi(String strategy, String figi, Sort var1);

    CandleStrategyResultEntity findByStrategyAndFigiAndDateTimeAndInterval(String strategy, String figi, OffsetDateTime dateTime, String interval);
}
