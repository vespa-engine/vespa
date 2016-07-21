package com.yahoo.vespa.hosted.node.admin.util;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author valerijf
 */
public class PrefixLogger {
    private String prefix;
    private Logger logger;

    public PrefixLogger(String className, String prefix) {
        this.logger = Logger.getLogger(className);
        this.prefix = prefix + " ";
    }

    public void log(Level level, String msg, Throwable thrown) {
        logger.log(level, prefix + msg, thrown);
    }

    public void log(Level level, String msg) {
        logger.log(level, prefix + msg);
    }
}
