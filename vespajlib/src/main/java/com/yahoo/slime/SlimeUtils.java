// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Extra utilities/operations on slime trees.
 *
 * @author Ulf Lilleengen
 */
public class SlimeUtils {

    public static void copyObject(Inspector from, Cursor to) {
        if (from.type() != Type.OBJECT) {
            throw new IllegalArgumentException("Cannot copy object: " + from);
        }
        from.traverse((ObjectTraverser) (name, inspector) -> setObjectEntry(inspector, name, to));

    }

    public static void setObjectEntry(Inspector from, String name, Cursor to) {
        switch (from.type()) {
            case NIX -> to.setNix(name);
            case BOOL -> to.setBool(name, from.asBool());
            case LONG -> to.setLong(name, from.asLong());
            case DOUBLE -> to.setDouble(name, from.asDouble());
            case STRING -> to.setString(name, from.asString());
            case DATA -> to.setData(name, from.asData());
            case ARRAY -> copyArray(from, to.setArray(name));
            case OBJECT -> copyObject(from, to.setObject(name));
        }
    }

    public static void copyArray(Inspector from, Cursor to) {
        if (from.type() != Type.ARRAY) {
            throw new IllegalArgumentException("Cannot copy array: " + from);
        }
        from.traverse((ArrayTraverser) (i, inspector) -> addValue(inspector, to));
    }

    private static void addValue(Inspector from, Cursor to) {
        switch (from.type()) {
            case NIX -> to.addNix();
            case BOOL -> to.addBool(from.asBool());
            case LONG -> to.addLong(from.asLong());
            case DOUBLE -> to.addDouble(from.asDouble());
            case STRING -> to.addString(from.asString());
            case DATA -> to.addData(from.asData());
            case ARRAY -> copyArray(from, to.addArray());
            case OBJECT -> copyObject(from, to.addObject());
        }

    }

    public static byte[] toJsonBytes(Slime slime) throws IOException {
        return toJsonBytes(slime.get());
    }

    public static byte[] toJsonBytes(Inspector inspector) throws IOException {
        return toJsonBytes(inspector, true);
    }

    public static byte[] toJsonBytes(Inspector inspector, boolean compact) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new JsonFormat(compact ? 0 : 2).encode(baos, inspector);
        return baos.toByteArray();
    }

    public static String toJson(Slime slime) {
        return toJson(slime.get());
    }

    public static String toJson(Inspector inspector) {
        return toJson(inspector, true);
    }

    public static String toJson(Inspector inspector, boolean compact) {
        var outputStream = new ByteArrayOutputStream();
        var jsonFormat = new JsonFormat(compact ? 0 : 2);
        uncheck(() -> jsonFormat.encode(outputStream, inspector));
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    public static Slime jsonToSlime(byte[] json) {
        Slime slime = new Slime();
        new JsonDecoder().decode(slime, json);
        return slime;
    }

    public static Slime jsonToSlime(String json) {
        return jsonToSlime(json.getBytes(StandardCharsets.UTF_8));
    }

    /** Throws {@link JsonParseException} on invalid JSON. */
    public static Slime jsonToSlimeOrThrow(String json) {
        return jsonToSlimeOrThrow(json.getBytes(StandardCharsets.UTF_8));
    }

    public static Slime jsonToSlimeOrThrow(byte[] json) {
        Slime slime = new Slime();
        new JsonDecoder().decodeOrThrow(slime, json);
        return slime;
    }

    public static Instant instant(Inspector field) {
        return Instant.ofEpochMilli(field.asLong());
    }

    public static Duration duration(Inspector field) {
        return Duration.ofMillis(field.asLong());
    }

    public static Optional<String> optionalString(Inspector inspector) {
        return Optional.of(inspector.asString()).filter(s -> !s.isEmpty());
    }

    public static OptionalLong optionalLong(Inspector field) {
        return field.valid() ? OptionalLong.of(field.asLong()) : OptionalLong.empty();
    }

    public static OptionalInt optionalInteger(Inspector field) {
        return field.valid() ? OptionalInt.of((int) field.asLong()) : OptionalInt.empty();
    }

    public static OptionalDouble optionalDouble(Inspector field) {
        return field.valid() ? OptionalDouble.of(field.asDouble()) : OptionalDouble.empty();
    }

    public static Optional<Instant> optionalInstant(Inspector field) {
        return optionalLong(field).stream().mapToObj(Instant::ofEpochMilli).findFirst();
    }

    public static Optional<Duration> optionalDuration(Inspector field) {
        return optionalLong(field).stream().mapToObj(Duration::ofMillis).findFirst();
    }

    public static Iterator<Inspector> entriesIterator(Inspector inspector) {
        return new Iterator<>() {
            private int current = 0;
            @Override public boolean hasNext() { return current < inspector.entries(); }
            @Override public Inspector next() { return inspector.entry(current++); }
        };
    }

    /** Returns stream of entries for given inspector. If the inspector is not an array, empty stream is returned */
    public static Stream<Inspector> entriesStream(Inspector inspector) {
        int characteristics = Spliterator.NONNULL | Spliterator.SIZED | Spliterator.ORDERED;
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(entriesIterator(inspector),
                                                                        characteristics),
                                    false);
    }

}
