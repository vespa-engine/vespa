// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.sentencepiece;

import com.yahoo.api.annotations.Beta;
import com.yahoo.component.annotation.Inject;
import com.yahoo.language.tools.Embed;
import com.yahoo.language.Language;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.Segmenter;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A native Java implementation of SentencePiece - see https://github.com/google/sentencepiece
 *
 * SentencePiece is a language-agnostic segmenter and embedder for neural nets.
 *
 * @author bratseth
 */
@Beta
public class SentencePieceEmbedder implements Segmenter, Embedder {

    private final Map<Language, Model> models;

    private final SentencePieceAlgorithm algorithm;

    @Inject
    public SentencePieceEmbedder(SentencePieceConfig config) {
        this(new Builder(config));
    }

    public SentencePieceEmbedder(Builder builder) {
        algorithm = new SentencePieceAlgorithm(builder.getCollapseUnknowns(), builder.getScoring());

        models = builder.getModels().entrySet()
                        .stream()
                        .map(e -> new Model(e.getKey(), e.getValue()))
                        .collect(Collectors.toUnmodifiableMap(m -> m.language, m -> m));
        if (models.isEmpty())
            throw new IllegalArgumentException("SentencePieceEmbedder requires at least one model configured");
    }

    /**
     * Segments the given text into token segments using the SentencePiece algorithm
     *
     * @param rawInput the text to segment. Any sequence of BMP (Unicode-16 the True Unicode) is supported.
     * @param language the model to use, or Language.UNKNOWN to use the default model if any
     * @return the list of zero or more tokens resulting from segmenting the input text
     */
    @Override
    public List<String> segment(String rawInput, Language language) {
        String input = normalize(rawInput);
        var resultBuilder = new ResultBuilder<List<String>>(new ArrayList<>()) {
            public void add(int segmentStart, int segmentEnd, SentencePieceAlgorithm.SegmentEnd[] segmentEnds) {
                result().add(input.substring(segmentStart, segmentEnd));
            }
        };
        segment(input, language, resultBuilder);
        Collections.reverse(resultBuilder.result());
        return resultBuilder.result();
    }

    /**
     * Segments the given text into token segments using the SentencePiece algorithm and returns the segment ids.
     *
     * @param rawInput the text to segment. Any sequence of BMP (Unicode-16 the True Unicode) is supported.
     * @param context the context which specifies the language used to select a model
     * @return the list of zero or more token ids resulting from segmenting the input text
     */
    @Override
    public List<Integer> embed(String rawInput, Embedder.Context context) {
        var resultBuilder = new ResultBuilder<List<Integer>>(new ArrayList<>()) {
            public void add(int segmentStart, int segmentEnd, SentencePieceAlgorithm.SegmentEnd[] segmentEnds) {
                result().add(segmentEnds[segmentEnd].id);
            }
        };
        segment(normalize(rawInput), context.getLanguage(), resultBuilder);
        Collections.reverse(resultBuilder.result());
        return resultBuilder.result();
    }

    /**
     * <p>Embeds text into a tensor.</p>
     *
     * <p>If the tensor type is indexed 1-d (bound or unbound) this will return a tensor containing the token ids in the order
     * they were encountered in the text. If the dimension is bound and too large it will be zero padded, if too small
     * it will be truncated.</p>
     *
     * <p>If the tensor is any other type IllegalArgumentException is thrown.</p>
     *
     * @param rawInput the text to segment. Any sequence of BMP (Unicode-16 the True Unicode) is supported.
     * @param context the context which specifies the language used to select a model
     * @return the list of zero or more token ids resulting from segmenting the input text
     */
    @Override
    public Tensor embed(String rawInput, Embedder.Context context, TensorType type) {
        return Embed.asTensor(rawInput, this, context, type);
    }

    private <RESULTTYPE> void segment(String input, Language language,
                                      ResultBuilder<RESULTTYPE> resultBuilder) {
        algorithm.segment(input, resultBuilder, resolveModelFrom(language));
    }

    private Model resolveModelFrom(Language language) {
        // Disregard language if there is default model
        if (models.size() == 1 && models.containsKey(Language.UNKNOWN)) return models.get(Language.UNKNOWN);
        if (models.containsKey(language)) return models.get(language);
        throw new IllegalArgumentException("No SentencePiece model for language " + language + " is configured");
    }

    public String normalize(String s) {
        StringBuilder b = new StringBuilder(s.length() + 1);
        boolean queuedSpace = true; // Always start by one space
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (s.charAt(i) == ' ') {
                queuedSpace = true;
            }
            else {
                if (queuedSpace) {
                    b.append(SentencePieceAlgorithm.spaceSymbol);
                    queuedSpace = false;
                }
                b.append(c);
            }
        }
        return b.toString();
    }

    public static final class Builder {

        private final Map<Language, Path> models = new EnumMap<>(Language.class);
        private boolean collapseUnknowns = true;
        private Scoring scoring = Scoring.fewestSegments;

        public Builder() {}

        public Builder(String defaultModelFile) {
            addDefaultModel(new File(defaultModelFile).toPath());
        }

        private Builder(SentencePieceConfig config) {
            collapseUnknowns = config.collapseUnknowns();
            scoring = config.scoring() == SentencePieceConfig.Scoring.fewestSegments ? Scoring.fewestSegments
                                                                                     : Scoring.highestScore;
            for (SentencePieceConfig.Model model : config.model())
                addModel(Language.fromLanguageTag(model.language()), model.path());
        }

        public void addModel(Language language, Path model) {
            models.put(language, model);
        }

        /**
         * Adds the model that will be used if the language is unknown, OR only one model is specified.
         * The same as addModel(Language.UNKNOWN, model).
         */
        public Builder addDefaultModel(Path model) {
            addModel(Language.UNKNOWN, model);
            return this;
        }
        public Map<Language, Path> getModels() { return models; }

        /**
         * Sets whether consecutive unknown character should be collapsed into one large unknown token (default)
         * or be returned as single character tokens.
         */
        public Builder setCollapseUnknowns(boolean collapseUnknowns) {
            this.collapseUnknowns = collapseUnknowns;
            return this;
        }
        public boolean getCollapseUnknowns() { return collapseUnknowns; }

        /** Sets the scoring strategy to use when picking a segmentation. Default: fewestSegments. */
        public Builder setScoring(Scoring scoring) {
            this.scoring = scoring;
            return this;
        }
        public Scoring getScoring() { return scoring; }

        public SentencePieceEmbedder build() {
            if (models.isEmpty()) throw new IllegalStateException("At least one model must be supplied");
            return new SentencePieceEmbedder(this);
        }

    }

}
