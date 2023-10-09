// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

/**
 * This reply class is used by operations that perform writes to VDS/search,
 * that is: Put, Remove, Update.
 */
public class WriteDocumentReply extends DocumentAcceptedReply {

    private long highestModificationTimestamp = 0;

    public WriteDocumentReply(int type) {
        super(type);
    }

    /**
     * Returns a unique VDS timestamp so that visiting up to and including that timestamp
     * will return a state including this operation but not any operations sent to the same distributor
     * after it. For PUT/UPDATE/REMOVE operations this timestamp will be the timestamp of the operation.
     *
     * @return Returns the modification timestamp.
     */
    public long getHighestModificationTimestamp() {
        return highestModificationTimestamp;
    }

    /**
     * Sets the modification timestamp.
     */
    public void setHighestModificationTimestamp(long timestamp) {
        this.highestModificationTimestamp = timestamp;
    }

}
