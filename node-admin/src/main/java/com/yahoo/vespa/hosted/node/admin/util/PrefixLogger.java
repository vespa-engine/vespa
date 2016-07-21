package com.yahoo.vespa.hosted.node.admin.util;

import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author valerijf
 */
public class PrefixLogger {
    private String prefix;
    private Logger logger;

    private PrefixLogger(String className, String prefix) {
        this.logger = Logger.getLogger(className);
        this.prefix = prefix + ": ";
    }

    public static PrefixLogger getNodeAdminLogger(String className) {
        return new PrefixLogger(className, "NodeAdmin");
    }

    public static PrefixLogger getNodeAgentLogger(String className, ContainerName containerName) {
        return new PrefixLogger(className, "NodeAgent-" + containerName.asString());
    }

    public void log(Level level, String msg, Throwable thrown) {
        logger.log(level, prefix + msg, thrown);
    }

    public void log(Level level, String msg) {
        logger.log(level, prefix + msg);
    }
}
