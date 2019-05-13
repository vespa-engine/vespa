package ai.vespa.hosted.api;

import java.time.Instant;
import java.util.List;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toUnmodifiableList;

/**
 * A list of {@link Entry} items from a deployment job.
 *
 * @author jonmv
 */
public class DeploymentLog {

    private final List<Entry> entries;
    private final boolean active;
    private final long last;

    public DeploymentLog(List<Entry> entries, boolean active, long last) {
        this.entries = entries.stream().sorted(comparing(Entry::at)).collect(toUnmodifiableList());
        this.active = active;
        this.last = last;
    }

    public List<Entry> entries() {
        return entries;
    }

    public boolean isActive() {
        return active;
    }

    public long last() {
        return last;
    }


    public static class Entry {

        private final Instant at;
        private final String level;
        private final String message;

        public Entry(Instant at, String level, String message) {
            this.at = at;
            this.level = level;
            this.message = message;
        }

        public Instant at() {
            return at;
        }

        public String level() {
            return level;
        }

        public String message() {
            return message;
        }

    }

}
