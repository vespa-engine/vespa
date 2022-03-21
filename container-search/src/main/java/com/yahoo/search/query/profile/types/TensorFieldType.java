// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.types;

import com.yahoo.language.process.Embedder;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tensor field type in a query profile
 *
 * @author bratseth
 */
public class TensorFieldType extends FieldType {

    private static final Pattern embedderArgumentRegexp = Pattern.compile("^([A-Za-z0-9_\\-.]+),\\s*([\"'].*[\"'])");

    private final TensorType type;

    /** Creates a tensor field type with information about the kind of tensor this will hold */
    public TensorFieldType(TensorType type) {
        this.type = type;
    }

    /** Returns information about the type of tensor this will hold */
    @Override
    public TensorType asTensorType() { return type; }

    @Override
    public Class getValueClass() { return Tensor.class; }

    @Override
    public String stringValue() { return type.toString(); }

    @Override
    public String toString() { return "field type " + stringValue(); }

    @Override
    public String toInstanceDescription() { return "a tensor"; }

    @Override
    public Object convertFrom(Object o, QueryProfileRegistry registry) {
        return convertFrom(o, ConversionContext.empty());
    }

    @Override
    public Object convertFrom(Object o, ConversionContext context) {
        if (o instanceof Tensor) return o;
        if (o instanceof String && ((String)o).startsWith("embed(")) return encode((String)o, context);
        if (o instanceof String) return Tensor.from(type, (String)o);
        return null;
    }

    private Tensor encode(String s, ConversionContext context) {
        if ( ! s.endsWith(")"))
            throw new IllegalArgumentException("Expected any string enclosed in embed(), but the argument does not end by ')'");
        String argument = s.substring("embed(".length(), s.length() - 1);
        Embedder embedder;

        // Check if arguments specifies an embedder with the format embed(embedder, "text to encode")
        Matcher matcher = embedderArgumentRegexp.matcher(argument);
        if (matcher.matches()) {
            String embedderId = matcher.group(1);
            argument = matcher.group(2);
            if (!context.embedders().containsKey(embedderId)) {
                throw new IllegalArgumentException("Can't find embedder '" + embedderId + "'. " +
                        "Valid embedders are " + validEmbedders(context.embedders()));
            }
            embedder = context.embedders().get(embedderId);
        } else if (context.embedders().size() == 0) {
            throw new IllegalStateException("No embedders provided");  // should never happen
        } else if (context.embedders().size() > 1) {
            throw new IllegalArgumentException("Multiple embedders are provided but no embedder id is given. " +
                    "Valid embedders are " + validEmbedders(context.embedders()));
        } else {
            embedder = context.embedders().entrySet().stream().findFirst().get().getValue();
        }

        return embedder.embed(removeQuotes(argument), toEmbedderContext(context), type);
    }

    private static String removeQuotes(String s) {
        if (s.startsWith("'") && s.endsWith("'")) {
            return s.substring(1, s.length() - 1);
        }
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String validEmbedders(Map<String, Embedder> embedders) {
        List<String> embedderIds = new ArrayList<>();
        embedders.forEach((key, value) -> embedderIds.add(key));
        embedderIds.sort(null);
        return String.join(",", embedderIds);
    }

    private Embedder.Context toEmbedderContext(ConversionContext context) {
        return new Embedder.Context(context.destination()).setLanguage(context.language());
    }

    public static TensorFieldType fromTypeString(String s) {
        return new TensorFieldType(TensorType.fromSpec(s));
    }

}
