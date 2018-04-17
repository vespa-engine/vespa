package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.task.util.file.Editor;
import com.yahoo.vespa.hosted.node.admin.task.util.file.LineEditor;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class IPTablesEditor {

    private static final PrefixLogger log = PrefixLogger.getNodeAdminLogger(AclMaintainer.class);

    public static boolean editFlushOnError(DockerOperations dockerOperations, ContainerName containerName, IPVersion ipVersion, String table, LineEditor lineEditor) {
        return edit(dockerOperations, containerName, ipVersion, table, lineEditor, true);
    }

    public static boolean editLogOnError(DockerOperations dockerOperations, ContainerName containerName, IPVersion ipVersion, String table, LineEditor lineEditor) {
        return edit(dockerOperations, containerName, ipVersion, table, lineEditor, false);
    }

    private static boolean edit(DockerOperations dockerOperations, ContainerName containerName, IPVersion ipVersion, String table, LineEditor lineEditor, boolean flush) {
        Editor editor = new Editor(ipVersion.iptablesCmd() + " table: " + table,
                listTable(dockerOperations, containerName, ipVersion, table),
                restoreTable(dockerOperations, containerName, ipVersion, table, flush), lineEditor);
        return editor.edit(log::info);
    }

    private static Supplier<List<String>> listTable(DockerOperations dockerOperations, ContainerName containerName, IPVersion ipVersion, String table) {
        return () -> {
            ProcessResult currentRulesResult =
                    dockerOperations.executeCommandInNetworkNamespace(containerName, ipVersion.iptablesCmd(), "-S", "-t", table);
            return Arrays.stream(currentRulesResult.getOutput().split("\n"))
                    .collect(Collectors.toList());
        };
    }

    private static Consumer<List<String>> restoreTable(DockerOperations dockerOperations, ContainerName containerName, IPVersion ipVersion, String table, boolean flush) {
        return list -> {
            File file = null;
            try {
                String rules = String.join("\n", list);
                file = writeTempFile(ipVersion.name(), "*" + table + "\n" + rules + "\nCOMMIT\n");
                dockerOperations.executeCommandInNetworkNamespace(containerName, ipVersion.iptablesRestore(), file.getAbsolutePath());
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
        };
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
}
