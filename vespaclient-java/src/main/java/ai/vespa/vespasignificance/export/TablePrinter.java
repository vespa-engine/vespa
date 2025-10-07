// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license.
package ai.vespa.vespasignificance.export;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Pretty print table.
 *
 * @author johsol
 */
final class TablePrinter {

    /**
     * Print an ascii table to stdout.
     */
    static void printTable(String heading, List<String> headers, List<List<String>> rows) {
        printTable(System.out, heading, headers, rows);
    }

    /**
     * Print an ascii table. Specify PrintStream to use with the out parameter.
     */
    static void printTable(PrintStream out, String heading, List<String> headers, List<List<String>> rows) {
        if (headers == null) headers = Collections.emptyList();
        if (rows == null) rows = Collections.emptyList();

        int cols = headers.size();
        if (cols == 0) {
            // Nothing to format — just print heading (if any) and return.
            if (heading != null && !heading.isEmpty()) {
                out.println(heading);
            }
            return;
        }

        // Normalize rows to avoid NPEs and differing lengths.
        List<List<String>> normalized = new ArrayList<>(rows.size());
        for (List<String> row : rows) {
            List<String> r = new ArrayList<>(cols);
            for (int c = 0; c < cols; c++) {
                String v = (row != null && c < row.size()) ? row.get(c) : "";
                r.add(v == null ? "" : v);
            }
            normalized.add(r);
        }

        // Compute column widths from headers and rows.
        int[] widths = new int[cols];
        for (int c = 0; c < cols; c++) {
            widths[c] = len(safe(headers.get(c)));
        }
        for (List<String> r : normalized) {
            for (int c = 0; c < cols; c++) {
                widths[c] = Math.max(widths[c], len(safe(r.get(c))));
            }
        }

        String sep = buildSeparator(widths);
        int tableWidth = sep.length();

        // Print heading (centered to table width, excluding trailing newline).
        if (heading != null && !heading.isEmpty()) {
            out.println(center(heading, tableWidth));
        }

        // Header
        out.println(sep);
        out.println(buildRow(headers, widths));
        out.println(sep);

        // Rows
        for (List<String> r : normalized) {
            out.println(buildRow(r, widths));
        }
        out.println(sep);
    }

    // ---- helpers ----

    private static String buildSeparator(int[] widths) {
        StringBuilder sb = new StringBuilder();
        sb.append('+');
        for (int w : widths) {
            sb.append(repeat('-', w + 2)).append('+');
        }
        return sb.toString();
    }

    private static String buildRow(List<String> cells, int[] widths) {
        StringBuilder sb = new StringBuilder();
        sb.append('|');
        for (int c = 0; c < widths.length; c++) {
            String v = c < cells.size() ? safe(cells.get(c)) : "";
            sb.append(' ').append(padRight(v, widths[c])).append(' ').append('|');
        }
        return sb.toString();
    }

    private static String padRight(String s, int width) {
        int pad = width - len(s);
        if (pad <= 0) return s;
        return s + repeat(' ', pad);
    }

    private static String center(String s, int width) {
        // Strip trailing spaces from width baseline (like separator newline)
        int w = Math.max(0, width);
        if (len(s) >= w) return s;
        int totalPad = w - len(s);
        int left = totalPad / 2;
        int right = totalPad - left;
        return repeat(' ', left) + s + repeat(' ', right);
    }

    private static String repeat(char ch, int count) {
        if (count <= 0) return "";
        char[] arr = new char[count];
        Arrays.fill(arr, ch);
        return new String(arr);
    }

    private static int len(String s) {
        return s == null ? 0 : s.length();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

}
