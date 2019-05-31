// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool.securityenv;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Definition of some unix shell variants and how to export environments variable for those supported.
 * The output format is inspired by ssh-agent's output.
 *
 * @author bjorncs
 */
enum UnixShell {
    BOURNE("bourne", List.of("bash", "sh")) {
        @Override
        void writeOutputVariables(PrintStream out, Map<String, String> outputVariables) {
            outputVariables.forEach((name, value) -> {
                out.print(name);
                out.print('=');
                out.print(value); // note: value is assumed to need no escaping
                out.print("; export ");
                out.print(name);
                out.println(';');
            });
        }
    },
    CSHELL("cshell", List.of("csh", "fish")) {
        @Override
        void writeOutputVariables(PrintStream out, Map<String, String> outputVariables) {
            outputVariables.forEach((name, value) -> {
                out.print("setenv ");
                out.print(name);
                out.print(' ');
                out.print(value); // note: value is assumed to need no escaping
                out.println(';');
            });
        }
    };

    private static final UnixShell DEFAULT = BOURNE;

    private final String configName;
    private final List<String> knownShellBinaries;

    UnixShell(String configName, List<String> knownShellBinaries) {
        this.configName = configName;
        this.knownShellBinaries = knownShellBinaries;
    }

    abstract void writeOutputVariables(PrintStream out, Map<String, String> outputVariables);

    String configName() {
        return configName;
    }

    static UnixShell fromConfigName(String configName) {
        return Arrays.stream(values())
                .filter(shell -> shell.configName.equals(configName))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("Unknown shell: " + configName));
    }

    static UnixShell detect(String shellEnvVariable) {
        if (shellEnvVariable == null || shellEnvVariable.isEmpty()) return DEFAULT;
        int lastSlash = shellEnvVariable.lastIndexOf('/');
        String shellName = lastSlash != -1 ? shellEnvVariable.substring(lastSlash + 1) : shellEnvVariable;
        return Arrays.stream(values())
                .filter(shell -> shell.knownShellBinaries.contains(shellName))
                .findAny()
                .orElse(DEFAULT);
    }
}
