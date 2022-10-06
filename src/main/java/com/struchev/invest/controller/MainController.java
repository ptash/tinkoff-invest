package com.struchev.invest.controller;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.struchev.invest.service.report.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
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
        return new ModelAndView("dygraphs", Map.of(
                "strategy", strategy,
                "figi", figi,
                "visibility", getStrategyDygraphsVisibility(strategy, figi, "Strategy")
        ));
    }

    @GetMapping(path = {"/strategy_dygraphs_by_fiat.csv"})
    public void strategyDygraphsByFiatCsv(@RequestParam String strategy, @RequestParam String figi, HttpServletResponse response) {
        outputStrategyDygraphsCsvData(strategy, figi, response, "Strategy");
    }

    @GetMapping(path = {"/strategy_dygraphs_by_fiat_orders.csv"})
    public void strategyDygraphsByFiatOrdersCsv(@RequestParam String strategy, @RequestParam String figi, HttpServletResponse response) {
        outputStrategyDygraphsCsvData(strategy, figi, response, "Offer");
    }

    private String getStrategyDygraphsVisibility(String strategy, String figi, String suffix) {
        File[] fileList = new File(loggingPath).listFiles((dir, name) -> {
            return name.contains(strategy + figi + suffix);
        });
        Arrays.sort(fileList);
        var file = fileList[fileList.length - 1];
        var headerLine = "";
        try {
            BufferedReader fileReader = new BufferedReader(new FileReader(file.getPath()));
            headerLine = fileReader.readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var visibility = "true";
        List<String> items = Lists.newArrayList(Splitter.on("|").split(headerLine));
        for (var i = 1; i < items.size(); i++) {
            if (items.get(i).equals("strategy")) {
                visibility += ",false";
            }
            visibility += ",true";
        }
        return visibility;
    }

    private void outputStrategyDygraphsCsvData(String strategy, String figi, HttpServletResponse response, String suffix) {
        File[] fileList = new File(loggingPath).listFiles((dir, name) -> {
            return name.contains(strategy + figi + suffix);
        });
        Arrays.sort(fileList);
        int max = 2;
        for (int i = Math.max(0, fileList.length - max); i < fileList.length; i++) {
            File file = fileList[i];
            try {
                // get your file as InputStream
                BufferedReader is = new BufferedReader(new FileReader(file));
                if (i != Math.max(0, fileList.length - max)) {
                    is.readLine();
                }
                // copy it to response's OutputStream
                org.apache.commons.io.IOUtils.copy(is, response.getOutputStream(), "UTF-8");
                response.flushBuffer();
            } catch (IOException ex) {
                log.info("Error writing file to output stream: {}", file, ex);
                throw new RuntimeException("IOError writing file to output stream");
            }
        }
        response.setContentType("text/csv");
    }
}
