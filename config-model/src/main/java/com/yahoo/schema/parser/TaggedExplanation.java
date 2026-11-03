// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import com.yahoo.searchlib.ranking.features.FeatureNames;
import com.yahoo.searchlib.rankingexpression.Reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A rank profile explanation split into its main text and the explanations of inputs given by
 * javadoc style tags on the form "@input name explanation".
 *
 * As in javadoc, tags are only recognized in a section at the end of the explanation, which starts
 * at the first line beginning with a known tag. Lines inside fenced code blocks are never taken to be tags.
 * A tag's explanation continues until the next tag or the end of the explanation.
 *
 * @author bratseth
 */
public record TaggedExplanation(Optional<String> text, Map<Reference, String> inputs) {

    private static final Pattern tagPattern = Pattern.compile("^@([A-Za-z]\\w*)\\b(.*)$");
    private static final Pattern inputPattern = Pattern.compile("^\\s+(?:query\\(\\s*([A-Za-z_]\\w*)\\s*\\)|([A-Za-z_]\\w*))(?:\\s+(.*))?$",
                                                                Pattern.DOTALL);
    private static final String inputTag = "input";

    public TaggedExplanation {
        inputs = Collections.unmodifiableMap(inputs);
    }

    /** Parses an explanation which has already been cleaned by {@link Utils#explanationText}. */
    public static TaggedExplanation parse(String explanation) {
        List<String> lines = explanation.lines().toList();
        int tagSectionStart = tagSectionStart(lines);
        String text = String.join("\n", lines.subList(0, tagSectionStart)).strip();
        Map<Reference, String> inputs = new LinkedHashMap<>();
        for (var tag : tags(lines.subList(tagSectionStart, lines.size())))
            addInput(tag, inputs);
        return new TaggedExplanation(text.isEmpty() ? Optional.empty() : Optional.of(text), inputs);
    }

    private static int tagSectionStart(List<String> lines) {
        boolean inFence = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (isFence(line))
                inFence = ! inFence;
            else if ( ! inFence && tagName(line).filter(inputTag::equals).isPresent())
                return i;
        }
        return lines.size();
    }

    /** Returns the content of each tag (following the '@'), in order. */
    private static List<String> tags(List<String> tagSection) {
        List<String> tags = new ArrayList<>();
        StringBuilder current = null;
        boolean inFence = false;
        for (String line : tagSection) {
            String stripped = line.strip();
            if (isFence(stripped))
                inFence = ! inFence;
            Optional<String> tagName = inFence ? Optional.empty() : tagName(stripped);
            if (tagName.isPresent()) {
                if ( ! tagName.get().equals(inputTag))
                    throw new IllegalArgumentException("Unknown tag '@" + tagName.get() + "' in explanation: " +
                                                       "Only '@" + inputTag + "' is supported");
                if (current != null)
                    tags.add(current.toString());
                current = new StringBuilder(stripped.substring(1));
            }
            else {
                current.append("\n").append(line);
            }
        }
        if (current != null)
            tags.add(current.toString());
        return tags;
    }

    private static void addInput(String tag, Map<Reference, String> inputs) {
        Matcher matcher = inputPattern.matcher(tag.substring(inputTag.length()));
        if ( ! matcher.matches())
            throw new IllegalArgumentException("'@" + inputTag + "' in explanation must be followed by an input name " +
                                               "and its explanation, but got '@" + tag + "'");
        String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        Reference input = FeatureNames.asQueryFeature(name);
        String explanation = matcher.group(3) == null ? "" : Utils.explanationText(matcher.group(3));
        if (explanation.isEmpty())
            throw new IllegalArgumentException("'@" + inputTag + " " + name + "' in explanation has no explanation");
        if (inputs.containsKey(input))
            throw new IllegalArgumentException("'@" + inputTag + " " + name + "' is given more than once in explanation");
        inputs.put(input, explanation);
    }

    private static Optional<String> tagName(String strippedLine) {
        Matcher matcher = tagPattern.matcher(strippedLine);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static boolean isFence(String strippedLine) {
        return strippedLine.startsWith("```") || strippedLine.startsWith("~~~");
    }

}
