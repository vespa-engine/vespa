package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class to sync rules for a given iptables table in a container.
 *
 * @author smorgrav
 */
public class IPTablesRestore {

    private static final PrefixLogger log = PrefixLogger.getNodeAdminLogger(AclMaintainer.class);

    public static void syncTableFlushOnError(DockerOperations dockerOperations, ContainerName containerName, IPVersion ipVersion, String table, String rules) {
        syncTable(dockerOperations, containerName, ipVersion, table, rules, true);
    }

    public static void syncTableLogOnError(DockerOperations dockerOperations, ContainerName containerName, IPVersion ipVersion, String table, String rules) {
        syncTable(dockerOperations, containerName, ipVersion, table, rules, false);
    }

    private static void syncTable(DockerOperations dockerOperations, ContainerName containerName, IPVersion ipVersion, String table, String rules, boolean flush) {
        File file = null;
        try {
            // Get current rules for table
            ProcessResult currentRulesResult =
                    dockerOperations.executeCommandInNetworkNamespace(containerName, ipVersion.iptablesCmd(), "-S", "-t", table);
            String currentRules = currentRulesResult.getOutput();

            // Compare and apply wanted if different
            if (!equalsWhenIgnoreSpaceAndCase(rules, currentRules)) {
                log.info(ipVersion.iptablesCmd() + " table: " + table + " differs. Wanted:\n" + rules + "\nGot\n" + currentRules);
                file = writeTempFile(ipVersion.name(), "*" + table + "\n" + rules + "\nCOMMIT\n");
                dockerOperations.executeCommandInNetworkNamespace(containerName, ipVersion.iptablesRestore(), file.getAbsolutePath());
            }
        } catch (Exception e) {
            if (flush) {
                log.error("Exception occurred while syncing iptable " + table + " for " + containerName.asString() + ", attempting rollback", e);
                try {
                    dockerOperations.executeCommandInNetworkNamespace(containerName, ipVersion.iptablesCmd(), "-F", "-t", table);
                } catch (Exception ne) {
                    log.error("Rollback of table " + table + " for " + containerName.asString() + " failed, giving up", ne);
                }
            } else {
                log.warning("Unable to sync iptables for " + table, e);
            }
        } finally {
            if (file != null) {
                file.delete();
            }
        }
    }

    private static File writeTempFile(String postfix, String content) {
        try {
            Path path = Files.createTempFile("iptables-restore", "." + postfix);
            File file = path.toFile();
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
            file.deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new RuntimeException("Unable to write restore file for iptables.", e);
        }
    }

    /**
     * to be agnostic to potential variances in output (and simplify test cases)
     */
    private static boolean equalsWhenIgnoreSpaceAndCase(String a, String b) {
        return a.trim().replaceAll("\\s+", " ").equalsIgnoreCase(b.trim().replaceAll("\\s+", " "));
    }
}
