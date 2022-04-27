// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema.internal;

import com.yahoo.language.Language;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class which knows how to convert an Object value to a tensor of a given type.
 *
 * @author bratseth
 */
public class TensorConverter {

    private static final Pattern embedderArgumentRegexp = Pattern.compile("^([A-Za-z0-9_\\-.]+),\\s*([\"'].*[\"'])");

    private final Map<String, Embedder> embedders;

    public TensorConverter(Map<String, Embedder> embedders) {
        this.embedders = embedders;
    }

    public Tensor convertTo(TensorType type, String key, Object value, Language language) {
        var context = new Embedder.Context(key).setLanguage(language);
        Tensor tensor = toTensor(type, value, context);
        if (tensor == null) return null;
        if (! tensor.type().isAssignableTo(type))
            throw new IllegalArgumentException("Require a tensor of type " + type);
        return tensor;
    }

    private Tensor toTensor(TensorType type, Object value, Embedder.Context context) {
        if (value instanceof Tensor) return (Tensor)value;
        if (value instanceof String && isEmbed((String)value)) return embed((String)value, type, context);
        if (value instanceof String) return Tensor.from(type, (String)value);
        return null;
    }

    static boolean isEmbed(String value) {
        return value.startsWith("embed(");
    }

    private Tensor embed(String s, TensorType type, Embedder.Context embedderContext) {
        if ( ! s.endsWith(")"))
            throw new IllegalArgumentException("Expected any string enclosed in embed(), but the argument does not end by ')'");
        String argument = s.substring("embed(".length(), s.length() - 1);
        Embedder embedder;

        // Check if arguments specifies an embedder with the format embed(embedder, "text to encode")
        Matcher matcher = embedderArgumentRegexp.matcher(argument);
        if (matcher.matches()) {
            String embedderId = matcher.group(1);
            argument = matcher.group(2);
            if ( ! embedders.containsKey(embedderId)) {
                throw new IllegalArgumentException("Can't find embedder '" + embedderId + "'. " +
                                                   "Valid embedders are " + validEmbedders(embedders));
            }
            embedder = embedders.get(embedderId);
        } else if (embedders.size() == 0) {
            throw new IllegalStateException("No embedders provided");  // should never happen
        } else if (embedders.size() > 1) {
            throw new IllegalArgumentException("Multiple embedders are provided but no embedder id is given. " +
                                               "Valid embedders are " + validEmbedders(embedders));
        } else {
            embedder = embedders.entrySet().stream().findFirst().get().getValue();
        }

        return embedder.embed(removeQuotes(argument), embedderContext, type);
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

}
