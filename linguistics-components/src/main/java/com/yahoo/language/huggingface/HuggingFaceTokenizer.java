// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.language.huggingface;

import ai.djl.huggingface.tokenizers.jni.LibUtils;
import ai.djl.huggingface.tokenizers.jni.TokenizersLibrary;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.language.Language;
import com.yahoo.language.huggingface.ModelInfo.PaddingStrategy;
import com.yahoo.language.huggingface.ModelInfo.TruncationStrategy;
import com.yahoo.language.huggingface.config.HuggingFaceTokenizerConfig;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.Segmenter;
import com.yahoo.language.tools.Embed;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * {@link Embedder}/{@link Segmenter} using Deep Java Library's HuggingFace Tokenizer.
 *
 * @author bjorncs
 */
@Beta
public class HuggingFaceTokenizer extends AbstractComponent implements Embedder, Segmenter, AutoCloseable {

    private final Map<Language, ai.djl.huggingface.tokenizers.HuggingFaceTokenizer> models;

    @Inject public HuggingFaceTokenizer(HuggingFaceTokenizerConfig cfg) { this(new Builder(cfg)); }

    static {
        // Stop HuggingFace Tokenizer from reporting usage statistics back to mothership
        // See ai.djl.util.Ec2Utils.callHome()
        System.setProperty("OPT_OUT_TRACKING", "true");
    }

    private HuggingFaceTokenizer(Builder b) {
        this.models = withContextClassloader(() -> {
            var models = new EnumMap<Language, ai.djl.huggingface.tokenizers.HuggingFaceTokenizer>(Language.class);
            b.models.forEach((language, path) -> {
                models.put(language,
                           uncheck(() -> {
                               var hfb = ai.djl.huggingface.tokenizers.HuggingFaceTokenizer.builder()
                                       .optTokenizerPath(path)
                                       .optAddSpecialTokens(b.addSpecialTokens != null ? b.addSpecialTokens : true);
                               if (b.maxLength != null) {
                                   hfb.optMaxLength(b.maxLength);
                                   // Override modelMaxLength to workaround HF tokenizer limiting maxLength to 512
                                   hfb.configure(Map.of("modelMaxLength", b.maxLength > 0 ? b.maxLength : Integer.MAX_VALUE));
                               }
                               if (b.padding != null) {
                                   if (b.padding) hfb.optPadToMaxLength(); else hfb.optPadding(false);
                               }
                               if (b.truncation != null) hfb.optTruncation(b.truncation);
                               return hfb.build();
                           }));
            });
            return models;
        });
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

    public static ModelInfo getModelInfo(Path path) {
        return withContextClassloader(() -> {
            // Hackish solution to read padding/truncation configuration through JNI wrapper directly
            LibUtils.checkStatus();
            var handle = TokenizersLibrary.LIB.createTokenizerFromString(uncheck(() -> Files.readString(path)));
            try {
                return new ModelInfo(
                        TruncationStrategy.fromString(TokenizersLibrary.LIB.getTruncationStrategy(handle)),
                        PaddingStrategy.fromString(TokenizersLibrary.LIB.getPaddingStrategy(handle)),
                        TokenizersLibrary.LIB.getMaxLength(handle),
                        TokenizersLibrary.LIB.getStride(handle),
                        TokenizersLibrary.LIB.getPadToMultipleOf(handle));
            } finally {
                TokenizersLibrary.LIB.deleteTokenizer(handle);
            }
        });
    }

    private ai.djl.huggingface.tokenizers.HuggingFaceTokenizer resolve(Language language) {
        // Disregard language if there is default model
        if (models.size() == 1 && models.containsKey(Language.UNKNOWN)) return models.get(Language.UNKNOWN);
        if (models.containsKey(language)) return models.get(language);
        throw new IllegalArgumentException("No model for language " + language);
    }

    private static <R> R withContextClassloader(Supplier<R> r) {
        var original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(HuggingFaceTokenizer.class.getClassLoader());
        try {
            return r.get();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private static long[] toArray(Collection<? extends Number> c) { return c.stream().mapToLong(Number::longValue).toArray(); }

    public static final class Builder {
        private final Map<Language, Path> models = new EnumMap<>(Language.class);
        private Boolean addSpecialTokens;
        private Integer maxLength;
        private Boolean truncation;
        private Boolean padding;

        public Builder() {}
        public Builder(HuggingFaceTokenizerConfig cfg) {
            for (var model : cfg.model())
                addModel(Language.fromLanguageTag(model.language()), model.path());
            addSpecialTokens(cfg.addSpecialTokens());
            if (cfg.maxLength() != -1) setMaxLength(cfg.maxLength());
            switch (cfg.truncation()) {
                case ON -> setTruncation(true);
                case OFF -> setTruncation(false);
            }
            switch (cfg.padding()) {
                case ON -> setPadding(true);
                case OFF -> setPadding(false);
            }
        }

        public Builder addModel(Language lang, Path path) { models.put(lang, path); return this; }
        public Builder addDefaultModel(Path path) { return addModel(Language.UNKNOWN, path); }
        public Builder addSpecialTokens(boolean enabled) { addSpecialTokens = enabled; return this; }
        public Builder setMaxLength(int length) { maxLength = length; return this; }
        public Builder setTruncation(boolean enabled) { truncation = enabled; return this; }
        public Builder setPadding(boolean enabled) { padding = enabled; return this; }
        public HuggingFaceTokenizer build() { return new HuggingFaceTokenizer(this); }
    }

}