package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class BuyEma600CrossStrategy extends AInstrumentByFiatCrossStrategy {
    private static final Map FIGIES = new HashMap<String, Integer>();
    static {
        //FIGIES.put("BBG000LWVHN8", 1); // Дагестанская энергосбытовая компания
        /*
        FIGIES.put("BBG000LWVHN8", 1); // Дагестанская энергосбытовая компания
        FIGIES.put("BBG00178PGX3", 1);    // VK
        FIGIES.put("BBG004TC84Z8", 1); // Трубная Металлургическая Компания
        FIGIES.put("BBG006L8G4H1", 1); // Yandex
        FIGIES.put("BBG004S68696", 1); // Распадская
        FIGIES.put("BBG002W2FT69", 1); // АбрауДюрсо
        FIGIES.put("BBG00475KKY8", 1); // НОВАТЭК
        FIGIES.put("BBG000QFH687", 1); // ТГК-1
        FIGIES.put("BBG004S683W7", 1);   // Аэрофлот
        FIGIES.put("BBG000LNHHJ9", 1); // КАМАЗ*/

        /*
        FIGIES.put("BBG004730RP0", 1); // Газпром
        FIGIES.put("BBG004S683W7", 1);   // Аэрофлот
        FIGIES.put("BBG00178PGX3", 1);    // VK
        FIGIES.put("BBG00475KKY8", 1); // НОВАТЭК
        //FIGIES.put("BBG004S68BH6", 1); // ПИК
        FIGIES.put("BBG000LWVHN8", 1); // Дагестанская энергосбытовая компания
        FIGIES.put("BBG004S68696", 1); // Распадская

        FIGIES.put("BBG004S68758", 1); //Башнефть
        FIGIES.put("BBG004TC84Z8", 1); // Трубная Металлургическая Компания
        FIGIES.put("BBG000LNHHJ9", 1); // КАМАЗ
        FIGIES.put("BBG004RVFCY3", 1); // Магнит
        FIGIES.put("BBG004S68473", 1); // Интер РАО ЕЭС
        FIGIES.put("BBG004S689R0", 1); // ФосАгро
        FIGIES.put("BBG00475K2X9", 1); // РусГидро
        FIGIES.put("BBG000QFH687", 1); // ТГК-1
        FIGIES.put("BBG004S68CP5", 1); // М.видео
        FIGIES.put("BBG002W2FT69", 1); // АбрауДюрсо
        FIGIES.put("BBG006L8G4H1", 1); // Yandex
         */



        //"BBG008NMBXN8", 1, // Robinhood

        /*
        //FIGIES.put("BBG0016XJ8S0", 1); // TAL Education Group
        FIGIES.put("BBG003QBJKN0", 1); // Allakos Inc
        //FIGIES.put("BBG001KS9450", 1); // 2U Inc
        //"BBG000GRZDV1", 1, // Strategic Education Inc
        FIGIES.put("BBG006G2JVL2", 1); // Alibaba
        //IGIES.put("BBG001KS9450", 1); // 2U Inc
        //FIGIES.put("BBG003QBJKN0", 1); // Allakos Inc
        FIGIES.put("BBG004NLQHL0", 1); // Fastly Inc
        FIGIES.put("BBG005DXJS36", 1); // TCS Group (Tinkoff Bank holder)
        FIGIES.put("BBG000BLY663", 1); // CROCS
        */


        /*
        FIGIES.put("BBG000QGWY50", 1); // Bluebird Bio Inc
        FIGIES.put("BBG000BPWXK1", 1); // Newmont Goldcorp Corporation
        FIGIES.put("BBG002NLDLV8", 1); // VIPS
        FIGIES.put("BBG00W0KZD98", 1); // LI
        FIGIES.put("BBG000J3D1Y8", 1); // OraSure Technologies Inc")
        FIGIES.put("BBG004NLQHL0", 1); // Fastly Inc
        FIGIES.put("BBG006G2JVL2", 1); // Alibaba
        FIGIES.put("BBG0016XJ8S0", 1); // TAL Education Group
         */


        //FIGIES.put("BBG00W0KZD98", 1); // LI
        //FIGIES.put("BBG000BLY663", 1); // CROCS
        //FIGIES.put("BBG000BPWXK1", 1); // Newmont Goldcorp Corporation
        FIGIES.put("BBG001KS9450", 1); // 2U Inc
        //FIGIES.put("BBG0016XJ8S0", 1); // TAL Education Group
        //FIGIES.put("BBG002NLDLV8", 1); // VIPS

/*
        FIGIES.put("BBG005F1DK91", 1); // G1
        FIGIES.put("BBG000BLY663", 1); // CROCS
        FIGIES.put("BBG005DXJS36", 1); // TCS Group (Tinkoff Bank holder)
        FIGIES.put("BBG003QBJKN0", 1); // Allakos Inc
        FIGIES.put("BBG001KS9450", 1); // 2U Inc
        FIGIES.put("BBG000GRZDV1", 1); // Strategic Education Inc

 */

/*
        FIGIES.put("BBG005F1DK91", 1); // G1
        FIGIES.put("BBG002NLDLV8", 1); // VIPS
        FIGIES.put("BBG0016XJ8S0", 1); // TAL Education Group
        FIGIES.put("BBG000BLY663", 1); // CROCS
        FIGIES.put("BBG006G2JVL2", 1); // Alibaba
        FIGIES.put("BBG003QBJKN0", 1); // Allakos Inc
        FIGIES.put("BBG000BPWXK1", 1); // Newmont Goldcorp Corporation
        FIGIES.put("BBG005DXJS36", 1); // TCS Group (Tinkoff Bank holder)
        FIGIES.put("BBG000QGWY50", 1); // Bluebird Bio Inc*/

/*
        FIGIES.put("BBG004NLQHL0", 1); // Fastly Inc
        FIGIES.put("BBG000BPWXK1", 1); // Newmont Goldcorp Corporation
        FIGIES.put("BBG000QGWY50", 1); // Bluebird Bio Inc
        FIGIES.put("BBG006G2JVL2", 1); // Alibaba
        //FIGIES.put("BBG005F1DK91", 1); // G1
        FIGIES.put("BBG002NLDLV8", 1); // VIPS
        //FIGIES.put("BBG000BLY663", 1); // CROCS
        FIGIES.put("BBG000J3D1Y8", 1); // OraSure Technologies Inc")
        FIGIES.put("BBG0016XJ8S0", 1); // TAL Education Group
        //FIGIES.put("BBG000BR85F1", 1); // PetroChina
        FIGIES.put("BBG00W0KZD98", 1); //LI*/

    }

    public Map<String, Integer> getFigies() {
        return FIGIES;
    }

    @Override
    public BuyCriteria getBuyCriteria() {
        return BuyCriteria.builder().lessThenPercentile(5).build();
    }

    public Integer getSmaSlowestLength() {
        return 600;
    }

    public Integer getSmaSlowLength() {
        return 150;
    }

    public Integer getSmaFastLength() {
        return 60;
    }

    public Integer getEmaFastLength() {
        return 60;
    }

    @Override
    public SellCriteria getSellCriteria() {
        return SellCriteria.builder().takeProfitPercent(2f).stopLossPercent(3f).build();
    }

    @Override
    public boolean isEnabled() { return false; }
}
