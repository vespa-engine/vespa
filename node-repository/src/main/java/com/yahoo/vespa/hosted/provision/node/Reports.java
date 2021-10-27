// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Type;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * @author hakonhall
 */
// @Immutable
public class Reports {

    private final Map<String, Report> reports;

    public Reports() { this(Collections.emptyMap()); }
    private Reports(Map<String, Report> reports) { this.reports = Collections.unmodifiableMap(reports); }

    public boolean isEmpty() { return reports.isEmpty(); }
    public Optional<Report> getReport(String id) { return Optional.ofNullable(reports.get(id)); }
    public List<Report> getReports() { return List.copyOf(reports.values()); }
    public Reports withReport(Report report) { return new Builder(this).setReport(report).build(); }

    public void toSlime(Cursor reportsParentObjectCursor, String reportsName) {
        if (reports.isEmpty()) return;
        Cursor reportsCursor = reportsParentObjectCursor.setObject(reportsName);
        reports.values().forEach(report -> report.toSlime(reportsCursor.setObject(report.getReportId())));
    }

    public static Reports fromSlime(Inspector reportsInspector) {
        // Absent or null "reports" field => empty Reports
        if (reportsInspector.type() != Type.OBJECT) return new Reports();

        var builder = new Builder();
        reportsInspector.traverse((ObjectTraverser) (reportId, reportInspector) -> {
            builder.setReport(Report.fromSlime(reportId, reportInspector));
        });

        return builder.build();
    }

    public static class Builder {
        private final TreeMap<String, Report> reportMap;

        public Builder() { this(new TreeMap<>()); }

        public Builder(Reports reports) {
            this(new TreeMap<>(reports.reports));
        }

        private Builder(TreeMap<String, Report> reportMap) { this.reportMap = reportMap; }

        public Builder setReport(Report report) {
            reportMap.put(report.getReportId(), report);
            return this;
        }

        public Builder clearReport(String reportId) {
            reportMap.remove(reportId);
            return this;
        }

        public Builder clear() {
            reportMap.clear();
            return this;
        }

        public Reports build() { return new Reports(reportMap); }
    }
}
