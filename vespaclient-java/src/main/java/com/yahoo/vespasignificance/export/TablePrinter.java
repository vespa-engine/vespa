// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespasignificance.export;

import java.util.List;

/**
 * Pretty prints a table to the terminal.
 *
 * @author johsol
 */
final class TablePrinter {

    static void printTable(String heading, List<String> headers, List<List<String>> rows) {
        System.out.println(heading);
        int cols = headers.size();
        int[] widths = new int[cols];
        for (int c = 0; c < cols; c++) widths[c] = headers.get(c).length();
        for (var row : rows) {
            for (int c = 0; c < cols; c++) {
                widths[c] = Math.max(widths[c], row.get(c).length());
            }
        }
        // header
        for (int c = 0; c < cols; c++) {
            System.out.print(rpad(headers.get(c), widths[c]));
            System.out.print(c + 1 < cols ? "  " : "\n");
        }
        // underline
        for (int c = 0; c < cols; c++) {
            System.out.print("-".repeat(widths[c]));
            System.out.print(c + 1 < cols ? "  " : "\n");
        }
        // rows
        for (var row : rows) {
            for (int c = 0; c < cols; c++) {
                System.out.print(rpad(row.get(c), widths[c]));
                System.out.print(c + 1 < cols ? "  " : "\n");
            }
        }
    }

    private static String rpad(String s, int w) {
        int pad = Math.max(0, w - s.length());
        return s + " ".repeat(pad);
    }

    private TablePrinter() {}
}
