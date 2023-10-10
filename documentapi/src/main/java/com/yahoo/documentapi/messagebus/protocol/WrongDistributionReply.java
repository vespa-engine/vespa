// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

/**
 * @author Simon Thoresen Hult
 */
public class WrongDistributionReply extends DocumentReply {

    private String systemState;

    public WrongDistributionReply() {
        super(DocumentProtocol.REPLY_WRONGDISTRIBUTION);
    }

    public WrongDistributionReply(String systemState) {
        super(DocumentProtocol.REPLY_WRONGDISTRIBUTION);
        this.systemState = systemState;
    }

    public String getSystemState() {
        return systemState;
    }

    public void setSystemState(String state) {
        systemState = state;
    }
}
