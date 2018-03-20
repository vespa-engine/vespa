// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.yahoo.container.core.AccessLogConfig;

import static com.yahoo.container.core.AccessLogConfig.FileHandler.RotateScheme.DATE;

import java.util.logging.Logger;

/**
 * @author Bjorn Borud
 */
class AccessLogHandler {

    public Logger access = Logger.getAnonymousLogger();
    private LogFileHandler logFileHandler;

    public AccessLogHandler(AccessLogConfig.FileHandler config) {
        access.setUseParentHandlers(false);

        logFileHandler = new LogFileHandler(config.rotateScheme(), config.compressOnRotation());

        logFileHandler.setFilePattern(config.pattern());
        logFileHandler.setRotationTimes(config.rotation());

        if (config.rotateScheme() == DATE)
            createSymlink(config, logFileHandler);


        LogFormatter lf = new LogFormatter();
        lf.messageOnly(true);
        this.logFileHandler.setFormatter(lf);
        access.addHandler(this.logFileHandler);
    }

    private void createSymlink(AccessLogConfig.FileHandler config, LogFileHandler handler) {
        if (!config.symlink().isEmpty())
            handler.setSymlinkName(config.symlink());
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
