// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.language.huggingface;

import com.yahoo.api.annotations.Beta;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.language.Language;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.Segmenter;
import com.yahoo.language.tools.Embed;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * {@link Embedder}/{@link Segmenter} using Deep Java Library's HuggingFace Tokenizer.
 *
 * @author bjorncs
 */
@Beta
public class HuggingFaceTokenizer extends AbstractComponent implements Embedder, Segmenter, AutoCloseable {

    private final Map<Language, ai.djl.huggingface.tokenizers.HuggingFaceTokenizer> models = new EnumMap<>(Language.class);

    @Inject public HuggingFaceTokenizer(HuggingFaceTokenizerConfig cfg) { this(new Builder(cfg)); }

    private HuggingFaceTokenizer(Builder b) {
        var original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(HuggingFaceTokenizer.class.getClassLoader());
        try {
            b.models.forEach((language, path) -> {
                models.put(language,
                           uncheck(() -> {
                               var hfb = ai.djl.huggingface.tokenizers.HuggingFaceTokenizer.builder()
                                       .optTokenizerPath(path)
                                       .optAddSpecialTokens(b.addSpecialTokens != null ? b.addSpecialTokens : true);
                               if (b.maxLength != null) hfb.optMaxLength(b.maxLength);
                               if (b.truncation != null) hfb.optTruncation(b.truncation);
                               return hfb.build();
                           }));
            });
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Override
    public List<Integer> embed(String text, Context ctx) {
        var encoding = resolve(ctx.getLanguage()).encode(text);
        return Arrays.stream(encoding.getIds()).mapToInt(Math::toIntExact).boxed().toList();
    }

    @Override
    public Tensor embed(String text, Context ctx, TensorType type) {
        return Embed.asTensor(text, this, ctx, type);
    }

    @Override
    public List<String> segment(String input, Language language) {
        return List.of(resolve(language).encode(input).getTokens());
    }

    @Override
    public String decode(List<Integer> tokens, Context ctx) {
        return resolve(ctx.getLanguage()).decode(toArray(tokens));
    }

    public Encoding encode(String text) { return encode(text, Language.UNKNOWN); }
    public Encoding encode(String text, Language language) { return Encoding.from(resolve(language).encode(text)); }
    public String decode(List<Long> tokens) { return decode(tokens, Language.UNKNOWN); }
    public String decode(List<Long> tokens, Language language) { return resolve(language).decode(toArray(tokens)); }

    @Override public void close() { models.forEach((__, model) -> model.close()); }
    @Override public void deconstruct() { close(); }

    private ai.djl.huggingface.tokenizers.HuggingFaceTokenizer resolve(Language language) {
        // Disregard language if there is default model
        if (models.size() == 1 && models.containsKey(Language.UNKNOWN)) return models.get(Language.UNKNOWN);
        if (models.containsKey(language)) return models.get(language);
        throw new IllegalArgumentException("No model for language " + language);
    }

    private static long[] toArray(Collection<? extends Number> c) { return c.stream().mapToLong(Number::longValue).toArray(); }

    public static final class Builder {
        private final Map<Language, Path> models = new EnumMap<>(Language.class);
        private Boolean addSpecialTokens;
        private Integer maxLength;
        private Boolean truncation;

        public Builder() {}
        public Builder(HuggingFaceTokenizerConfig cfg) {
            for (var model : cfg.model())
                addModel(Language.fromLanguageTag(model.language()), model.path());
            addSpecialTokens(cfg.addSpecialTokens());
            if (cfg.maxLength() != -1) setMaxLength(cfg.maxLength());
            if (cfg.truncation()) setTruncation(true);
        }

        public Builder addModel(Language lang, Path path) { models.put(lang, path); return this; }
        public Builder addDefaultModel(Path path) { return addModel(Language.UNKNOWN, path); }
        public Builder addSpecialTokens(boolean enabled) { addSpecialTokens = enabled; return this; }
        public Builder setMaxLength(int length) { maxLength = length; return this; }
        public Builder setTruncation(boolean enabled) { truncation = enabled; return this; }
        public HuggingFaceTokenizer build() { return new HuggingFaceTokenizer(this); }
    }

}