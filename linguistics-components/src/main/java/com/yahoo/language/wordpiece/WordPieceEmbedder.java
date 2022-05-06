// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.wordpiece;

import com.yahoo.component.annotation.Inject;
import com.yahoo.language.tools.Embed;
import com.yahoo.language.Language;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.Segmenter;
import com.yahoo.language.process.Tokenizer;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.language.wordpiece.WordPieceConfig;

import java.io.File;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * An implementation of the WordPiece embedder, usually used with BERT models,
 * see https://arxiv.org/pdf/1609.08144v2.pdf
 * Text is tokenized into tokens from a configured vocabulary,
 * and optionally returned as a 1-d dense tensor of token ids.
 *
 * @author bratseth
 */
public class WordPieceEmbedder implements Embedder, Segmenter {

    private final Map<Language, Model> models;

    private final Tokenizer tokenizer;

    @Inject
    public WordPieceEmbedder(WordPieceConfig config) {
        this(new Builder(config));
    }

    private WordPieceEmbedder(Builder builder) {
        super();
        this.tokenizer = new SimpleLinguistics().getTokenizer(); // always just split on spaces etc. and lowercase
        models = builder.getModels().entrySet()
                        .stream()
                        .map(e -> new Model(builder.getSubwordPrefix(), e.getKey(), e.getValue()))
                        .collect(Collectors.toUnmodifiableMap(m -> m.language(), m -> m));
        if (models.isEmpty())
            throw new IllegalArgumentException("WordPieceEmbedder requires at least one model configured");
    }

    /**
     * Segments the given text into token segments from the WordPiece vocabulary.
     *
     * @param text the text to segment. The text should be of a language using space-separated words.
     * @return the list of zero or more token ids resulting from segmenting the input text
     */
    @Override
    public List<String> segment(String text, Language language) {
        return resolveModelFrom(language).segment(text, tokenizer);
    }

    /**
     * Segments the given text into token segments from the WordPiece vocabulary and returns the token ids.
     *
     * @param text the text to segment. The text should be of a language using space-separated words.
     * @param context the context which specifies the language used to select a model
     * @return the list of zero or more token ids resulting from segmenting the input text
     */
    @Override
    public List<Integer> embed(String text, Context context) {
        return resolveModelFrom(context.getLanguage()).embed(text, tokenizer);
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
     * @param text the text to segment. The text should be of a language using space-separated words.
     * @param context the context which specifies the language used to select a model
     * @return the list of zero or more token ids resulting from segmenting the input text
     */
    @Override
    public Tensor embed(String text, Context context, TensorType type) {
        return Embed.asTensor(text, this, context, type);
    }

    private Model resolveModelFrom(Language language) {
        // Disregard language if there is default model
        if (models.size() == 1 && models.containsKey(Language.UNKNOWN)) return models.get(Language.UNKNOWN);
        if (models.containsKey(language)) return models.get(language);
        throw new IllegalArgumentException("No WordPiece model for language " + language + " is configured");
    }

    public static final class Builder {

        private String subwordPrefix = "##";
        private final Map<Language, Path> models = new EnumMap<>(Language.class);

        public Builder() {}

        public Builder(String defaultModelFile) {
            addDefaultModel(new File(defaultModelFile).toPath());
        }

        private Builder(WordPieceConfig config) {
            this.subwordPrefix = config.subwordPrefix();
            for (WordPieceConfig.Model model : config.model())
                addModel(Language.fromLanguageTag(model.language()), model.path());
        }

        public Builder setSubwordPrefix(String prefix) {
            this.subwordPrefix = subwordPrefix;
            return this;
        }

        public String getSubwordPrefix() { return subwordPrefix; }

        public void addModel(Language language, Path model) {
            models.put(language, model);
        }

        /**
         * Adds the model that will be used if the language is unknown, OR only one model is specified.
         * The same as addModel(Language.UNKNOWN, model).
         */
        public WordPieceEmbedder.Builder addDefaultModel(Path model) {
            addModel(Language.UNKNOWN, model);
            return this;
        }

        public Map<Language, Path> getModels() { return models; }

        public WordPieceEmbedder build() {
            if (models.isEmpty()) throw new IllegalStateException("At least one model must be supplied");
            return new WordPieceEmbedder(this);
        }

    }

}

