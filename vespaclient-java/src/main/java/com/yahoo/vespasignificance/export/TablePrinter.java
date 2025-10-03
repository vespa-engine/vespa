// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license.
package com.yahoo.vespasignificance.export;

import java.util.List;

/**
 * Pretty print table.
 *
 * @author johsol
 */
final class TablePrinter {

    /** Unicode box-drawing table with auto width and middle-ellipsizing. */
    static void printTable(String heading, List<String> headers, List<List<String>> rows) {
        System.out.println(heading);

        final int cols = headers.size();
        final int[] w = computeColumnWidths(headers, rows);

        int maxWidth = parseInt(System.getenv("COLUMNS"), 120);
        shrinkToWidth(w, maxWidth);

        // Top border
        System.out.println(frameLine('┌', '┬', '┐', '─', w));

        // Header row
        System.out.println(rowLine('│', headers, w));

        // Header separator
        System.out.println(frameLine('├', '┼', '┤', '─', w));

        // Data rows
        for (var r : rows) {
            System.out.println(rowLine('│', r, w));
        }

        // Bottom border
        System.out.println(frameLine('└', '┴', '┘', '─', w));
    }

    private static int[] computeColumnWidths(List<String> headers, List<List<String>> rows) {
        int cols = headers.size();
        int[] w = new int[cols];
        for (int c = 0; c < cols; c++) w[c] = headers.get(c).length();
        for (var r : rows) {
            for (int c = 0; c < cols; c++) {
                w[c] = Math.max(w[c], r.get(c).length());
            }
        }
        return w;
    }

    private static String frameLine(char left, char mid, char right, char hz, int[] w) {
        var sb = new StringBuilder();
        sb.append(left);
        for (int c = 0; c < w.length; c++) {
            sb.append(String.valueOf(hz).repeat(w[c] + 2));
            sb.append(c + 1 < w.length ? mid : right);
        }
        return sb.toString();
    }

    private static String rowLine(char vt, List<String> cells, int[] w) {
        var sb = new StringBuilder();
        sb.append(vt);
        for (int c = 0; c < w.length; c++) {
            String cell = ellipsize(cells.get(c), w[c]);
            sb.append(' ').append(rpad(cell, w[c])).append(' ');
            sb.append(vt);
        }
        return sb.toString();
    }

    private static String rpad(String s, int w) {
        int pad = Math.max(0, w - s.length());
        return s + " ".repeat(pad);
    }

    /** Middle-ellipsize long strings to fit column width. */
    private static String ellipsize(String s, int w) {
        if (s.length() <= w) return s;
        if (w <= 1) return s.substring(0, Math.max(0, w));
        if (w == 2) return s.substring(0, 2);
        int head = (w - 1) / 2;
        int tail = w - 1 - head;
        return s.substring(0, head) + '…' + s.substring(s.length() - tail);
    }

    /** Try parse int with default. */
    private static int parseInt(String s, int def) {
        try { return (s == null) ? def : Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    /**
     * Try to shrink rightmost columns so overall table width ≲ maxWidth.
     * Each column keeps at least 8 chars (headers still fit).
     */
    private static void shrinkToWidth(int[] w, int maxWidth) {
        // total width = borders + inter-column joints + content+padding
        // content width = sum(w[c] + 2) ; there are N+1 verticals
        int cols = w.length;
        int total = 1 /*left*/ + 1 /*right*/ + (cols - 1) /*joints*/
                + 2 * cols /*padding*/ + sum(w);
        int over = total - maxWidth;
        if (over <= 0) return;

        final int MIN = 8;
        for (int c = cols - 1; c >= 0 && over > 0; c--) {
            int can = Math.max(0, w[c] - MIN);
            int take = Math.min(can, over);
            w[c] -= take; over -= take;
        }
    }

    private static int sum(int[] a) { int s = 0; for (int x : a) s += x; return s; }

    private TablePrinter() {}
}
