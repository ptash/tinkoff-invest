package com.struchev.invest.repository;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.entity.OrderDomainEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<OrderDomainEntity, Long> {
    List<OrderDomainEntity> findByFigi(String figi, Sort var1);
    List<OrderDomainEntity> findByStrategy(String strategy, Sort var1);
    List<OrderDomainEntity> findByPurchaseDateTimeGreaterThan(OffsetDateTime purchaseDateTime);

    List<OrderDomainEntity> findByStrategyAndFigi(String strategy, String figi, Sort var1);

    //List<OrderDomainEntity> findAllLimit(Pageable pageable);

    //@Query("select o from OrderDomainEntity o where o.purchase_date_time IS NULL or o.sell_date_time IS NULL " +
    //        "ORDER BY o.id")
    //List<OrderDomainEntity> findAllOpen();
}
