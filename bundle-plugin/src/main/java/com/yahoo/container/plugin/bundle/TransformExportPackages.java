// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.bundle;

import com.yahoo.container.plugin.osgi.ExportPackages.Export;
import com.yahoo.container.plugin.osgi.ExportPackages.Parameter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class TransformExportPackages {
    public static List<Export> replaceVersions(List<Export> exports, String newVersion) {
        List<Export> ret = new ArrayList<>();

        for (Export export : exports) {
            List<Parameter> newParams = new ArrayList<>();
            for (Parameter param : export.getParameters()) {
                if ("version".equals(param.getName())) {
                    newParams.add(new Parameter("version", newVersion));
                } else {
                    newParams.add(param);
                }
            }
            ret.add(new Export(export.getPackageNames(), newParams));
        }
        return ret;
    }

    public static List<Export> removeUses(List<Export> exports) {
        List<Export> ret = new ArrayList<>();

        for (Export export : exports) {
            List<Parameter> newParams = new ArrayList<>();
            for (Parameter param : export.getParameters()) {
                if ("uses".equals(param.getName()) == false) {
                    newParams.add(param);
                }
            }
            ret.add(new Export(export.getPackageNames(), newParams));
        }
        return ret;
    }

    public static String toExportPackageProperty(List<Export> exports) {
        return exports.stream().map(exp -> {
            String oneExport = String.join(";", exp.getPackageNames());
            if (exp.getParameters().size() > 0) {
                String paramString = exp.getParameters().stream().map(param -> param.getName() + "=" + quote(param.getValue())).collect(Collectors.joining(";"));
                oneExport += ";" + paramString;
            }
            return oneExport;
        }).collect(Collectors.joining(","));
    }

    public static String quote(String s) {
        return "\"" + s + "\"";
    }
}
