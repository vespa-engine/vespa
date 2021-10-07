// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Per-operation feed parameters
 *
 * @author bjorncs
 * @author jonmv
 */
public class OperationParameters {

    static final OperationParameters empty = new OperationParameters(false, null, null, null, 0);

    private final boolean create;
    private final String condition;
    private final Duration timeout;
    private final String route;
    private final int tracelevel;

    private OperationParameters(boolean create, String condition, Duration timeout, String route, int tracelevel) {
        this.create = create;
        this.condition = condition;
        this.timeout = timeout;
        this.route = route;
        this.tracelevel = tracelevel;
    }

    public static OperationParameters empty() { return empty; }

    public OperationParameters createIfNonExistent(boolean create) {
        return new OperationParameters(create, condition, timeout, route, tracelevel);
    }

    public OperationParameters testAndSetCondition(String condition) {
        if (condition.isEmpty())
            throw new IllegalArgumentException("TestAndSetCondition must be non-empty");

        return new OperationParameters(create, condition, timeout, route, tracelevel);
    }

    public OperationParameters timeout(Duration timeout) {
        if (timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("Timeout must be positive, but was " + timeout);

        return new OperationParameters(create, condition, timeout, route, tracelevel);
    }

    public OperationParameters route(String route) {
        if (route.isEmpty())
            throw new IllegalArgumentException("Route must be non-empty");

        return new OperationParameters(create, condition, timeout, route, tracelevel);
    }

    public OperationParameters tracelevel(int tracelevel) {
        if (tracelevel < 1 || tracelevel > 9)
            throw new IllegalArgumentException("Tracelevel must be in [1, 9]");

        return new OperationParameters(create, condition, timeout, route, tracelevel);
    }

    public boolean createIfNonExistent() { return create; }
    public Optional<String> testAndSetCondition() { return Optional.ofNullable(condition); }
    public Optional<Duration> timeout() { return Optional.ofNullable(timeout); }
    public Optional<String> route() { return Optional.ofNullable(route); }
    public OptionalInt tracelevel() { return tracelevel == 0 ? OptionalInt.empty() : OptionalInt.of(tracelevel); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OperationParameters that = (OperationParameters) o;
        return create == that.create && tracelevel == that.tracelevel && Objects.equals(condition, that.condition) && Objects.equals(timeout, that.timeout) && Objects.equals(route, that.route);
    }

    @Override
    public int hashCode() {
        return Objects.hash(create, condition, timeout, route, tracelevel);
    }

    @Override
    public String toString() {
        return "OperationParameters{" +
               "create=" + create +
               ", condition='" + condition + '\'' +
               ", timeout=" + timeout +
               ", route='" + route + '\'' +
               ", tracelevel=" + tracelevel +
               '}';
    }

}
