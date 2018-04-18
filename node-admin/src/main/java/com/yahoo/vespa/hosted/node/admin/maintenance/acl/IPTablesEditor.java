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

/**
 * Edit the iptables for docker containers.
 */
public class IPTablesEditor {

    private static final PrefixLogger log = PrefixLogger.getNodeAdminLogger(AclMaintainer.class);

    private final DockerOperations dockerOperations;
    private final ContainerName containerName;
    private final Consumer<String> testInterceptor;

    public IPTablesEditor(DockerOperations dockerOperations, ContainerName containerName) {
        this(dockerOperations, containerName, (result) -> {});
    }

    IPTablesEditor(DockerOperations dockerOperations, ContainerName containerName, Consumer<String> testInterceptor) {
        this.dockerOperations = dockerOperations;
        this.containerName = containerName;
        this.testInterceptor = testInterceptor;
    }

    public static boolean editFlushOnError(DockerOperations dockerOperations, ContainerName containerName, IPVersion ipVersion, String table, LineEditor lineEditor) {
        return new IPTablesEditor(dockerOperations, containerName).edit(table, ipVersion, lineEditor, true);
    }

    public static boolean editLogOnError(DockerOperations dockerOperations, ContainerName containerName, IPVersion ipVersion, String table, LineEditor lineEditor) {
        return new IPTablesEditor(dockerOperations, containerName).edit(table, ipVersion, lineEditor, false);
    }

    public boolean edit(String table, IPVersion ipVersion, LineEditor lineEditor, boolean flush) {
        String editId = ipVersion.iptablesCmd() + "-" + table;
        Editor editor = new Editor(editId, listTable(table, ipVersion), restoreTable(table, ipVersion, flush), lineEditor);
        return editor.edit(log::info);
    }

    private Supplier<List<String>> listTable(String table, IPVersion ipVersion) {
        return () -> {
            ProcessResult currentRulesResult =
                    dockerOperations.executeCommandInNetworkNamespace(containerName, ipVersion.iptablesCmd(), "-S", "-t", table);
            return Arrays.stream(currentRulesResult.getOutput().split("\n"))
                    .collect(Collectors.toList());
        };
    }

    private Consumer<List<String>> restoreTable(String table, IPVersion ipVersion, boolean flush) {
        return list -> {
            File file = null;
            try {
                String rules = String.join("\n", list);
                String fileContent = "*" + table + "\n" + rules + "\nCOMMIT\n";
                file = writeTempFile(table, fileContent);
                dockerOperations.executeCommandInNetworkNamespace(containerName, ipVersion.iptablesRestore(), file.getAbsolutePath());
                testInterceptor.accept(fileContent);
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

    private File writeTempFile(String table, String content) {
        try {
            Path path = Files.createTempFile("iptables-restore", "." + table);
            File file = path.toFile();
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
            file.deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new RuntimeException("Unable to write restore file for iptables.", e);
        }
    }
}
