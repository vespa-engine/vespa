// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.logging;

abstract class AbstractSpoolingLogger extends AbstractThreadedLogger {

    @Override
    protected void dequeue(LoggerEntry entry) {
        // Todo: add to spooler etc
    }

}
