package com.struchev.invest.strategy;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "invest")
@Data
@Slf4j
public class StrategySettings {
    private List<String> currencies = new ArrayList<>();
    private Map<String, StrategyConfig> strategies = new HashMap<>();

    @Data
    public static class StrategyConfig {
        private Boolean isEnable = false;
        private Boolean isArchive = false;
        private Map<String, Integer> figies;
    }

    public Map<String, Integer> getFigies(String strategy) {
        //log.info("getFigies for {} size={}", strategy, config.size());
        if (!this.strategies.containsKey(strategy) || null == this.strategies.get(strategy).figies) {
            return new HashMap<>();
        }
        return this.strategies.get(strategy).figies;
    }

    public boolean isEnabled(String strategy) {
        log.info("getFigies for {} size={}", strategy, this.strategies.size());
        if (!this.strategies.containsKey(strategy)) {
            return false;
        }
        return this.strategies.get(strategy).isEnable;
    }

    public boolean isArchive(String strategy) {
        if (!this.strategies.containsKey(strategy)) {
            return false;
        }
        return this.strategies.get(strategy).isArchive;
    }
}
