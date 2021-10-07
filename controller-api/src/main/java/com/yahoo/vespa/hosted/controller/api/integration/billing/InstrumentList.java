// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

import java.util.ArrayList;
import java.util.List;

/**
 * @author olaa
 */
public class InstrumentList {

    private String activeInstrumentId;
    private List<PaymentInstrument> instruments;


    public InstrumentList(List<PaymentInstrument> instruments) {
        this.instruments = instruments;
    }

    public void setActiveInstrumentId(String activeInstrumentId) {
        this.activeInstrumentId = activeInstrumentId;
    }

    public void addInstrument(PaymentInstrument instrument) {
        instruments.add(instrument);
    }

    public void addInstruments(List<PaymentInstrument> instruments) {
        instruments.addAll(instruments);
    }

    public String getActiveInstrumentId() {
        return activeInstrumentId;
    }

    public List<PaymentInstrument> getInstruments() {
        return instruments;
    }
}
