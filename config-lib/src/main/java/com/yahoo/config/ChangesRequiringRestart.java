// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.joining;

/**
 * This class aggregates information about config changes that causes a restart to be required.
 *
 * @author Magnar Nedland
 */
public class ChangesRequiringRestart {

    static class ReportLine {
        private String name;
        private final Node from;
        private final Node to;
        private final String comment;

        public ReportLine(String name, Node from, Node to, String comment) {
            this.name = name;
            this.from = from;
            this.to = to;
            this.comment = comment;
        }

        public void addNamePrefix(String prefix) {
            if (!name.isEmpty()) {
                name = prefix + "." + name;
            } else {
                name = prefix;
            }
        }

        private String getCommentAndName(String indent, String namePrefix) {
            return indent + (comment.isEmpty()? "" : "# " + comment.replace("\n", "\n" + indent + "# ") + "\n" + indent)
                    + namePrefix + name;
        }

        private static String formatValue(String indent, Node n) {
            String str = n.toString();
            if (str.contains("\n")) {  // Struct
                str = "\n" + indent + "  { " + str.replace("\n", "\n" + indent + "    ") + " }";
            }
            return str;
        }

        @Override
        public String toString() {
            return toString("", "");
        }

        public String toString(String indent, String namePrefix) {
            if (from == null) {
                return getCommentAndName(indent, namePrefix) + " was added with value " + formatValue(indent, to);
            } else if (to == null) {
                return getCommentAndName(indent, namePrefix) + " with value " + formatValue(indent, from) + " was removed";
            }
            return getCommentAndName(indent, namePrefix) + " has changed from " + formatValue(indent, from) + " to " + formatValue(indent, to);
        }
    }

    private ArrayList<ReportLine> report = new ArrayList<>();
    private String componentName;

    public ChangesRequiringRestart(String componentName) {
        this.componentName = componentName;
    }

    public String getName() {
        return componentName;
    }

    public ChangesRequiringRestart compare(Node from, Node to, String name, String comment) {
        if (!from.equals(to)) {
            report.add(new ReportLine(name, from, to, comment));
        }
        return this;
    }

    public void mergeChanges(String prefix, ChangesRequiringRestart childReport) {
        for (ReportLine line : childReport.getReportLines()) {
            line.addNamePrefix(prefix);
            report.add(line);
        }
    }

    /**
     * Interface used to pass lambda functions from generated code to compareArray/-Map functions.
     */
    public interface CompareFunc {
        // Generates a report based on a config change.
        ChangesRequiringRestart getChangesRequiringRestart(Node from, Node to);
    }

    public ChangesRequiringRestart compareArray(List<? extends Node> from,
                                                List<? extends Node> to,
                                                String name,
                                                String comment,
                                                CompareFunc func) {
        if (!from.equals(to)) {
            int commonElements = Math.min(from.size(), to.size());
            for (int i = 0; i < commonElements; ++i) {
                ChangesRequiringRestart childReport = func.getChangesRequiringRestart(from.get(i), to.get(i));
                String prefix = childReport.componentName + "[" + Integer.toString(i) + "]";
                mergeChanges(prefix, childReport);
            }
            for (int i = commonElements; i < from.size(); ++i) {
                report.add(new ReportLine(name + "[" + Integer.toString(i) + "]", from.get(i), null, comment));
            }
            for (int i = commonElements; i < to.size(); ++i) {
                report.add(new ReportLine(name + "[" + Integer.toString(i) + "]", null, to.get(i), comment));
            }
        }
        return this;
    }

    public ChangesRequiringRestart compareMap(Map<String, ? extends Node> from,
                                              Map<String, ? extends Node> to,
                                              String name,
                                              String comment,
                                              CompareFunc func) {
        if (!from.equals(to)) {
            for (String key : from.keySet()) {
                if (to.containsKey(key)) {
                    ChangesRequiringRestart childReport = func.getChangesRequiringRestart(from.get(key), to.get(key));
                    String prefix = childReport.componentName + "{" + key + "}";
                    mergeChanges(prefix, childReport);
                } else {
                    report.add(new ReportLine(name + "{" + key + "}", from.get(key), null, comment));
                }
            }
            for (String key : to.keySet()) {
                if (!from.containsKey(key)) {
                    report.add(new ReportLine(name + "{" + key + "}", null, to.get(key), comment));
                }
            }
        }
        return this;
    }

    List<ReportLine> getReportLines() {
        return report;
    }

    @Override
    public String toString() {
        return toString("");
    }

    public String toString(String indent) {
        return report.stream()
                .map(line -> line.toString(indent, componentName + "."))
                .collect(joining("\n"));
    }

    public boolean needsRestart() {
        return !report.isEmpty();
    }

}
