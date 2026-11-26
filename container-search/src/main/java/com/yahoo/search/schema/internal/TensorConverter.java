// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema.internal;

import com.yahoo.language.Language;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.InvocationContext;
import com.yahoo.processing.request.Properties;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.text.Text;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.yahoo.language.process.Embedder.Modality.IMAGE;
import static com.yahoo.language.process.Embedder.Modality.TEXT;

/**
 * A class which knows how to convert an Object value to a tensor of a given type.
 *
 * @author bratseth
 * @author glebashnik
 */
public class TensorConverter {

    private static final Logger log = Logger.getLogger(TensorConverter.class.getName());

    private static final Pattern embedderArgumentAndQuotedTextRegexp = Pattern.compile("^([A-Za-z0-9_\\-.][A-Za-z0-9_@\\-.]*),\\s*([\"'].*[\"'])");
    private static final Pattern embedderArgumentAndReferenceRegexp = Pattern.compile("^([A-Za-z0-9_\\-.][A-Za-z0-9_@\\-.]*),\\s*(@.*)");
    private static final Pattern twoReferencesRegexp = Pattern.compile("^(@[^,]+),\\s*(@[^,]+)$");
    // A quoted text next to a reference, in either order: 'text', @image or @image, 'text'
    private static final Pattern quotedTextNextToReferenceRegexp =
            Pattern.compile("^(.*[\"']\\s*,\\s*@[^,\\s\"']+|@[^,\\s\"']+\\s*,\\s*[\"'].*)$", Pattern.DOTALL);
    // The media type of a data URL may have parameters, and a data URL has no whitespace before its data.
    // So a text such as "data:image/png files" is not a data URL.
    private static final String imageDataUrlPrefix = "^\\s*data:image/[^\\s,;]*(?:;[^\\s,;]+)*";
    // A data URL is case insensitive up to its data
    private static final Pattern base64ImageDataUrlRegexp = Pattern.compile(imageDataUrlPrefix + ";base64,(.*)$",
                                                                            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern imageDataUrlRegexp = Pattern.compile(imageDataUrlPrefix + ",",
                                                                      Pattern.CASE_INSENSITIVE);

    private final Map<String, Embedder> embedders;

    public TensorConverter(Map<String, Embedder> embedders) {
        this.embedders = embedders;
    }

    public Tensor convertTo(TensorType type, Object value, String key, Language language,
                            Map<String, String> contextValues, Properties properties, Optional<Instant> deadline) {
        var context = new Embedder.Context(key).setLanguage(language);
        deadline.ifPresent(instant -> context.setDeadline(InvocationContext.Deadline.of(instant)));
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

        boolean acceptsImage = embedder.supportedModalities().stream()
                                       .anyMatch(modalities -> modalities.contains(IMAGE));
        if (acceptsImage && quotedTextNextToReferenceRegexp.matcher(argument).matches())
            throw new IllegalArgumentException("embed() takes a text with an image as two references, as in " +
                                               "embed(embedder-id, @text, @image), " +
                                               "but got a quoted text next to a reference");

        Matcher references = twoReferencesRegexp.matcher(argument);
        List<String> values = embedder.supportedModalities().contains(Set.of(TEXT, IMAGE)) && references.matches()
                              ? List.of(resolveReference(references.group(1).strip(), contextValues, properties),
                                        resolveReference(references.group(2).strip(), contextValues, properties))
                              : List.of(resolve(argument, contextValues, properties));

        Optional<String> text = Optional.empty();
        Optional<Embedder.Image> image = Optional.empty();
        for (String value : values) {
            if (value.isBlank()) continue;
            Optional<Embedder.Image> imageOfValue = acceptsImage ? toImage(value) : Optional.empty();
            if (imageOfValue.isPresent()) {
                if (image.isPresent()) throw new IllegalArgumentException("embed() accepts one image, but got two");
                image = imageOfValue;
            } else {
                if (text.isPresent()) throw new IllegalArgumentException("embed() accepts one text, but got two");
                text = Optional.of(value);
            }
        }

        // Return zero tensor for empty input as some embedder implementations does support blank or empty strings.
        // A zero vector might be close to a document embedding with a completely different semantic meaning.
        // Ideally we should include an additional annotation in the query to indicate empty input.
        // The likelihood of searching on an empty input is low, so this is an acceptable workaround.
        if (text.isEmpty() && image.isEmpty()) {
            log.fine(() ->
                    Text.format("Input is blank, returning zero tensor of type %s using embedder '%s'", type, embedderId));
            return Tensor.generate(type, __ -> 0.0);
        }

        var input = new Embedder.Input(text, image);
        Optional<String> rejection = Embedder.rejectionReason(input.modalities(), embedder.supportedModalities());
        if (rejection.isPresent())
            throw new IllegalArgumentException("Embedder '" + embedderId + "' " + rejection.get());

        var context = embedderContext.copy().setEmbedderId(embedderId);
        if (image.isEmpty())
            return embedder.embed(text.get(), context, type);
        return embedder.embed(input, context, type);
    }

    /**
     * A query parameter is an untyped string.
     * Only a base64 data URL, such as 'data:image/jpeg;base64,/9j/...', marks the value as an image.
     */
    private static Optional<Embedder.Image> toImage(String value) {
        Matcher dataUrl = base64ImageDataUrlRegexp.matcher(value);
        if ( ! dataUrl.matches()) {
            if (imageDataUrlRegexp.matcher(value).lookingAt())
                throw new IllegalArgumentException("An argument of embed() is a data URL of an image " +
                                                   "without ';base64', but only base64 data URLs are supported");
            if (isBase64Image(value.strip()))
                throw new IllegalArgumentException("An argument of embed() is a base64 encoded image " +
                                                   "without the prefix 'data:image/[format];base64,'");
            return Optional.empty();
        }
        byte[] data;
        try {
            data = decodeBase64(dataUrl.group(1));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("An argument of embed() is a data URL whose data is not valid base64", e);
        }
        return Optional.of(new Embedder.Image(data));
    }

    private static boolean isBase64Image(String value) {
        // decodeBase64 reads a space as a '+'. A text such as "/9j/ starts a JPEG" then decodes to a JPEG.
        if (value.contains(" ")) return false;
        try {
            new Embedder.Image(decodeBase64(value));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * A URL query parameter without URL encoding turns each '+' into a space, and base64 has no space.
     * Tools such as GNU base64 wrap their output in lines, which Base64.getDecoder() rejects.
     */
    private static byte[] decodeBase64(String data) {
        return Base64.getDecoder().decode(data.replaceAll("[\\r\\n]", "").replace(' ', '+'));
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
