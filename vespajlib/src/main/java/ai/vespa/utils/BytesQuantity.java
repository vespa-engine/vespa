package ai.vespa.utils;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reprents a quantity of bytes with a human-readable string representation.
 * Currently only supports binary units (e.g. 1 kB = 1024 bytes).
 *
 * @author bjorncs
 */
public class BytesQuantity {
    public enum Unit {
        BYTES, KB, MB, GB, TB;

        public long binarySize() {
            return switch (this) {
                case BYTES -> 1;
                case KB -> 1 << 10;
                case MB -> 1 << 20;
                case GB -> 1 << 30;
                case TB -> 1L << 40;
            };
        }

        public String toUnitString() {
            return switch (this) {
                case BYTES -> "bytes";
                case KB -> "kB";
                case MB -> "MB";
                case GB -> "GB";
                case TB -> "TB";
            };
        }

        static Unit fromString(String s) {
            return switch (s) {
                case "", "B", "bytes", "byte" -> BYTES;
                case "kB", "k", "K", "KB" -> KB;
                case "MB", "m", "M" -> MB;
                case "GB", "g", "G" -> GB;
                case "TB", "t", "T" -> TB;
                default -> throw new IllegalArgumentException("Invalid unit: " + s);
            };
        }
    }

    private final long bytes;
    private BytesQuantity(long bytes) { this.bytes = bytes; }

    public long toBytes() { return bytes; }

    public static BytesQuantity ofBytes(long bytes) { return new BytesQuantity(bytes); }
    public static BytesQuantity ofKB(long kb) { return BytesQuantity.of(kb, Unit.KB); }
    public static BytesQuantity ofMB(long mb) { return BytesQuantity.of(mb, Unit.MB); }
    public static BytesQuantity ofGB(long gb) { return BytesQuantity.of(gb, Unit.GB); }
    public static BytesQuantity ofTB(long tb) { return BytesQuantity.of(tb, Unit.TB); }
    public static BytesQuantity of(long value, Unit unit) { return new BytesQuantity(value * unit.binarySize()); }

    private static final Pattern PATTERN = Pattern.compile("^(?<digits>\\d+)\\s*(?<unit>[a-zA-Z]*)$");
    public static BytesQuantity fromString(String value) {
        var matcher = PATTERN.matcher(value);
        if (!matcher.matches())
            throw new IllegalArgumentException(
                    "Bytes quantity '%s' does not match pattern '%s'".formatted(value, PATTERN.pattern()));
        var digits = Long.parseLong(matcher.group("digits"));
        var unit = Unit.fromString(matcher.group("unit"));
        return BytesQuantity.of(digits, unit);
    }

    public String asPrettyString() {
        if (bytes == 0) return "0 bytes";
        if (bytes == 1) return "1 byte";
        long remaining = bytes;
        int unit = 0;
        for (; remaining % 1024 == 0 && unit < Unit.values().length - 1; unit++) remaining /= 1024;
        return String.format(Locale.ENGLISH, "%d %s", remaining, Unit.values()[unit].toUnitString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BytesQuantity that = (BytesQuantity) o;
        return bytes == that.bytes;
    }

    @Override public int hashCode() { return Objects.hashCode(bytes); }
    @Override public String toString() { return asPrettyString(); }
}
