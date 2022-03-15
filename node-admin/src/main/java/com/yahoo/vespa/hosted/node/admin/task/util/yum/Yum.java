// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.Terminal;

import java.util.List;
import java.util.Optional;

import static com.yahoo.vespa.hosted.node.admin.task.util.yum.YumCommand.DeleteVersionLockYumCommand;
import static com.yahoo.vespa.hosted.node.admin.task.util.yum.YumCommand.GenericYumCommand;
import static com.yahoo.vespa.hosted.node.admin.task.util.yum.YumCommand.InstallFixedYumCommand;

/**
 * @author hakonhall
 */
public class Yum {

    private final Terminal terminal;

    public Yum(Terminal terminal) {
        this.terminal = terminal;
    }

    public Optional<YumPackageName> queryInstalled(TaskContext context, String packageName) {
        return YumCommand.queryInstalled(terminal, context, YumPackageName.fromString(packageName));
    }

    /** Lock and install, or if necessary downgrade, a package to a given version. */
    public InstallFixedYumCommand installFixedVersion(YumPackageName yumPackage) {
        return new InstallFixedYumCommand(terminal, yumPackage);
    }

    public GenericYumCommand install(YumPackageName... packages) {
        return new GenericYumCommand(terminal, GenericYumCommand.CommandType.install, List.of(packages));
    }

    public GenericYumCommand install(String package1, String... packages) {
        return install(toYumPackageNameArray(package1, packages));
    }

    public GenericYumCommand install(List<String> packages) {
        return install(packages.stream().map(YumPackageName::fromString).toArray(YumPackageName[]::new));
    }


    public GenericYumCommand upgrade(YumPackageName... packages) {
        return new GenericYumCommand(terminal, GenericYumCommand.CommandType.upgrade, List.of(packages));
    }

    public GenericYumCommand upgrade(String package1, String... packages) {
        return upgrade(toYumPackageNameArray(package1, packages));
    }

    public GenericYumCommand upgrade(List<String> packages) {
        return upgrade(packages.stream().map(YumPackageName::fromString).toArray(YumPackageName[]::new));
    }


    public GenericYumCommand remove(YumPackageName... packages) {
        return new GenericYumCommand(terminal, GenericYumCommand.CommandType.remove, List.of(packages));
    }

    public GenericYumCommand remove(String package1, String... packages) {
        return remove(toYumPackageNameArray(package1, packages));
    }

    public GenericYumCommand remove(List<String> packages) {
        return remove(packages.stream().map(YumPackageName::fromString).toArray(YumPackageName[]::new));
    }

    public YumCommand.DeleteVersionLockYumCommand deleteVersionLock(YumPackageName yumPackage) {
        return new DeleteVersionLockYumCommand(terminal, yumPackage);
    }

    static YumPackageName[] toYumPackageNameArray(String package1, String... packages) {
        YumPackageName[] array = new YumPackageName[1 + packages.length];
        array[0] = YumPackageName.fromString(package1);
        for (int i = 0; i < packages.length; ++i) {
            array[1 + i] = YumPackageName.fromString(packages[i]);
        }
        return array;
    }

}
