// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.merge;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * This class implements k-way merge for multiple streams of "term\tnumber\n".
 * <p>
 * The implementation uses a min-heap on a set of cursors where each cursor
 * holds a file handle to one of the files being merged and the current term
 * and document frequency value for that file.
 *
 * @author johsol
 */
public class TermDfKWayMerge {

    /**
     * Holds handle to buffered reader and when advanced holds the current term and document
     * frequency for this stream.
     */
    final static class Cursor {

        final BufferedReader bufferedReader;
        String term;
        long documentFrequency;
        long lineNo;

        Cursor(BufferedReader bufferedReader) {
            this.bufferedReader = bufferedReader;
            this.lineNo = 1;
        }

        /**
         * Parses term and document frequency from the next line from the buffered reader.
         * <p>
         * Returns true if it parsed a new line successfully, and false if there are no more lines.
         */
        boolean advance() throws IOException {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;

                int tab = line.indexOf('\t');
                if (tab < 0) {
                    throw new IllegalArgumentException("Missing tab at line " + lineNo + ": " + line);
                }

                term = line.substring(0, tab);
                if (term.isEmpty()) {
                    throw new IllegalArgumentException("Empty term at line " + lineNo);
                }

                String num = line.substring(tab + 1).trim();
                try {
                    documentFrequency = Long.parseLong(num);
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("Invalid number at line " + lineNo + ": \"" + num + "\"", nfe);
                }

                return true;
            }

            return false;
        }
    }

    /**
     * k-way merge between buffered readers expecting "term\tnumber" for each line.
     * <p>
     * Every reader is read linearly. And since we use a min-heap to find which streams to merge and polling
     * (take Cursor out of the queue) and offering (inserting cursor into the queue) takes O(log(k)), this
     * algorithm has a time complexity of O(n log(k)).
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
            String term = queue.peek().term;
            long sumDf = 0L;

            while (!queue.isEmpty() && queue.peek().term.equals(term)) {
                Cursor cursor = queue.poll();
                sumDf += cursor.documentFrequency;

                if (cursor.advance()) {
                    queue.offer(cursor);
                }
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
