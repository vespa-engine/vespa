package com.yahoo.vespa.hosted.node.admin.util;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author valerijf
 */
public class PrefixLogger {
    private String prefix;
    private Logger logger;

    private PrefixLogger(Class clazz, String prefix) {
        this.logger = Logger.getLogger(clazz.getName());
        this.prefix = prefix + ": ";
    }

    public static PrefixLogger getNodeAdminLogger(Class clazz) {
        return new PrefixLogger(clazz, "NodeAdmin");
    }

    public static PrefixLogger getNodeAgentLogger(Class clazz, ContainerName containerName) {
        return new PrefixLogger(clazz, "NodeAgent-" + containerName.asString());
    }

    private void log(Level level, String message, Throwable thrown) {
        logger.log(level, prefix + message, thrown);
    }

    private void log(Level level, String message) {
        logger.log(level, prefix + message);
    }


    public void info(String message) {
        log(LogLevel.INFO, message);
    }

    public void info(String message, Throwable thrown) {
        log(LogLevel.INFO, message, thrown);
    }

    public void error(String message) {
        log(LogLevel.ERROR, message);
    }

    public void error(String message, Throwable thrown) {
        log(LogLevel.ERROR, message, thrown);
    }

    public void warning(String message) {
        log(LogLevel.WARNING, message);
    }

    public void warning(String message, Throwable thrown) {
        log(LogLevel.WARNING, message, thrown);
    }
}
