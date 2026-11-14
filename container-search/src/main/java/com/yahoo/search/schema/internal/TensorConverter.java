// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema.internal;

import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.language.Language;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.InvocationContext;
import com.yahoo.language.provider.DefaultEmbedderProvider;
import com.yahoo.processing.request.Properties;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.text.Text;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A class which knows how to convert an Object value to a tensor of a given type.
 *
 * @author bratseth
 */
public class TensorConverter {

    private static final Logger log = Logger.getLogger(TensorConverter.class.getName());

    private static final Pattern embedderArgumentAndQuotedTextRegexp = Pattern.compile("^([A-Za-z0-9_@\\-.]+),\\s*([\"'].*[\"'])");
    private static final Pattern embedderArgumentAndReferenceRegexp = Pattern.compile("^([A-Za-z0-9_@\\-.]+),\\s*(@.*)");

    private final Map<String, Embedder> embedders;

    public TensorConverter(Map<String, Embedder> embedders) {
        this.embedders = embedders;
    }

    /** Returns the given embedders as a map indexed by embedder id. */
    public static Map<String, Embedder> toMap(ComponentRegistry<Embedder> embedders) {
        var map = embedders.allComponentsById().entrySet().stream()
                           .collect(Collectors.toMap(e -> e.getKey().stringValue(), Map.Entry::getValue));
        if (map.size() > 1) {
            map.remove(DefaultEmbedderProvider.class.getName());
            // Ideally, this should be handled by dependency injection, however for now this workaround is necessary.
        }
        return Collections.unmodifiableMap(map);
    }

    public Tensor convertTo(TensorType type, String key, Object value, Language language,
                            Map<String, String> contextValues, Properties properties, Instant deadline) {
        Tensor tensor = toTensor(type, value, embedderContext(key, language, deadline), contextValues, properties);
        if (tensor == null) return null;
        return requireAssignableTo(type, tensor);
    }

    /**
     * Embeds a text as specified by an input expression on the form embed(@input) or embed(embedder-id, @input),
     * where @input is the text.
     */
    public Tensor embedInput(String inputExpression, String text, TensorType type, String key, Language language,
                             Instant deadline) {
        if ( ! isEmbed(inputExpression) || ! inputExpression.endsWith(")"))
            throw new IllegalArgumentException("Expected an input expression on the form embed([embedder-id, ]@input), " +
                                               "but got '" + inputExpression + "'");
        String argument = inputExpression.substring("embed(".length(), inputExpression.length() - 1);
        int comma = argument.indexOf(',');
        String embedderId;
        Embedder embedder;
        if (comma >= 0) {
            embedderId = argument.substring(0, comma).strip();
            embedder = requireEmbedder(embedderId);
        }
        else {
            var entry = requireSingleEmbedder("The embedder to use is not specified by '" + inputExpression + "'.");
            embedderId = entry.getKey();
            embedder = entry.getValue();
        }
        return requireAssignableTo(type, embed(text, embedderId, embedder, type, embedderContext(key, language, deadline)));
    }

    private static Embedder.Context embedderContext(String key, Language language, Instant deadline) {
        var context = new Embedder.Context(key).setLanguage(language);
        if (deadline != null) {
            context.setDeadline(InvocationContext.Deadline.of(deadline));
        }
        return context;
    }

    private static Tensor requireAssignableTo(TensorType type, Tensor tensor) {
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

    /** Returns whether this value is an embed(...) expression. */
    public static boolean isEmbed(String value) {
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
        } else {
            String usage = "Usage: embed(embedder-id, 'text'). " + embedderIds(embedders);
            var entry = requireSingleEmbedder(! argument.contains("\"") && ! argument.contains("'")
                                              ? "Multiple embedders are provided but the string to embed is not quoted. " + usage
                                              : "Multiple embedders are provided but no embedder id is given. " + usage);
            embedderId = entry.getKey();
            embedder = entry.getValue();
        }
        return embed(resolve(argument, contextValues, properties), embedderId, embedder, type, embedderContext);
    }

    private Map.Entry<String, Embedder> requireSingleEmbedder(String multipleEmbeddersMessage) {
        if (embedders.isEmpty())
            throw new IllegalArgumentException("No embedders provided");  // should never happen
        if (embedders.size() > 1)
            throw new IllegalArgumentException(multipleEmbeddersMessage);
        return embedders.entrySet().stream().findFirst().get();
    }

    private static Tensor embed(String text, String embedderId, Embedder embedder, TensorType type,
                                Embedder.Context embedderContext) {
        // Return zero tensor for empty input text as some embedder implementations does support blank or empty strings.
        // A zero vector might be close to a document embedding with a completely different semantic meaning.
        // Ideally we should include an additional annotation in the query to indicate empty input.
        // The likelihood of searching on an empty input text is low, so this is an acceptable workaround.
        if (text.isBlank()) {
            log.fine(() ->
                    Text.format("Input text is blank, returning zero tensor of type %s using embedder '%s'", type, embedderId));
            return Tensor.generate(type, __ -> 0.0);
        }

        return embedder.embed(text, embedderContext.copy().setEmbedderId(embedderId), type);
    }

    private Embedder requireEmbedder(String embedderId) {
        if ( ! embedders.containsKey(embedderId))
            throw new IllegalArgumentException("Can't find embedder '" + embedderId + "'. " + embedderIds(embedders));
        return embedders.get(embedderId);
    }

    /**
     * Resolves the text given by an argument: A quoted string is unquoted,
     * a reference (@name) is replaced by the value of the referenced property, and any other string is returned as-is.
     */
    public static String resolve(String s, Map<String, String> contextValues, Properties properties) {
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
            throw new IllegalArgumentException("Could not resolve query parameter reference '" + referenceKey + "'");
        return referencedValue.toString();
    }

    private static String embedderIds(Map<String, Embedder> embedders) {
        List<String> embedderIds = new ArrayList<>();
        embedders.forEach((key, value) -> embedderIds.add("'" + key + "'"));
        embedderIds.sort(null);
        return "Available embedder ids are " + String.join(", ", embedderIds) + ".";
    }

}
