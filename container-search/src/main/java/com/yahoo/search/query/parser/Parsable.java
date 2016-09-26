// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.parser;

import com.yahoo.language.Language;
import com.yahoo.search.query.Model;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * <p>This class encapsulates all the parameters required to call {@link Parser#parse(Parsable)}. Because all set-
 * methods return a reference to self, you can write very compact calls to the parser:</p>
 *
 * <pre>
 * parser.parse(new Parsable()
 *                  .setQuery("foo")
 *                  .setFilter("bar")
 *                  .setDefaultIndexName("default")
 *                  .setLanguage(Language.ENGLISH))
 * </pre>
 *
 * <p>In case you are parsing the content of a {@link Model}, you can use the {@link #fromQueryModel(Model)} factory for
 * convenience.</p>
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 * @since 5.1.4
 */
public final class Parsable {

    private final Set<String> sourceList = new HashSet<>();
    private final Set<String> restrictList = new HashSet<>();
    private String query;
    private String filter;
    private String defaultIndexName;
    private Language language;
    private Optional<Language> explicitLanguage = Optional.empty();

    public String getQuery() {
        return query;
    }

    public Parsable setQuery(String query) {
        this.query = query;
        return this;
    }

    public String getFilter() {
        return filter;
    }

    public Parsable setFilter(String filter) {
        this.filter = filter;
        return this;
    }

    public String getDefaultIndexName() {
        return defaultIndexName;
    }

    public Parsable setDefaultIndexName(String defaultIndexName) {
        this.defaultIndexName = defaultIndexName;
        return this;
    }

    /** 
     * Returns the language to use when parsing, 
     * if not decided by the item under parsing. This is never null or UNKNOWN 
     */
    public Language getLanguage() { return language; }

    public Parsable setLanguage(Language language) {
        Objects.requireNonNull(language, "Language cannot be null");
        this.language = language;
        return this;
    }

    /** Returns the language explicitly set to be used when parsing, or empty if none is set. */
    public Optional<Language> getExplicitLanguage() { return explicitLanguage; }

    public Parsable setExplicitLanguage(Optional<Language> language) {
        Objects.requireNonNull(language, "Explicit language cannot be null");
        this.explicitLanguage = language;
        return this;
    }

    public Set<String> getSources() {
        return sourceList;
    }

    public Parsable addSource(String sourceName) {
        sourceList.add(sourceName);
        return this;
    }

    public Parsable addSources(Collection<String> sourceNames) {
        sourceList.addAll(sourceNames);
        return this;
    }

    public Set<String> getRestrict() {
        return restrictList;
    }

    public Parsable addRestrict(String restrictName) {
        restrictList.add(restrictName);
        return this;
    }

    public Parsable addRestricts(Collection<String> restrictNames) {
        restrictList.addAll(restrictNames);
        return this;
    }

    public static Parsable fromQueryModel(Model model) {
        return new Parsable()
                .setQuery(model.getQueryString())
                .setFilter(model.getFilter())
                .setLanguage(model.getParsingLanguage())
                .setExplicitLanguage(Optional.ofNullable(model.getLanguage()))
                .setDefaultIndexName(model.getDefaultIndex())
                .addSources(model.getSources())
                .addRestricts(model.getRestrict());
    }

}
