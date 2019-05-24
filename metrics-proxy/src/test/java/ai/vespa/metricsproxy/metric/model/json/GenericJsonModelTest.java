/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.metric.model.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;

import static ai.vespa.metricsproxy.TestUtil.getContents;
import static ai.vespa.metricsproxy.metric.model.json.JacksonUtil.createObjectMapper;
import static org.junit.Assert.assertEquals;

/**
 * @author gjoranv
 */
public class GenericJsonModelTest {
    private static final String TEST_FILE = "generic-sample.json";

    @Test
    public void deserialize_serialize_roundtrip() throws IOException {
        GenericJsonModel jsonModel = genericJsonModelFromTestFile();

        // Do some sanity checking
        assertEquals(2, jsonModel.services.size());
        assertEquals(2, jsonModel.node.metrics.size());
        assertEquals(16.222, jsonModel.node.metrics.get(0).values.get("cpu.util"), 0.01d);

        String expected = getContents(TEST_FILE).trim().replaceAll("\\s+", "");;

        String serialized = jsonModel.serialize();
        String trimmed = serialized.trim().replaceAll("\\s+", "");

        assertEquals(expected, trimmed);
    }

    private GenericJsonModel genericJsonModelFromTestFile() throws IOException {
        ObjectMapper mapper = createObjectMapper();
        return mapper.readValue(getContents(TEST_FILE), GenericJsonModel.class);
    }

}
