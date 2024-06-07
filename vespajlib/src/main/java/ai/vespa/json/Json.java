package ai.vespa.json;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.slime.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.yahoo.slime.Type.ARRAY;
import static com.yahoo.slime.Type.STRING;

/**
 * A {@link Slime} wrapper that throws {@link InvalidJsonException} on missing members or invalid types.
 *
 * @author bjorncs
 */
public class Json implements Iterable<Json> {

    private final Inspector inspector;
    // Used to keep track of the path to the current node for error messages
    private final String path;

    public static Json of(Slime slime) { return of(slime.get()); }
    public static Json of(Inspector inspector) { return new Json(inspector, ""); }
    public static Json of(String json) { return of(SlimeUtils.jsonToSlime(json)); }

    private Json(Inspector inspector, String path) {
        this.inspector = Objects.requireNonNull(inspector);
        this.path = Objects.requireNonNull(path);
    }

    public Json f(String field) { return field(field); }
    public Json field(String field) {
        requireType(Type.OBJECT);
        return new Json(inspector.field(field), path.isEmpty() ? field : "%s.%s".formatted(path, field));
    }

    public Json a(int index) { return entry(index); }
    public Json entry(int index) {
        requireType(ARRAY);
        return new Json(inspector.entry(index), "%s[%d]".formatted(path, index));
    }

    public int length() { return inspector.children(); }
    public boolean has(String field) { return inspector.field(field).valid(); }
    public boolean isPresent() { return inspector.valid(); }
    public boolean isMissing() { return !isPresent(); }

    public Optional<String> asOptionalString() { return Optional.ofNullable(asString(null)); }
    public String asString() { requireType(STRING); return inspector.asString(); }
    public String asString(String defaultValue) {
        if (isMissing()) return defaultValue;
        return asString();
    }

    public Optional<Long> asOptionalLong() { return isMissing() ? Optional.empty() : Optional.of(asLong()); }
    public long asLong() { requireType(Type.LONG, Type.DOUBLE); return inspector.asLong(); }
    public long asLong(long defaultValue) {
        if (isMissing()) return defaultValue;
        return asLong();
    }

    public Optional<Double> asOptionalDouble() { return isMissing() ? Optional.empty() : Optional.of(asDouble()); }
    public double asDouble() { requireType(Type.LONG, Type.DOUBLE); return inspector.asDouble(); }
    public double asDouble(double defaultValue) {
        if (isMissing()) return defaultValue;
        return asDouble();
    }

    public Optional<Boolean> asOptionalBool() { return isMissing() ? Optional.empty() : Optional.of(asBool()); }
    public boolean asBool() { requireType(Type.BOOL); return inspector.asBool(); }
    public boolean asBool(boolean defaultValue) {
        if (isMissing()) return defaultValue;
        return asBool();
    }

    public Optional<Instant> asOptionalInstant() { return isMissing() ? Optional.empty() : Optional.of(asInstant()); }
    public Instant asInstant() {
        requireType(Type.STRING);
        try {
            return Instant.parse(asString());
        } catch (DateTimeParseException e) {
            throw new InvalidJsonException("Expected JSON member '%s' to be a valid timestamp: %s".formatted(path, e.getMessage()));
        }
    }
    public Instant asInstant(Instant defaultValue) {
        if (isMissing()) return defaultValue;
        return asInstant();
    }

    public List<Json> toList() {
        var list = new ArrayList<Json>(length());
        forEachEntry(json -> list.add(json));
        return List.copyOf(list);
    }

    public Stream<Json> stream() { return StreamSupport.stream(this.spliterator(), false); }

    public String toJson(boolean pretty) { return SlimeUtils.toJson(inspector, !pretty); }

    public boolean isString() { return inspector.type() == STRING; }
    public boolean isArray() { return inspector.type() == ARRAY; }
    public boolean isLong() { return inspector.type() == Type.LONG; }
    public boolean isDouble() { return inspector.type() == Type.DOUBLE; }
    public boolean isBool() { return inspector.type() == Type.BOOL; }
    public boolean isNumber() { return isLong() || isDouble(); }
    public boolean isObject() { return inspector.type() == Type.OBJECT; }

    @Override
    public Iterator<Json> iterator() {
        requireType(ARRAY);
        return new Iterator<>() {
            private int current = 0;
            @Override public boolean hasNext() { return current < length(); }
            @Override public Json next() { return entry(current++); }
        };
    }

    public void forEachField(BiConsumer<String, Json> consumer) {
        requireType(Type.OBJECT);
        inspector.traverse((ObjectTraverser) (name, inspector) -> {
            consumer.accept(name, field(name));
        });
    }

    public void forEachEntry(BiConsumer<Integer, Json> consumer) {
        requireType(ARRAY);
        for (int i = 0; i < length(); i++) {
            consumer.accept(i, entry(i));
        }
    }

    public void forEachEntry(Consumer<Json> consumer) {
        requireType(ARRAY);
        for (int i = 0; i < length(); i++) {
            consumer.accept(entry(i));
        }
    }

    private void requireType(Type... types) {
        requirePresent();
        if (!List.of(types).contains(inspector.type())) throw createInvalidTypeException(types);
    }

    private void requirePresent() { if (isMissing()) throw createMissingMemberException(); }

