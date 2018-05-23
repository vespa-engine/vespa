// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.logging;

/**
 * Hollow compatibility class for com.yahoo.container.logging.AccessLogEntry.
 *
 * @author Steinar Knutsen
 * @deprecated do not use
 */
@Deprecated // TODO: Remove on Vespa 7
public class AccessLogEntry extends com.yahoo.container.logging.AccessLogEntry {

    public AccessLogEntry() {
        super();
    }

}
