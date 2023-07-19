// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.errors;

public abstract class StateRestApiException extends Exception {

    private Integer htmlCode;
    private String htmlStatus;

    public StateRestApiException(String description) {
        super(description);
    }

    public StateRestApiException(String message, Integer htmlCode, String htmlStatus) {
        super(message);
        this.htmlCode = htmlCode;
        this.htmlStatus = htmlStatus != null ? htmlStatus : message;
    }

    public Integer getCode() { return htmlCode; }
    public String getStatus() { return htmlStatus; }

}
