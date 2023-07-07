// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.state;

import java.util.LinkedList;
import java.util.List;

/**
 * TODO: document this
 */
public class Diff {

    public static class Entry {
        final String id;
            // Values set for entries that contain diff themselves
        String preContent;
        String postContent;
        boolean bold = false; // Print content in bold. Used if very important
            // Values set for entries that contains subdiffs
        Diff subDiff;
        boolean splitLine = false; // If set, split this content on multiple lines

        public Entry(Object id, Object pre, Object post) {
            this.id = id.toString();
            preContent = pre.toString();
            postContent = post.toString();
        }

        public Entry(Object id, Diff subDiff) {
            this.id = id.toString();
            this.subDiff = subDiff;
        }

        public Entry bold() { bold = true; return this; }
        public Entry splitLine() { splitLine = true; return this; }
    }
    private final List<Entry> diff = new LinkedList<>();

    public void add(Entry e) { diff.add(e); }

    public boolean differs() { return (!diff.isEmpty()); }

    static class PrintProperties {
        boolean insertLineBreaks = false;
        final boolean ommitGroupForSingleEntries = true;
        String lineBreak = "\n";
        final String entrySeparator = ", ";
        final String idValueSeparator = ": ";
        String keyValueSeparator = " => ";
        final String singleGroupSeparator = "";
        final String groupStart = "[";
        final String groupStop = "]";
        String indent = "  ";
        String boldStart = "";
        String boldStop = "";
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        PrintProperties pp = new PrintProperties();
        print(sb, "", pp, false);
        return sb.toString();
    }
    public String toHtml() {
        StringBuilder sb = new StringBuilder();
        PrintProperties pp = new PrintProperties();
        pp.lineBreak = "<br>\n";
        pp.indent = "&nbsp;";
        pp.keyValueSeparator = " =&gt; ";
        pp.insertLineBreaks = true;
        pp.boldStart = "<b>";
        pp.boldStop = "</b>";
        print(sb, "", pp, false);
        return sb.toString();
    }

    public void print(StringBuilder sb, String indent, PrintProperties pp, boolean splitLines) {
        boolean first = true;
        for (Entry e : diff) {
            if (first) {
                first = false;
            } else {
                sb.append(pp.entrySeparator);
                if (splitLines && pp.insertLineBreaks) {
                    sb.append(pp.lineBreak).append(indent);
                }
            }
            sb.append(e.id);
            if (e.subDiff != null) {
                sb.append(pp.idValueSeparator);
                if (e.subDiff.diff.size() > 1 || !pp.ommitGroupForSingleEntries) {
                    sb.append(pp.groupStart);
                } else {
                    sb.append(pp.singleGroupSeparator);
                }
                if (e.splitLine && pp.insertLineBreaks) {
                    sb.append(pp.lineBreak).append(indent + pp.indent);
                }
                e.subDiff.print(sb, indent + pp.indent, pp, e.splitLine);
                if (e.splitLine && pp.insertLineBreaks) {
                    sb.append(pp.lineBreak).append(indent);
                }
                if (e.subDiff.diff.size() > 1 || !pp.ommitGroupForSingleEntries) {
                    sb.append(pp.groupStop);
                }
            } else {
                if (!e.id.isEmpty()) {
                    sb.append(pp.idValueSeparator);
                }
                if (e.bold) {
                    sb.append(pp.boldStart).append(e.preContent).append(pp.boldStop)
                      .append(pp.keyValueSeparator)
                      .append(pp.boldStart).append(e.postContent).append(pp.boldStop);
                } else {
                    sb.append(e.preContent)
                      .append(pp.keyValueSeparator)
                      .append(e.postContent);
                }
            }
        }
    }

}
