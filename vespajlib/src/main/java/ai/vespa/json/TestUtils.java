package ai.vespa.json;

/**
 * Utilities for tests where you need JSON comparisons
 *
 * @author arnej
 */
public class TestUtils {

    /**
     * Parse JSON into slime structures and compare for equivalence.
     * Dumps both strings on stderr if they are not equivalent.
     * @throws IllegalArgumentException if jsonA or jsonB cannot be parsed.
     */
    public static boolean equivalent(String jsonA, String jsonB) {
        return equivalent(Json.of(jsonA), jsonB);
    }
    /** Compare JSON for equivalence. */
    public static boolean equivalent(Json jsonA, String jsonB) {
        return equivalent(jsonA, Json.of(jsonB));
    }
    /** Compare JSON for equivalence. */
    public static boolean equivalent(Json jsonA, Json jsonB) {
        boolean equal = jsonA.isEqualTo(jsonB);
        if (!equal) {
            // TODO: recursively compare jsonA and jsonB, printing
            // where and how they are different
            System.err.println("JSON values are not equivalent:");
            System.err.println("First:  " + jsonA.toJson(true));
            System.err.println("Second: " + jsonB.toJson(true));
        }
        return equal;
    }
}
