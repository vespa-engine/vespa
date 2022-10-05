package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandResult;
import com.yahoo.vespa.hosted.node.admin.task.util.process.Terminal;

/**
 * Ensures that wireguard-go is running on the host.
 *
 * @author gjoranv
 */
public class WireguardMaintainer {

    private static final String WIREGUARD_GO = "/path/wireguard-go wg0";
    private static final String WIREGUARD_SOCK_FILE= "/var/run/wireguard/wg0.sock";

    private final Terminal terminal;


    public WireguardMaintainer(Terminal terminal) {
        this.terminal = terminal;
    }


    public void converge(NodeAgentContext context) {

        // TODO?: check if any containers are restarted

        terminal.newCommandLine(context)
                .add("rm", "-f", WIREGUARD_SOCK_FILE)
                .executeSilently();

        // TODO: track exit status? How to handle failure, throw?
        CommandResult result  = terminal.newCommandLine(context)
                .add(WIREGUARD_GO, "wg0")
                .executeSilently();

    }

}
