// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.subscription.ConfigInstanceUtil;
import com.yahoo.slime.JsonFormat;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
/**
 * @author Ulf Lilleengen
 */
public class GenericConfigBuilderTest {

    @Test
    public void require_that_builder_can_be_overridden() throws IOException {
        ConfigPayloadBuilder ba = new ConfigPayloadBuilder();
        ba.setField("foo", "bar");
        ConfigPayloadBuilder bb = new ConfigPayloadBuilder();
        bb.setField("foo", "baz");
        ConfigPayloadBuilder bc = new ConfigPayloadBuilder();
        bc.setField("foo", "bim");
        GenericConfig.GenericConfigBuilder a = new GenericConfig.GenericConfigBuilder(null, ba);
        GenericConfig.GenericConfigBuilder b = new GenericConfig.GenericConfigBuilder(null, bb);
        GenericConfig.GenericConfigBuilder c = new GenericConfig.GenericConfigBuilder(null, bc);
        assertThat(getString(a), is("{\"foo\":\"bar\"}"));
        assertThat(getString(b), is("{\"foo\":\"baz\"}"));
        assertThat(getString(c), is("{\"foo\":\"bim\"}"));
        ConfigInstanceUtil.setValues(a, b);
        assertThat(getString(a), is("{\"foo\":\"baz\"}"));
        assertThat(getString(b), is("{\"foo\":\"baz\"}"));
        assertThat(getString(c), is("{\"foo\":\"bim\"}"));
        ConfigInstanceUtil.setValues(c, a);
        assertThat(getString(a), is("{\"foo\":\"baz\"}"));
        assertThat(getString(b), is("{\"foo\":\"baz\"}"));
        assertThat(getString(c), is("{\"foo\":\"baz\"}"));
    }

    private String getString(GenericConfig.GenericConfigBuilder builder) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        builder.getPayload().serialize(baos, new JsonFormat(true));
        return baos.toString();
    }
}
