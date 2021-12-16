// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.bert;

import com.google.inject.Inject;
import com.yahoo.language.tools.Embed;
import com.yahoo.language.Language;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.Segmenter;
import com.yahoo.language.process.Tokenizer;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * An embedder to use with BERT models: Text is tokenized into tokens from a configured vocabulary,
 * and optionally returned as a 1-d dense tensor of token ids.
 *
 * @author bratseth
 */
public class BertEmbedder implements Embedder, Segmenter {

    private final Map<Language, Model> models;

    private final Tokenizer tokenizer;

    @Inject
    public BertEmbedder(BertConfig config) {
        this(new Builder(config));
    }

    private BertEmbedder(Builder builder) {
        super();
        this.tokenizer = new SimpleLinguistics().getTokenizer(); // always just split on spaces etc. and lowercase
        models = builder.getModels().entrySet()
                        .stream()
                        .map(e -> new Model(e.getKey(), e.getValue()))
                        .collect(Collectors.toUnmodifiableMap(m -> m.language, m -> m));
        if (models.isEmpty())
            throw new IllegalArgumentException("BertEmbedder requires at least one model configured");
    }

    /**
     * Segments the given text into token segments from the BERT vocabulary.
     *
     * @param text the text to segment. The text should be of a language using space-separated words.
     * @return the list of zero or more token ids resulting from segmenting the input text
     */
    @Override
    public List<String> segment(String text, Language language) {
        return resolveModelFrom(language).segment(text, tokenizer);
    }

    /**
     * Segments the given text into token segments from the BERT vocabulary and returns the token ids.
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
        throw new IllegalArgumentException("No BERT model for language " + language + " is configured");
    }

    public static class Builder {

        private final Map<Language, Path> models = new EnumMap<>(Language.class);

        public Builder() {
        }

        private Builder(BertConfig config) {
            for (BertConfig.Model model : config.model())
                addModel(Language.fromLanguageTag(model.language()), model.path());
        }

        public void addModel(Language language, Path model) {
            models.put(language, model);
        }

        /**
         * Adds the model that will be used if the language is unknown, OR only one model is specified.
         * The same as addModel(Language.UNKNOWN, model).
         */
        public BertEmbedder.Builder addDefaultModel(Path model) {
            addModel(Language.UNKNOWN, model);
            return this;
        }

        public Map<Language, Path> getModels() { return models; }

        public BertEmbedder build() {
            if (models.isEmpty()) throw new IllegalStateException("At least one model must be supplied");
            return new BertEmbedder(this);
        }

    }

}

