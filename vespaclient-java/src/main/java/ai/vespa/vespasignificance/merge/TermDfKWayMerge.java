package ai.vespa.vespasignificance.merge;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.rmi.UnexpectedException;
import java.util.List;

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
    static class Cursor {
        final BufferedReader br;

        String term;
        long df;

        Cursor(BufferedReader br) {
            this.br = br;
        }

        /**
         * Parses the next TermDf line from the buffered reader.
         */
        boolean advance() throws IOException {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;

                int tab = line.indexOf('\t');
                if (tab < 0) {
                    throw new UnexpectedException("Invalid term line when expecting tab: " + line);
                }

                term = line.substring(0, tab);
                df =  Long.parseLong(line.substring(tab + 1).trim());
                return true;
            }

            return false;
        }
    }

    public static void merge(
            List<Path> paths,
            Path output,
            long minKeep
    ) {

    }

}
