package com.struchev.invest.repository;

import com.struchev.invest.entity.InstrumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstrumentRepository extends JpaRepository<InstrumentEntity, Long> {
    InstrumentEntity findByFigi(String figi);
}
