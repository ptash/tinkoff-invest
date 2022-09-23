package com.struchev.invest.controller;

import com.struchev.invest.service.report.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

@Controller
@Slf4j
@RequiredArgsConstructor
public class MainController {
    private final ReportService reportService;

    @Value("${logging.file.path}")
    private String loggingPath;

    @GetMapping(path = {"/", "/strategies"})
    public ModelAndView strategies() {
        var report = reportService.buildReportStrategiesInfo();
        return new ModelAndView("report_strategies", Map.of("reportStrategiesInfo", report));
    }

    @GetMapping(path = {"/orders"})
    public ModelAndView orders() {
        var orders = reportService.getOrdersSortByIdDesc();
        return new ModelAndView("report_orders", Map.of("orders", orders));
    }

    @GetMapping(path = {"/instrument_by_instrument"})
    public ModelAndView instrumentByInstrument() {
        var report = reportService.buildReportInstrumentByInstrument();
        return new ModelAndView("report_instrument_by_instrument", Map.of("reportInstrumentByInstrument", report));
    }

    @GetMapping(path = {"/instrument_by_fiat"})
    public ModelAndView instrumentByFiat() {
        var report = reportService.buildReportInstrumentByFiat();
        return new ModelAndView("report_instrument_by_fiat", Map.of("reportInstrumentByFiat", report));
    }

        @GetMapping(path = {"/strategy_dygraphs_by_fiat"})
    public ModelAndView strategyDygraphsByFiat(@RequestParam String strategy, @RequestParam String figi) {
        return new ModelAndView("dygraphs", Map.of("strategy", strategy, "figi", figi));
    }

    @GetMapping(path = {"/strategy_dygraphs_by_fiat.csv"})
    public void strategyDygraphsByFiatCsv(@RequestParam String strategy, @RequestParam String figi, HttpServletResponse response) {
        outputStrategyDygraphsCsvData(strategy, figi, response, "Strategy");
    }

    @GetMapping(path = {"/strategy_dygraphs_by_fiat_orders.csv"})
    public void strategyDygraphsByFiatOrdersCsv(@RequestParam String strategy, @RequestParam String figi, HttpServletResponse response) {
        outputStrategyDygraphsCsvData(strategy, figi, response, "Offer");
    }

    private void outputStrategyDygraphsCsvData(String strategy, String figi, HttpServletResponse response, String suffix) {
        File[] fileList = new File(loggingPath).listFiles((dir, name) -> {
            return name.contains(strategy + figi + suffix);
        });
        Arrays.sort(fileList);
        int max = 5;
        for (int i = Math.max(0, fileList.length - max); i < fileList.length; i++) {
            File file = fileList[i];
            try {
                // get your file as InputStream
                InputStream is = new FileInputStream(file);
                // copy it to response's OutputStream
                org.apache.commons.io.IOUtils.copy(is, response.getOutputStream());
                response.flushBuffer();
            } catch (IOException ex) {
                log.info("Error writing file to output stream: {}", file, ex);
                throw new RuntimeException("IOError writing file to output stream");
            }
        }
        response.setContentType("text/csv");
    }
}
