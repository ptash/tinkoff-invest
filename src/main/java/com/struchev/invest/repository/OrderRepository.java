package com.struchev.invest.repository;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.entity.OrderDomainEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<OrderDomainEntity, Long> {
    List<OrderDomainEntity> findByFigi(String figi, Sort var1);
    List<OrderDomainEntity> findByStrategy(String strategy, Sort var1);

    List<OrderDomainEntity> findByStrategyAndFigi(String strategy, String figi, Sort var1);
}
