// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.dispatch.rpc;

import com.yahoo.document.GlobalId;
import com.yahoo.document.idstring.IdString;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.search.Query;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * @author ollivir
 */
public class ProtobufSerializationTest {
    @Test
    public void testDocsumSerialization() throws IOException {
        Query q = new Query("search/?query=test&hits=10&offset=3");
        var builder = ProtobufSerialization.createDocsumRequestBuilder(q, "server", "summary", true);
        builder.setTimeout(0);
        var hit = new FastHit();
        hit.setGlobalId(new GlobalId(IdString.createIdString("id:ns:type::id")));
        var bytes = ProtobufSerialization.serializeDocsumRequest(builder, Collections.singletonList(hit));

        assertThat(bytes.length, equalTo(41));
    }
}
