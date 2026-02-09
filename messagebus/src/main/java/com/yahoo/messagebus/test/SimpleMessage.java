// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.test;

import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MetadataExtractor;
import com.yahoo.messagebus.MetadataInjector;
import com.yahoo.text.Utf8String;

/**
 * @author havardpe
 */
public class SimpleMessage extends Message {

    private String value;

    private String fooMeta;
    private String barMeta;

    public SimpleMessage(String value) {
        this.value = value;
    }

    @Override
    public int getType() {
        return SimpleProtocol.MESSAGE;
    }

    @Override
    public Utf8String getProtocol() {
        return SimpleProtocol.NAME;
    }

    @Override
    public int getApproxSize() {
        return value.length();
    }

    public boolean hasMetadata() {
        return fooMeta != null || barMeta != null;
    }

    @Override
    public void injectMetadata(MetadataInjector injector) {
        if (fooMeta != null) {
            injector.injectKeyValue("foo", fooMeta);
        }
        if (barMeta != null) {
            injector.injectKeyValue("bar", barMeta);
        }
    }

    @Override
    public void extractMetadata(MetadataExtractor extractor) {
        extractor.extractValue("foo").ifPresent(v -> fooMeta = v);
        extractor.extractValue("bar").ifPresent(v -> barMeta = v);
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getFooMeta() {
        return fooMeta;
    }

    public void setFooMeta(String fooMeta) {
        this.fooMeta = fooMeta;
    }

    public String getBarMeta() {
        return barMeta;
    }

    public void setBarMeta(String barMeta) {
        this.barMeta = barMeta;
    }
}
