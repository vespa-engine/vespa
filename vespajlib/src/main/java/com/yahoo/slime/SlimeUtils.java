// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    private static void setObjectEntry(Inspector from, String name, Cursor to) {
        switch (from.type()) {
            case NIX:
                to.setNix(name);
                break;
            case BOOL:
                to.setBool(name, from.asBool());
                break;
            case LONG:
                to.setLong(name, from.asLong());
                break;
            case DOUBLE:
                to.setDouble(name, from.asDouble());
                break;
            case STRING:
                to.setString(name, from.asString());
                break;
            case DATA:
                to.setData(name, from.asData());
                break;
            case ARRAY:
                Cursor array = to.setArray(name);
                copyArray(from, array);
                break;
            case OBJECT:
                Cursor object = to.setObject(name);
                copyObject(from, object);
                break;
        }
    }

    private static void copyArray(Inspector from, Cursor to) {
        from.traverse((ArrayTraverser) (i, inspector) -> addValue(inspector, to));
    }

    private static void addValue(Inspector from, Cursor to) {
        switch (from.type()) {
            case NIX:
                to.addNix();
                break;
            case BOOL:
                to.addBool(from.asBool());
                break;
            case LONG:
                to.addLong(from.asLong());
                break;
            case DOUBLE:
                to.addDouble(from.asDouble());
                break;
            case STRING:
                to.addString(from.asString());
                break;
            case DATA:
                to.addData(from.asData());
                break;
            case ARRAY:
                Cursor array = to.addArray();
                copyArray(from, array);
                break;
            case OBJECT:
                Cursor object = to.addObject();
                copyObject(from, object);
                break;
        }

    }

    public static byte[] toJsonBytes(Slime slime) throws IOException {
        return toJsonBytes(slime.get());
    }

    public static byte[] toJsonBytes(Inspector inspector) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new JsonFormat(true).encode(baos, inspector);
        return baos.toByteArray();
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
