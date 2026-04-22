// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.completion;

import com.yahoo.api.annotations.Beta;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A prompt consisting of ordered named sections that can still be rendered as a flat string.
 *
 * @author gdonovan
 */
@Beta
public class StructuredPrompt extends Prompt {

    private final List<Section> sections;
    private final String separator;

    private StructuredPrompt(List<Section> sections, String separator) {
        this.sections = List.copyOf(sections);
        this.separator = Objects.requireNonNull(separator);
        if (this.sections.isEmpty()) {
            throw new IllegalArgumentException("StructuredPrompt requires at least one section");
        }
    }

    public List<Section> sections() { return sections; }

    public String separator() { return separator; }

    @Override
    public String asString() {
        return sections.stream()
                       .map(Section::text)
                       .collect(Collectors.joining(separator));
    }

    @Override
    public StructuredPrompt append(String text) {
        Objects.requireNonNull(text);
        var updated = new ArrayList<>(sections);
        var lastSection = updated.remove(updated.size() - 1);
        updated.add(lastSection.append(text));
        return new StructuredPrompt(updated, separator);
    }

    /** Returns a new prompt with a new section appended at the end. */
    public StructuredPrompt append(String name, String text) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(text);
        var updated = new ArrayList<>(sections);
        updated.add(new Section(name, text));
        return new StructuredPrompt(updated, separator);
    }

    @Override
    public String toString() {
        return asString();
    }

    public static StructuredPrompt of(List<Section> sections) {
        return new StructuredPrompt(sections, "\n");
    }

    public static Builder builder() {
        return new Builder();
    }

    public record Section(String name, String text) {

        public Section {
            name = Objects.requireNonNull(name);
            text = Objects.requireNonNull(text);
        }

        private Section append(String suffix) {
            return new Section(name, text + suffix);
        }
    }

    public static class Builder {

        private final List<Section> sections = new ArrayList<>();
        private String separator = "\n";

        public Builder add(String name, String text) {
            sections.add(new Section(name, text));
            return this;
        }

        public Builder separator(String separator) {
            this.separator = Objects.requireNonNull(separator);
            return this;
        }

        public StructuredPrompt build() {
            return new StructuredPrompt(sections, separator);
        }
    }
}
