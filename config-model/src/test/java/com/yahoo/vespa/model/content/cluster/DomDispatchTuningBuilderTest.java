// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.DispatchTuning;
import com.yahoo.vespa.model.test.utils.DeployLoggerStub;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class DomDispatchTuningBuilderTest {

    @Test
    void requireThatDefaultsAreNull() throws Exception {
        DispatchTuning dispatch = newTuningDispatch(
                "<content/>");
        assertNull(dispatch.getMaxHitsPerPartition());
    }

    @Test
    void requireThatEmptyTuningIsSafe() throws Exception {
        DispatchTuning dispatch = newTuningDispatch(
                "<content>" +
                        "  <tuning/>" +
                        "</content>");
        assertNull(dispatch.getMaxHitsPerPartition());
    }

    @Test
    void requireThatEmptydispatchIsSafe() throws Exception {
        DispatchTuning dispatch = newTuningDispatch(
                "<content>" +
                        "  <tuning>" +
                        "    <dispatch/>" +
                        "  </tuning>" +
                        "</content>");
        assertNull(dispatch.getMaxHitsPerPartition());
        assertNull(dispatch.getMinActiveDocsCoverage());
        assertNull(dispatch.getDispatchPolicy());
        assertNull(dispatch.getTopkProbability());
    }

    @Test
    void requireThatTuningDispatchCanBeBuilt() throws Exception {
        DispatchTuning dispatch = newTuningDispatch(
                "<content>" +
                        "  <tuning>" +
                        "    <dispatch>" +
                        "      <max-hits-per-partition>69</max-hits-per-partition>" +
                        "      <min-active-docs-coverage>12.5</min-active-docs-coverage>" +
                        "      <top-k-probability>0.999</top-k-probability>" +
                        "    </dispatch>" +
                        "  </tuning>" +
                        "</content>");
        assertEquals(69, dispatch.getMaxHitsPerPartition().intValue());
        assertEquals(12.5, dispatch.getMinActiveDocsCoverage(), 0.0);
        assertEquals(0.999, dispatch.getTopkProbability(), 0.0);
    }

    private static String dispatchPolicy(String policy) {
        return "<content>" +
                "  <tuning>" +
                "    <dispatch>" +
                "      <dispatch-policy>" + policy +"</dispatch-policy>" +
                "    </dispatch>" +
                "  </tuning>" +
                "</content>";
    }

    @Test
    void requireThatTuningDispatchPolicies() throws Exception {
        assertEquals(DispatchTuning.DispatchPolicy.ROUNDROBIN,
                newTuningDispatch(dispatchPolicy("round-robin")).getDispatchPolicy());
        assertEquals(DispatchTuning.DispatchPolicy.ADAPTIVE,
                newTuningDispatch(dispatchPolicy("random")).getDispatchPolicy());
        assertEquals(DispatchTuning.DispatchPolicy.ADAPTIVE,
                newTuningDispatch(dispatchPolicy("adaptive")).getDispatchPolicy());
        assertEquals(DispatchTuning.DispatchPolicy.BEST_OF_RANDOM_2,
                newTuningDispatch(dispatchPolicy("best-of-random-2")).getDispatchPolicy());
        assertEquals(DispatchTuning.DispatchPolicy.LATENCY_AMORTIZED_OVER_REQUESTS,
                newTuningDispatch(dispatchPolicy("latency-amortized-over-requests")).getDispatchPolicy());
        assertEquals(DispatchTuning.DispatchPolicy.LATENCY_AMORTIZED_OVER_TIME,
                newTuningDispatch(dispatchPolicy("latency-amortized-over-time")).getDispatchPolicy());
    }


    private static DispatchTuning newTuningDispatch(String xml) throws Exception {
        return DomTuningDispatchBuilder.build(
                new ModelElement(DocumentBuilderFactory.newInstance()
                                                       .newDocumentBuilder()
                                                       .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                                                       .getDocumentElement()),
                new DeployLoggerStub());
    }
}
