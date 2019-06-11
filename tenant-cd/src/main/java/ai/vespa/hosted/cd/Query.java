package ai.vespa.hosted.cd;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Map.copyOf;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * An immutable query to send to a Vespa {@link Endpoint}, to receive a {@link Search}.
 *
 * @author jonmv
 */
public class Query {

    private final String rawQuery;
    private final Map<String, String> parameters;

    private Query(String rawQuery, Map<String, String> parameters) {
        this.rawQuery = rawQuery;
        this.parameters = parameters;
    }

    /** Creates a query with the given raw query part. */
    public static Query ofRaw(String rawQuery) {
        if (rawQuery.isBlank())
            throw new IllegalArgumentException("Query can not be blank.");

        return new Query(rawQuery,
                         Stream.of(rawQuery.split("&"))
                               .map(pair -> pair.split("="))
                               .collect(toUnmodifiableMap(pair -> pair[0], pair -> pair[1])));
    }

    /** Creates a query with the given name-value pairs. */
    public static Query ofParameters(Map<String, String> parameters) {
        if (parameters.isEmpty())
            throw new IllegalArgumentException("Parameters can not be empty.");

        return new Query(parameters.entrySet().stream()
                                   .map(entry -> entry.getKey() + "=" + entry.getValue())
                                   .collect(joining("&")),
                         copyOf(parameters));
    }

    /** Returns a copy of this with the given name-value pair added, potentially overriding any current value. */
    public Query withParameter(String name, String value) {
        return ofParameters(Stream.concat(parameters.entrySet().stream().filter(entry -> ! entry.getKey().equals(name)),
                                          Stream.of(Map.entry(name, value)))
                                  .collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    /** Returns the raw string representation of this query. */
    public String rawQuery() { return rawQuery; }

    /** Returns the parameters of this query. */
    public Map<String, String> parameters() { return parameters; }

    /** Returns the timeout parameter of the request, if one is set. */
    public Optional<Duration> timeout() {
        return Optional.ofNullable(parameters.get("timeout"))
                       .map(timeout -> Duration.of(Long.parseLong(timeout.replaceAll("\\s*m?s", "")),
                                                   timeout.contains("ms") ? ChronoUnit.MILLIS : ChronoUnit.SECONDS));
    }

}
