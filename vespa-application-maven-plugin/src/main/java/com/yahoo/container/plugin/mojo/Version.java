package com.yahoo.container.plugin.mojo;

import java.util.Comparator;
import java.util.Objects;

import static java.lang.Integer.parseInt;

/**
 * @author jonmv
 */
public class Version implements Comparable<Version> {

    private static final Comparator<Version> comparator = Comparator.comparingInt(Version::major)
                                                                    .thenComparing(Version::isSnapshot)
                                                                    .thenComparing(Version::minor)
                                                                    .thenComparing(Version::micro);

    private final int major;
    private final int minor;
    private final int micro;
    private final boolean snapshot;

    private Version(int major, int minor, int micro, boolean snapshot) {
        if (major < 0 || minor < 0 || micro < 0) throw new IllegalArgumentException("version numbers must all be non-negative");
        this.major = major;
        this.minor = minor;
        this.micro = micro;
        this.snapshot = snapshot;
    }

    public static Version of(int major, int minor, int micro) {
        return new Version(major, minor, micro, false);
    }

    public static Version ofSnapshot(int major) {
        return new Version(major, 0, 0, true);
    }

    public static Version from(String version) {
        if (version.endsWith("-SNAPSHOT")) {
            String[] parts = version.split("-");
            if (parts.length != 2) throw new IllegalArgumentException("snapshot version must only specify major, e.g., \"1-SNAPSHOT\"");
            return ofSnapshot(parseInt(parts[0]));
        }
        String[] parts = version.split("\\.");
        if (parts.length != 3) throw new IllegalArgumentException("release versions must specify major, minor and micro, separated by '.', e.g., \"1.2.0\"");
        return of(parseInt(parts[0]), parseInt(parts[1]), parseInt(parts[2]));
    }

    public int major() { return major; }
    public int minor() { return minor; }
    public int micro() { return micro; }
    public boolean isSnapshot() { return snapshot; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Version version = (Version) o;
        return major == version.major && minor == version.minor && micro == version.micro && snapshot == version.snapshot;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, micro, snapshot);
    }

    /** Snapshots sort last within their major, and sorting is on major, then minor, then micro otherwise. */
    @Override
    public int compareTo(Version other) {
        return comparator.compare(this, other);
    }

    @Override
    public String toString() {
        return isSnapshot() ? major + "-SNAPSHOT" : major + "." + minor + "." + micro;
    }

}
