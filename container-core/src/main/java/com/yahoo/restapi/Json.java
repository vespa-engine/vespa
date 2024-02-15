package com.yahoo.restapi;

import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.slime.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.yahoo.slime.Type.ARRAY;
import static com.yahoo.slime.Type.STRING;

/**
 * A {@link Slime} wrapper that throws {@link RestApiException.BadRequest} on missing members or invalid types.
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

    public OptionalLong asOptionalLong() { return isMissing() ? OptionalLong.empty() : OptionalLong.of(asLong()); }
    public long asLong() { requireType(Type.LONG, Type.DOUBLE); return inspector.asLong(); }
    public long asLong(long defaultValue) {
        if (isMissing()) return defaultValue;
        return asLong();
    }

    public OptionalDouble asOptionalDouble() { return isMissing() ? OptionalDouble.empty() : OptionalDouble.of(asDouble()); }
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

    public List<Json> toList() {
        var list = new ArrayList<Json>(length());
        forEachEntry(json -> list.add(json));
        return List.copyOf(list);
    }

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

    private RestApiException.BadRequest createInvalidTypeException(Type... expected) {
        var expectedTypesString = Arrays.stream(expected).map(this::toString).collect(Collectors.joining("' or '", "'", "'"));
        var pathString = path.isEmpty() ? "JSON" : "JSON member '%s'".formatted(path);
        return new RestApiException.BadRequest(
                "Expected %s to be a %s but got '%s'"
                        .formatted(pathString, expectedTypesString, toString(inspector.type())));
    }

    private RestApiException.BadRequest createMissingMemberException() {
        return new RestApiException.BadRequest(path.isEmpty() ? "Missing JSON" : "Missing JSON member '%s'".formatted(path));
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
}
