// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.debug;

import static com.yahoo.search.debug.SearcherUtils.clusterSearchers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;

import com.yahoo.fs4.mplex.Backend;
import com.yahoo.jrt.Int32Array;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.StringArray;
import com.yahoo.jrt.Value;
import com.yahoo.jrt.Values;
import com.yahoo.prelude.cluster.ClusterSearcher;
import com.yahoo.yolean.Exceptions;

/**
 * @author tonytv
 */
public class BackendStatistics implements DebugMethodHandler {
    public JrtMethodSignature getSignature() {
        String returnTypes = "" + (char)Value.STRING_ARRAY + (char)Value.INT32_ARRAY + (char)Value.INT32_ARRAY;
        String parametersTypes = "" + (char)Value.STRING;

        return new JrtMethodSignature(returnTypes, parametersTypes);
    }

    public void invoke(Request request) {
        try {
            Collection<ClusterSearcher> searchers = clusterSearchers(request);
            List<String> backendIdentificators = new ArrayList<>();
            List<Integer> activeConnections = new ArrayList<>();
            List<Integer> totalConnections = new ArrayList<>();

            for (ClusterSearcher searcher : searchers) {
                for (Map.Entry<String,Backend.BackendStatistics> statistics : searcher.getBackendStatistics().entrySet()) {
                    backendIdentificators.add(statistics.getKey());
                    activeConnections.add(statistics.getValue().activeConnections);
                    totalConnections.add(statistics.getValue().totalConnections());
                }
            }
            Values returnValues = request.returnValues();
            returnValues.add(new StringArray(backendIdentificators.toArray(new String[0])));
            addInt32Array(returnValues, activeConnections);
            addInt32Array(returnValues, totalConnections);

        } catch (Exception e) {
            request.setError(1000, Exceptions.toMessageString(e));
        }
    }

    private void addInt32Array(Values returnValues, List<Integer> ints) {
        returnValues.add(new Int32Array(ArrayUtils.toPrimitive(ints.toArray(new Integer[0]))));
    }
}
