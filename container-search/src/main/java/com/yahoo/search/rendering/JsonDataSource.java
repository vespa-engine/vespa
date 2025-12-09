// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.rendering;

import com.yahoo.data.access.slime.SlimeAdapter;
import com.yahoo.data.disclosure.DataSink;
import com.yahoo.data.disclosure.DataSource;
import com.yahoo.slime.SlimeUtils;

/**
 * Parses JSON to inspector and emits the inspector to the sink.
 *
 * @author johsol
 */
public class JsonDataSource implements DataSource {

    private final String json;

    public JsonDataSource(String json) {
        this.json = json;
    }

    public static JsonDataSource fromJson(String json) {
        return new JsonDataSource(json);
    }

    @Override
    public void emit(DataSink sink) {
        var inspector = new SlimeAdapter(SlimeUtils.jsonToSlime(json).get());
        inspector.emit(sink);
    }
}
