// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.status.statuspage;

import java.util.ArrayList;

/**
 * Helper class in order to write HTML tables
 */
public class HtmlTable {

    int border = 1;
    int cellSpacing = 0;
    enum Orientation { LEFT, CENTER, RIGHT }

    public static class CellProperties {
        Integer backgroundColor;
        Integer colSpan; // Colspan 0 indicate rest of table
        Integer rowSpan;
        Orientation contentAlignment;
        Boolean allowLineBreaks;

        CellProperties setColSpan(Integer span) { this.colSpan = span; return this; }
        CellProperties setRowSpan(Integer span) { this.rowSpan = span; return this; }
        CellProperties setBackgroundColor(Integer bgcol) { this.backgroundColor = bgcol; return this; }
        CellProperties align(Orientation alignment) { this.contentAlignment = alignment; return this; }
        CellProperties allowLineBreaks(Boolean allow) { this.allowLineBreaks = allow; return this; }

        void add(CellProperties cp) {
            if (cp.backgroundColor != null) backgroundColor = cp.backgroundColor;
            if (cp.colSpan != null) colSpan = cp.colSpan;
            if (cp.rowSpan != null) rowSpan = cp.rowSpan;
            if (cp.contentAlignment != null) contentAlignment = cp.contentAlignment;
            if (cp.allowLineBreaks != null) allowLineBreaks = cp.allowLineBreaks;
        }
    }
    ArrayList<CellProperties> colProperties = new ArrayList<CellProperties>();
    CellProperties tableProperties = new CellProperties();
    public static class Cell {
        CellProperties properties = new CellProperties();
        String content;

        Cell(String content) { this.content = content; }

        Cell addProperties(CellProperties c) { properties.add(c); return this; }
    }
    public static class Row {
        boolean isHeaderRow;
        ArrayList<Cell> cells = new ArrayList<Cell>();
        CellProperties rowProperties = new CellProperties();

        public Row addCell(Cell c) {
            cells.add(c);
            return this;
        }
        public Cell getLastCell() {
            return cells.get(cells.size() - 1);
        }

        Row setHeaderRow() { isHeaderRow = true; return this; }
        Row addProperties(CellProperties p) { rowProperties.add(p); return this; }
    }

    private final ArrayList<Row> cells = new ArrayList<Row>();

    public HtmlTable() {
    }

    public HtmlTable addRow(Row r) {
        cells.add(r);
        return this;
    }

    public CellProperties getTableProperties() { return tableProperties; }

    public CellProperties getColProperties(int col) {
        while (colProperties.size() <= col) {
            colProperties.add(new CellProperties());
        }
        return colProperties.get(col);
    }

    private String getColor(int color) {
        String col = Integer.toHexString(color);
        while (col.length() < 6) col = "0" + col;
        return col;
    }

    public static String escape(String s) {
        s = s.replaceAll("&", "&amp;");
        s = s.replaceAll("<", "&lt;");
        s = s.replaceAll(">", "&gt;");
        return s;
    }

    public int getColumnCount() {
        int cols = 0;
        ArrayList<Integer> next = new ArrayList<Integer>();
        for (Row row : cells) {
            int rowCount = 0;
            if (!next.isEmpty()) {
                rowCount += next.get(0);
                next.remove(0);
            }
            for (Cell c : row.cells) {
                int width = 1;
                if (c.properties.colSpan != null && c.properties.colSpan > 1) {
                    width = c.properties.colSpan;
                }
                rowCount += width;
                if (c.properties.rowSpan != null && c.properties.rowSpan > 1) {
                    while (next.size() < c.properties.rowSpan - 1) {
                        next.add(0);
                    }
                    for (int i=1; i<c.properties.rowSpan; ++i) {
                        next.set(i - 1, next.get(i - 1) + width);
                    }
                }
            }
            cols = Math.max(cols, rowCount);
        }
        return cols;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("<table border=\"").append(border).append("\" cellSpacing=\"").append(cellSpacing).append("\">\n");
        int columnCount = getColumnCount();
        for (Row row : cells) {
            sb.append("<tr>\n");
            for (int i=0; i<row.cells.size(); ++i) {
                Cell cell = row.cells.get(i);
                CellProperties properties = new CellProperties();
                properties.add(tableProperties);
                if (colProperties.size() > i) {
                    properties.add(colProperties.get(i));
                }
                properties.add(row.rowProperties);
                properties.add(cell.properties);

                sb.append(row.isHeaderRow ? "<th" : "<td");
                if (properties.backgroundColor != null) {
                    sb.append(" bgcolor=\"#").append(getColor(properties.backgroundColor)).append('"');
                }
                if (properties.contentAlignment != null) {
                    sb.append(" align=\"").append(properties.contentAlignment.name().toLowerCase()).append('"');
                }
                if (properties.colSpan != null) {
                    int colSpan = properties.colSpan;
                    if (colSpan == 0) colSpan = (columnCount - i);
                    sb.append(" colspan=\"").append(colSpan).append('"');
                }
                if (properties.rowSpan != null) {
                    sb.append(" rowspan=\"").append(properties.rowSpan).append('"');
                }
                sb.append(">");
                if (properties.allowLineBreaks != null && !properties.allowLineBreaks) sb.append("<nobr>");
                sb.append(cell.content);
                if (properties.allowLineBreaks != null && !properties.allowLineBreaks) sb.append("</nobr>");
                sb.append(row.isHeaderRow ? "</th>" : "</td>").append("\n");
            }
            sb.append("</tr>\n");
        }
        sb.append("</table>\n");
        return sb.toString();
    }

}