    private InvalidJsonException createInvalidTypeException(Type... expected) {
        var expectedTypesString = Arrays.stream(expected).map(this::toString)
                .collect(java.util.stream.Collectors.joining("' or '", "'", "'"));
        var pathString = path.isEmpty() ? "JSON" : "JSON member '%s'".formatted(path);
        return new InvalidJsonException(
                "Expected %s to be a %s but got '%s'"
                        .formatted(pathString, expectedTypesString, toString(inspector.type())));
    }

    private InvalidJsonException createMissingMemberException() {
        return new InvalidJsonException(path.isEmpty() ? "Missing JSON" : "Missing JSON member '%s'".formatted(path));
    }

    private String toString(Type type) {
        return switch (type) {
            case NIX -> "null";
            case BOOL -> "boolean";
            case LONG -> "integer";
            case DOUBLE -> "float";
            case STRING, DATA -> "string";
            case ARRAY -> "array";
            case OBJECT -> "object";
        };
    }

    @Override
    public String toString() {
        return "Json{" +
                "inspector=" + inspector +
                ", path='" + path + '\'' +
                '}';
    }

    /** Provides a fluent API for building a {@link Slime} instance. */
    public static class Builder {
        protected final Cursor cursor;

        public static Builder.Array newArray() { return new Builder.Array(new Slime().setArray()); }
        public static Builder.Object newObject() { return new Builder.Object(new Slime().setObject()); }
        public static Builder.Object existingSlimeObjectCursor(Cursor cursor) {
            if (cursor.type() != Type.OBJECT) throw new InvalidJsonException("Input is not an object");
            return new Builder.Object(cursor);
        }
        public static Builder.Array existingSlimeArrayCursor(Cursor cursor) {
            if (cursor.type() != Type.ARRAY) throw new InvalidJsonException("Input is not an array");
            return new Builder.Array(cursor);
        }

        private Builder(Cursor cursor) { this.cursor = cursor; }

        public static class Array extends Builder {
            private Array(Cursor cursor) { super(cursor); }

            public Builder.Array add(Builder.Array array) {
                SlimeUtils.copyArray(array.cursor, cursor.addArray()); return this;
            }
            public Builder.Array add(Builder.Object object) {
                SlimeUtils.copyObject(object.cursor, cursor.addObject()); return this;
            }
            public Builder.Array add(Json json) {
                SlimeUtils.addValue(json.inspector, cursor); return this;
            }
            public Builder.Array add(Json.Builder builder) { return add(builder.build()); }
            /** Add all values from {@code array} to this array. */
            public Builder.Array addAll(Json.Builder.Array array) {
                SlimeUtils.copyArray(array.cursor, cursor); return this;
            }

            /** Note: does not return {@code this}! */
            public Builder.Array addArray() { return new Array(cursor.addArray()); }
            /** Note: does not return {@code this}! */
            public Builder.Object addObject() { return new Object(cursor.addObject()); }

            public Builder.Array add(String value) { cursor.addString(value); return this; }
            public Builder.Array add(long value) { cursor.addLong(value); return this; }
            public Builder.Array add(double value) { cursor.addDouble(value); return this; }
            public Builder.Array add(boolean value) { cursor.addBool(value); return this; }
        }

        public static class Object extends Builder {
            private Object(Cursor cursor) { super(cursor); }

            public Builder.Object set(String field, Builder.Array array) {
                SlimeUtils.copyArray(array.cursor, cursor.setArray(field)); return this;
            }
            public Builder.Object set(String field, Builder.Object object) {
                SlimeUtils.copyObject(object.cursor, cursor.setObject(field)); return this;
            }
            public Builder.Object set(String field, Json json) {
                SlimeUtils.setObjectEntry(json.inspector, field, cursor); return this;
            }
            public Builder.Object set(String field, Json.Builder json) {
                SlimeUtils.setObjectEntry(json.build().inspector, field, cursor); return this;
            }
            /** Note: does not return {@code this}! */
            public Builder.Array setArray(String field) { return new Array(cursor.setArray(field)); }
            /** Note: does not return {@code this}! */
            public Builder.Object setObject(String field) { return new Object(cursor.setObject(field)); }

            public Builder.Object set(String field, String value) { cursor.setString(field, value); return this; }
            public Builder.Object set(String field, long value) { cursor.setLong(field, value); return this; }
            public Builder.Object set(String field, double value) { cursor.setDouble(field, value); return this; }
            public Builder.Object set(String field, boolean value) { cursor.setBool(field, value); return this; }
            public Builder.Object set(String field, BigDecimal value) {
                cursor.setString(field, value.stripTrailingZeros().toPlainString()); return this;
            }
            public Builder.Object set(String field, Instant timestamp) { cursor.setString(field, timestamp.toString()); return this; }
        }

        public Cursor slimeCursor() { return cursor; }
        public Json build() { return Json.of(cursor); }
    }

    public static class Collectors {
        private Collectors() {}
        /** @param accumululator Specify one of the 'add' overloads from {@link Builder.Array} */
        public static <T> Collector<T, Json.Builder.Array, Json> toArray(BiConsumer<Builder.Array, T> accumululator) {
            return new Collector<T, Builder.Array, Json>() {
                @Override public Supplier<Builder.Array> supplier() { return Json.Builder.Array::newArray; }
                @Override public BiConsumer<Builder.Array, T> accumulator() { return accumululator; }
                @Override public BinaryOperator<Builder.Array> combiner() { return Builder.Array::addAll; }
                @Override public Function<Builder.Array, Json> finisher() { return Builder.Array::build; }
                @Override public Set<Characteristics> characteristics() { return Set.of(); }
            };
        }
    }
}
