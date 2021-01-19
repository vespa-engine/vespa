// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.yahoo.container.core.AccessLogConfig;

import java.util.logging.Logger;

/**
 * @author Bjorn Borud
 */
class AccessLogHandler {

    public Logger access = Logger.getAnonymousLogger();
    private LogFileHandler logFileHandler;

    public AccessLogHandler(AccessLogConfig.FileHandler config) {
        access.setUseParentHandlers(false);

        LogFormatter lf = new LogFormatter();
        lf.messageOnly(true);
        logFileHandler = new LogFileHandler(config.compressOnRotation(), config.pattern(), config.rotation(), config.symlink(), lf);
        access.addHandler(this.logFileHandler);
    }

    public void shutdown() {
        logFileHandler.close();
        access.removeHandler(logFileHandler);

        if (logFileHandler!=null)
            logFileHandler.shutdown();
    }

    void rotateNow() {
        logFileHandler.rotateNow();
    }
}
