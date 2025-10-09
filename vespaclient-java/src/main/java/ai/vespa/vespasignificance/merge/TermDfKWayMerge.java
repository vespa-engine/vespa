// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.merge;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * This class implements k-way merge for term-df files.
 * <p>
 * The implementation uses a min-heap on a set of cursors where each cursor
 * holds a file handle to one of the files being merged.
 *
 * @author johsol
 */
public class TermDfKWayMerge {

    /**
     * Holds each buffered reader and a cursor to the current line.
     */
    final static class Cursor {
        final BufferedReader bufferedReader;

        String term;
        long documentFrequency;

        Cursor(BufferedReader bufferedReader) {
            this.bufferedReader = bufferedReader;
        }

        /**
         * Parses the next TermDf line from the buffered reader.
         * <p>
         * Returns true if a new term and df was parsed, else false if there
         * is no more data to parse.
         */
        boolean advance() throws IOException {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;

                int tab = line.indexOf('\t');
                if (tab < 0) {
                    throw new IllegalArgumentException("Invalid term line when expecting tab: " + line);
                }

                term = line.substring(0, tab);
                documentFrequency = Long.parseLong(line.substring(tab + 1).trim());
                return true;
            }

            return false;
        }
    }

    /**
     * k-way merge between buffered readers expecting "term\tnumber" for each line.
     * <p>
     * Every reader is read linearly. And since we use a min-heap to keep track of which to compare the current with,
     * and it has insertion of log(k), it will have a time complexity of O(n log(k)).
     * <p>
     * Assumes one term per line.
     */
    public static void merge(
            List<BufferedReader> inputs,
            BufferedWriter output,
            long minKeep
    ) throws IOException {
        PriorityQueue<Cursor> queue = new PriorityQueue<>(inputs.size(), Comparator.comparing(c -> c.term));
        for (var reader : inputs) {
            Cursor cursor = new Cursor(reader);
            if (cursor.advance()) {
                queue.offer(cursor);
            }
        }

        while (!queue.isEmpty()) {
            Cursor cursor = queue.poll();
            String term = cursor.term;
            long sumDf = cursor.documentFrequency;

            while (!queue.isEmpty() && queue.peek().term.equals(term)) {
                Cursor matchedCursor = queue.poll();
                sumDf += matchedCursor.documentFrequency;

                if (matchedCursor.advance()) {
                    queue.offer(matchedCursor);
                }
            }

            if (cursor.advance()) {
                queue.offer(cursor);
            }

            if (sumDf >= minKeep) {
                output.write(term);
                output.write('\t');
                output.write(Long.toString(sumDf));
                output.write('\n');
            }
        }

        output.flush();
    }

}
