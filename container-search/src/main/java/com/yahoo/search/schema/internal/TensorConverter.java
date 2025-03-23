// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema.internal;

import com.yahoo.language.Language;
import com.yahoo.language.process.Embedder;
import com.yahoo.processing.request.Properties;
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

    private static final Pattern embedderArgumentAndQuotedTextRegexp = Pattern.compile("^([A-Za-z0-9_@\\-.]+),\\s*([\"'].*[\"'])");
    private static final Pattern embedderArgumentAndReferenceRegexp = Pattern.compile("^([A-Za-z0-9_@\\-.]+),\\s*(@.*)");

    private final Map<String, Embedder> embedders;

    public TensorConverter(Map<String, Embedder> embedders) {
        this.embedders = embedders;
    }

    public Tensor convertTo(TensorType type, String key, Object value, Language language,
                            Map<String, String> contextValues, Properties properties) {
        var context = new Embedder.Context(key).setLanguage(language);
        Tensor tensor = toTensor(type, value, context, contextValues, properties);
        if (tensor == null) return null;
        if (! tensor.type().isAssignableTo(type))
            throw new IllegalArgumentException("Require a tensor of type " + type);
        return tensor;
    }

    private Tensor toTensor(TensorType type, Object value, Embedder.Context context, Map<String, String> contextValues,
                            Properties properties) {
        if (value instanceof Tensor) return (Tensor)value;
        if (value instanceof String && isEmbed((String)value)) return embed((String)value, type, context, contextValues, properties);
        if (value instanceof String) return Tensor.from(type, (String)value);
        return null;
    }

    static boolean isEmbed(String value) {
        return value.startsWith("embed(");
    }

    private Tensor embed(String s, TensorType type, Embedder.Context embedderContext, Map<String, String> contextValues,
                         Properties properties) {
        if ( ! s.endsWith(")"))
            throw new IllegalArgumentException("Expected any string enclosed in embed(), but the argument does not end by ')'");
        String argument = s.substring("embed(".length(), s.length() - 1);
        Embedder embedder;
        String embedderId;

        // Check if arguments specifies an embedder with the format embed(embedder, "text to encode")
        Matcher matcher;
        if (( matcher = embedderArgumentAndQuotedTextRegexp.matcher(argument)).matches()) {
            embedderId = matcher.group(1);
            embedder = requireEmbedder(embedderId);
            argument = matcher.group(2);
        } else if (( matcher = embedderArgumentAndReferenceRegexp.matcher(argument)).matches()) {
                embedderId = matcher.group(1);
                embedder = requireEmbedder(embedderId);
                argument = matcher.group(2);
        } else if (embedders.isEmpty()) {
            throw new IllegalArgumentException("No embedders provided");  // should never happen
        } else if (embedders.size() > 1) {
            String usage = "Usage: embed(embedder-id, 'text'). " + embedderIds(embedders);
            if (! argument.contains("\"") && ! argument.contains("'"))
                throw new IllegalArgumentException("Multiple embedders are provided but the string to embed is not quoted. " + usage);
            else
                throw new IllegalArgumentException("Multiple embedders are provided but no embedder id is given. " + usage);
        } else {
            var entry = embedders.entrySet().stream().findFirst().get();
            embedderId = entry.getKey();
            embedder = entry.getValue();
        }
        return embedder.embed(resolve(argument, contextValues, properties), embedderContext.copy().setEmbedderId(embedderId), type);
    }

    private Embedder requireEmbedder(String embedderId) {
        if ( ! embedders.containsKey(embedderId))
            throw new IllegalArgumentException("Can't find embedder '" + embedderId + "'. " + embedderIds(embedders));
        return embedders.get(embedderId);
    }

    private static String resolve(String s, Map<String, String> contextValues, Properties properties) {
        if (s.startsWith("'") && s.endsWith("'"))
            return s.substring(1, s.length() - 1);
        if (s.startsWith("\"") && s.endsWith("\""))
            return s.substring(1, s.length() - 1);
        if (s.startsWith("@"))
            return resolveReference(s, contextValues, properties);
        return s;
    }

    private static String resolveReference(String s, Map<String, String> contextValues, Properties properties) {
        String referenceKey = s.substring(1);
        Object referencedValue = properties.get(referenceKey, contextValues);
        if (referencedValue == null)
            throw new IllegalArgumentException("Could not resolve query parameter reference '" + referenceKey +
                                               "' used in an embed() argument");
        return referencedValue.toString();
    }

    private static String embedderIds(Map<String, Embedder> embedders) {
        List<String> embedderIds = new ArrayList<>();
        embedders.forEach((key, value) -> embedderIds.add("'" + key + "'"));
        embedderIds.sort(null);
        return "Available embedder ids are " + String.join(", ", embedderIds) + ".";
    }

}
