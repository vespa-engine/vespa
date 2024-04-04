package com.yahoo.language.significance;

import com.yahoo.component.annotation.Inject;
import com.yahoo.language.Language;
import com.yahoo.search.significance.config.SignificanceConfig;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import static com.yahoo.yolean.Exceptions.uncheck;
/**
 * Default implementation of {@link SignificanceModelRegistry}.
 * This implementation loads models lazily and caches them.
 *
 * @author MariusArhaug
 */
public class DefaultSignificanceModelRegistry implements SignificanceModelRegistry {

    private final Map<Language, SignificanceModel> models;
    @Inject
    public DefaultSignificanceModelRegistry(SignificanceConfig cfg) { this(new Builder(cfg)); }
    private DefaultSignificanceModelRegistry(Builder b) {

        this.models = withContextClassloader(() -> {
            var models = new EnumMap<Language, SignificanceModel>(Language.class);
            b.models.forEach((language, path) -> {
                models.put(language,
                        uncheck(() -> new DefaultSignificanceModel(path)));
            });
            return models;
        });
    }


    @Override
    public SignificanceModel getModel(Language language) {
        return models.get(language);
    }

    private static <R> R withContextClassloader(Supplier<R> r) {
        var original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(SignificanceModel.class.getClassLoader());
        try {
            return r.get();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    public static final class Builder {
        private final Map<Language, Path> models = new EnumMap<>(Language.class);

        public Builder() {}
        public Builder(SignificanceConfig cfg) {
            for (var model : cfg.model()) {
                addModel(Language.fromLanguageTag(model.language()), model.path());
            }
        }

        public Builder addModel(Language lang, Path path) { models.put(lang, path); return this; }
        public DefaultSignificanceModelRegistry build() { return new DefaultSignificanceModelRegistry(this); }
    }

}
