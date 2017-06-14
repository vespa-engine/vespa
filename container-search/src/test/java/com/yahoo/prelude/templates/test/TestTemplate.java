// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.templates.test;

import java.io.IOException;
import java.io.Writer;

import com.yahoo.prelude.templates.Context;
import com.yahoo.prelude.templates.UserTemplate;

/**
 * Test basic UserTemplate functionality of detecting
 * overridden group rendering methods.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
@SuppressWarnings("rawtypes")
public class TestTemplate extends UserTemplate {

    public TestTemplate(String name, String mimeType, String encoding) {
        super(name, mimeType, encoding);
    }

    @Override
    public void error(Context context, Writer writer) throws IOException {
        // NOP
    }

    @Override
    public void footer(Context context, Writer writer) throws IOException {
        // NOP
    }

    @Override
    public void header(Context context, Writer writer) throws IOException {
        // NOP
    }

    @Override
    public void hit(Context context, Writer writer) throws IOException {
        // NOP
    }

    @Override
    public void hitFooter(Context context, Writer writer) throws IOException {
        // NOP
    }

    @Override
    public void noHits(Context context, Writer writer) throws IOException {
        // NOP
    }

}
