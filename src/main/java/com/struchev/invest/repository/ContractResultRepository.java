package com.struchev.invest.repository;

import com.struchev.invest.entity.ContractResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;

public interface ContractResultRepository extends JpaRepository<ContractResultEntity, Long> {
    ContractResultEntity findByFigiAndDateTime(String figi, OffsetDateTime dateTime);
}
