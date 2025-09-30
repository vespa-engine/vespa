// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.exportsignificance;

import java.io.IOException;
import java.nio.file.*;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Searches for index.flush.n folders from root.
 *
 * @author johsol
 */
final class FlushedIndexPathIterator implements Iterable<Path>, Iterator<Path> {
    private final Iterator<Path> it;

    public FlushedIndexPathIterator(Path root) {
        try (Stream<Path> s = Files.find(root, Integer.MAX_VALUE,
                (p, a) -> a.isDirectory() &&
                        p.getFileName().toString().matches("index\\.flush\\.\\d+"))) {
            List<Path> hits = s.toList();   // eager collect; closes stream safely
            this.it = hits.iterator();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean hasNext() {
        return it.hasNext();
    }

    @Override
    public Path next() {
        return it.next();
    }

    @Override
    public Iterator<Path> iterator() {
        return this;
    }
}
