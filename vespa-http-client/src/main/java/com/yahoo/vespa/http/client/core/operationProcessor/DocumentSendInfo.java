// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.operationProcessor;

import com.yahoo.vespa.http.client.Result;
import com.yahoo.vespa.http.client.core.Document;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps an overview of what is sent and what is received for an operation.
 * This class is not thread-safe.
 */
class DocumentSendInfo {

    private final Document document;
    private final Map<Integer, Result.Detail> detailByClusterId = new HashMap<>();
    // This is lazily populated as normal cases does not require retries.
    private Map<Integer, Integer> attemptedRetriesByClusterId = null;
    private final StringBuilder localTrace;

    DocumentSendInfo(Document document, boolean traceThisDoc) {
        this.document = document;
        localTrace = traceThisDoc
                ? new StringBuilder("\n" + document.createTimeMillis() + " Trace starting " + "\n")
                : null;
    }

    boolean addIfNotAlreadyThere(Result.Detail detail, int clusterId) {
        if (detailByClusterId.containsKey(clusterId)) {
            if (localTrace != null) {
                localTrace.append(System.currentTimeMillis() + " Got duplicate detail, ignoring this: "
                                  + detail.toString() + "\n");
            }
            return false;
        }
        if (localTrace != null) {
            localTrace.append(System.currentTimeMillis() + " Got detail: " + detail.toString() + "\n");
        }
        detailByClusterId.put(clusterId, detail);
        return true;
    }

    int detailCount() {
        return detailByClusterId.size();
    }

    public Result createResult() {
        return new Result(document, detailByClusterId.values(), localTrace);
    }

    int incRetries(int clusterId, Result.Detail detail) {
        if (attemptedRetriesByClusterId == null) {
            attemptedRetriesByClusterId = new HashMap<>();
        }
        int retries = 0;
        if (attemptedRetriesByClusterId.containsKey(clusterId)) {
            retries = attemptedRetriesByClusterId.get(clusterId);
        }
        retries++;
        attemptedRetriesByClusterId.put(clusterId, retries);
        if (localTrace != null) {
            localTrace.append(System.currentTimeMillis() + " Asked about retrying for cluster ID "
                    + clusterId + ", number of retries is " + retries + " Detail:\n" + detail.toString());
        }
        return retries;
    }

    Document getDocument() {
        return document;
    }

}
