// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandResult;
import com.yahoo.vespa.hosted.node.admin.task.util.process.Terminal;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.node.admin.task.util.yum.YumCommand.GenericYumCommand;
import static com.yahoo.vespa.hosted.node.admin.task.util.yum.YumCommand.InstallFixedYumCommand;

/**
 * @author hakonhall
 */
public class Yum {

    // Note: "(?dm)" makes newline be \n (only), and enables multiline mode where ^$ match lines with find()
    public static final Pattern INSTALL_NOOP_PATTERN = Pattern.compile("(?dm)^Nothing to do\\.?$");
    public static final Pattern UPGRADE_NOOP_PATTERN = Pattern.compile("(?dm)^No packages marked for update$");
    public static final Pattern REMOVE_NOOP_PATTERN = Pattern.compile("(?dm)^No [pP]ackages marked for removal\\.?$");

    // WARNING: These must be in the same order as the supplier below
    private static final String RPM_QUERYFORMAT = Stream.of("NAME", "EPOCH", "VERSION", "RELEASE", "ARCH")
            .map(formatter -> "%{" + formatter + "}")
            .collect(Collectors.joining("\\n"));
    private static final Function<YumPackageName.Builder, List<Function<String, YumPackageName.Builder>>>
            PACKAGE_NAME_BUILDERS_GENERATOR = builder -> List.of(
                builder::setName, builder::setEpoch, builder::setVersion, builder::setRelease, builder::setArchitecture);


    private final Terminal terminal;

    public Yum(Terminal terminal) {
        this.terminal = terminal;
    }

    public Optional<YumPackageName> queryInstalled(TaskContext context, String packageName) {
        CommandResult commandResult = terminal.newCommandLine(context)
                .add("rpm", "-q", packageName, "--queryformat", RPM_QUERYFORMAT)
                .ignoreExitCode()
                .executeSilently();

        if (commandResult.getExitCode() != 0) return Optional.empty();

        YumPackageName.Builder builder = new YumPackageName.Builder();
        List<Function<String, YumPackageName.Builder>> builders = PACKAGE_NAME_BUILDERS_GENERATOR.apply(builder);
        List<Optional<String>> lines = commandResult.mapEachLine(line -> Optional.of(line).filter(s -> !"(none)".equals(s)));
        if (lines.size() != builders.size()) throw new IllegalStateException(String.format(
                "Unexpected response from rpm, expected %d lines, got %s", builders.size(), commandResult.getOutput()));

        IntStream.range(0, builders.size()).forEach(i -> lines.get(i).ifPresent(builders.get(i)::apply));
        return Optional.of(builder.build());
    }

    /** Lock and install, or if necessary downgrade, a package to a given version. */
    public InstallFixedYumCommand installFixedVersion(YumPackageName yumPackage) {
        return new InstallFixedYumCommand(terminal, yumPackage);
    }

    public GenericYumCommand install(YumPackageName... packages) {
        return new GenericYumCommand(terminal, "install", List.of(packages), INSTALL_NOOP_PATTERN);
    }

    public GenericYumCommand install(String package1, String... packages) {
        return install(toYumPackageNameArray(package1, packages));
    }

    public GenericYumCommand install(List<String> packages) {
        return install(packages.stream().map(YumPackageName::fromString).toArray(YumPackageName[]::new));
    }


    public GenericYumCommand upgrade(YumPackageName... packages) {
        return new GenericYumCommand(terminal, "upgrade", List.of(packages), INSTALL_NOOP_PATTERN, UPGRADE_NOOP_PATTERN);
    }

    public GenericYumCommand upgrade(String package1, String... packages) {
        return upgrade(toYumPackageNameArray(package1, packages));
    }

    public GenericYumCommand upgrade(List<String> packages) {
        return upgrade(packages.stream().map(YumPackageName::fromString).toArray(YumPackageName[]::new));
    }


    public GenericYumCommand remove(YumPackageName... packages) {
        return new GenericYumCommand(terminal, "remove", List.of(packages), REMOVE_NOOP_PATTERN);
    }

    public GenericYumCommand remove(String package1, String... packages) {
        return remove(toYumPackageNameArray(package1, packages));
    }

    public GenericYumCommand remove(List<String> packages) {
        return remove(packages.stream().map(YumPackageName::fromString).toArray(YumPackageName[]::new));
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
