// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.cgroup;

import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.node.admin.container.ContainerId;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;

import java.nio.file.FileSystem;
import java.nio.file.Path;

/**
 * Represents a cgroup directory in the control group v2 hierarchy, see
 * <a href="https://www.kernel.org/doc/html/latest/admin-guide/cgroup-v2.html">Control Group v2</a>.
 *
 * @author hakonhall
 */
public class ControlGroup {
    private final Path root;
    private final Path relativePath;

    public static ControlGroup root(FileSystem fileSystem) {
        return new ControlGroup(fileSystem.getPath("/sys/fs/cgroup"), fileSystem.getPath(""));
    }

    private ControlGroup(Path root, Path relativePath) {
        this.root = root.normalize();
        this.relativePath = this.root.relativize(this.root.resolve(relativePath).normalize());
        if (this.relativePath.toString().equals("..") || this.relativePath.toString().startsWith("../")) {
            throw new IllegalArgumentException("Invalid cgroup relative path: " + relativePath);
        }
    }

    /**
     * Resolve the given path against the path of this cgroup, and return the resulting cgroup.
     * If the given path is absolute, it is resolved against the root of the cgroup hierarchy.
     */
    public ControlGroup resolve(String path) {
        Path effectivePath = fileSystem().getPath(path);
        if (effectivePath.isAbsolute()) {
            return new ControlGroup(root, fileSystem().getPath("/").relativize(effectivePath));
        } else {
            return new ControlGroup(root, relativePath.resolve(path));
        }
    }

    /** Returns the parent ControlGroup. */
    public ControlGroup resolveParent() { return new ControlGroup(root, relativePath.getParent()); }

    /** Returns the ControlGroup of a system service, e.g. vespa-host-admin. */
    public ControlGroup resolveSystemService(String name) { return resolve("/system.slice").resolve(serviceNameOf(name)); }

    /** Returns the root ControlGroup of the given Podman container. */
    public ControlGroup resolveContainer(ContainerId containerId) { return resolve("/machine.slice/libpod-" + containerId + ".scope/container"); }

    /** Returns the ControlGroup of a system service in the given Podman container. */
    public ControlGroup resolveContainerSystemService(ContainerId containerId, String name) { return resolveContainer(containerId).resolve("system.slice").resolve(serviceNameOf(name)); }

    /** Returns the absolute path to this cgroup. */
    public Path path() { return root.resolve(relativePath); }

    /** Returns the absolute UnixPath to this cgroup. */
    public UnixPath unixPath() { return new UnixPath(path()); }

    /** Returns the CPU controller of this ControlGroup. */
    public CpuController cpu() { return new CpuController(this); }

    /** Returns the memory controller of this ControlGroup. */
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

    private static String serviceNameOf(String name) {
        return name.indexOf('.') == -1 ? name + ".service" : name;
    }

    private FileSystem fileSystem() { return root.getFileSystem(); }
}
