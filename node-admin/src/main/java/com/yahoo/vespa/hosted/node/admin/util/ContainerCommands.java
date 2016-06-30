package com.yahoo.vespa.hosted.node.admin.util;

import com.yahoo.vespa.defaults.Defaults;

/**
 * Commands that can be run inside a Docker container
 */
public class ContainerCommands {
    private static final String NODE_PROGRAM = Defaults.getDefaults().vespaHome() + "bin/vespa-nodectl";

    public static final String[] RESUME_NODE_COMMAND = new String[] {NODE_PROGRAM, "resume"};
    public static final String[] SUSPEND_NODE_COMMAND = new String[] {NODE_PROGRAM, "suspend"};
    public static final String[] GET_VESPA_VERSION_COMMAND = new String[]{NODE_PROGRAM, "vespa-version"};
}
