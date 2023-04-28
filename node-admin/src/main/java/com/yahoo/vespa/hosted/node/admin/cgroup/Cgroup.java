// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.cgroup;

import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.container.ContainerId;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Represents a cgroup in the control group v2 hierarchy, see
 * <a href="https://www.kernel.org/doc/html/latest/admin-guide/cgroup-v2.html">Control Group v2</a>.
 *
 * @author hakonhall
 */
public class Cgroup {
    private static final Logger logger = Logger.getLogger(Cgroup.class.getName());

    private static Map<String, Consumer<UnixPath>> cgroupDirectoryCallbacks = new HashMap<>();

    private final Path root;
    private final Path relativePath;

    public static Cgroup root(FileSystem fileSystem) {
        return new Cgroup(fileSystem.getPath("/sys/fs/cgroup"), fileSystem.getPath(""));
    }

    private Cgroup(Path root, Path relativePath) {
        this.root = root.normalize();
        this.relativePath = this.root.relativize(this.root.resolve(relativePath).normalize());
        if (this.relativePath.toString().equals("..") || this.relativePath.toString().startsWith("../")) {
            throw new IllegalArgumentException("Invalid cgroup relative path: " + relativePath);
        }
    }

    /** Whether this cgroup actually exists in the kernel / on the file system. */
    public boolean exists() { return unixPath().resolve("cgroup.controllers").exists(); }

    /** Creates this cgroup if it does not already exist, and return this. */
    public Cgroup create() {
        if (unixPath().createDirectory()) {
            // cgroup automatically creates various files in a newly created cgroup directory. A unit test may simulate
            // this by registering consumers before the test is run.
            Consumer<UnixPath> callback = cgroupDirectoryCallbacks.get(relativePath.toString());
            if (callback != null)
                callback.accept(unixPath());
        }
        return this;
    }

    /** Whether v2 cgroup is enabled on this host. */
    public boolean v2CgroupIsEnabled() { return resolveRoot().exists(); }

    /**
     * Resolve the given path against the path of this cgroup, and return the resulting cgroup.
     * If the given path is absolute, it is resolved against the root of the cgroup hierarchy.
     */
    public Cgroup resolve(String path) {
        Path effectivePath = fileSystem().getPath(path);
        if (effectivePath.isAbsolute()) {
            return new Cgroup(root, fileSystem().getPath("/").relativize(effectivePath));
        } else {
            return new Cgroup(root, relativePath.resolve(path));
        }
    }

    /** Returns the root cgroup, possibly this. */
    public Cgroup resolveRoot() { return isRoot() ? this : new Cgroup(root, fileSystem().getPath("")); }

    /** Returns the cgroup of a system service assuming this is the root, e.g. vespa-host-admin -> system.slice/vespa-host-admin.service. */
    public Cgroup resolveSystemService(String name) { return resolve("system.slice").resolve(serviceNameOf(name)); }

    /** Returns the root cgroup of the given Podman container. */
    public Cgroup resolveContainer(ContainerId containerId) { return resolve("/machine.slice/libpod-" + containerId + ".scope/container"); }

    /** Returns the root cgroup of the container, or otherwise the root cgroup. */
    public Cgroup resolveRoot(Optional<ContainerId> containerId) { return containerId.map(this::resolveContainer).orElseGet(this::resolveRoot); }

    /** Returns the absolute path to this cgroup. */
    public Path path() { return root.resolve(relativePath); }

    /** Returns the UnixPath of {@link #path()}. */
    public UnixPath unixPath() { return new UnixPath(path()); }

    public String read(String filename) {
        return unixPath().resolve(filename).readUtf8File();
    }

    public Optional<String> readIfExists(String filename) {
        return unixPath().resolve(filename).readUtf8FileIfExists().map(String::strip);
    }

    public List<String> readLines(String filename) {
        return unixPath().resolve(filename).readUtf8File().lines().toList();
    }

    public Optional<Integer> readIntIfExists(String filename) {
        return unixPath().resolve(filename).readUtf8FileIfExists().map(String::strip).map(Integer::parseInt);
    }

    public Size readSize(String filename) { return Size.from(read(filename).stripTrailing()); }

    public boolean convergeFileContent(TaskContext context, String filename, String content, boolean apply) {
        UnixPath path = unixPath().resolve(filename);
        String currentContent = path.readUtf8File();
        if (ensureSuffixNewline(currentContent).equals(ensureSuffixNewline(content))) return false;

        if (apply) {
            context.recordSystemModification(logger, "Updating " + path + " from '" + currentContent.stripTrailing() +
                                                     "' to '" + content.stripTrailing() + "'");
            path.writeUtf8File(content);
        }
        return true;
    }

    /** The kernel appears to append a newline if none exist, when writing to files in cgroupfs. */
    private static String ensureSuffixNewline(String content) {
        return content.endsWith("\n") ? content : content + "\n";
    }

    /** Returns an instance representing core interface files (cgroup.* files). */
    public CgroupCore core() { return new CgroupCore(this); }

    /** Returns the CPU controller of this cgroup (cpu.* files). */
    public CpuController cpu() { return new CpuController(this); }

    /** Returns the memory controller of this cgroup (memory.* files). */
    public MemoryController memory() { return new MemoryController(this); }

    /**
     * Wraps {@code command} to ensure it is executed in this cgroup.
     *
     * <p>WARNING: This method must be called only after vespa-cgexec has been installed.</p>
     */
    public String[] wrapCommandForExecutionInCgroup(String... command) {
        String[] fullCommand = new String[3 + command.length];
        fullCommand[0] = Defaults.getDefaults().vespaHome() + "/bin/vespa-cgexec";
        fullCommand[1] = "-g";
        fullCommand[2] = relativePath.toString();
        System.arraycopy(command, 0, fullCommand, 3, command.length);
        return fullCommand;
    }

    public static void unitTesting_atCgroupCreation(String relativePath, Consumer<UnixPath> callback) {
        cgroupDirectoryCallbacks.put(relativePath, callback);
    }

    private boolean isRoot() { return relativePath.toString().isEmpty(); }

    private static String serviceNameOf(String name) {
        return name.indexOf('.') == -1 ? name + ".service" : name;
    }

    private FileSystem fileSystem() { return root.getFileSystem(); }
}
