// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import com.yahoo.searchdefinition.Index;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.Case;
import com.yahoo.searchdefinition.document.Dictionary;
import com.yahoo.searchdefinition.document.NormalizeLevel;
import com.yahoo.searchdefinition.document.RankType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.Sorting;
import com.yahoo.vespa.documentmodel.SummaryField;

import java.util.Locale;

/**
 * Helper for converting ParsedField etc to SDField with settings
 *
 * @author arnej27959
 **/
public class ConvertParsedFields {

    void convertMatchSettings(SDField field, ParsedMatchSettings parsed) {
        parsed.getMatchType().ifPresent(matchingType -> field.setMatchingType(matchingType));
        parsed.getMatchCase().ifPresent(casing -> field.setMatchingCase(casing));
        parsed.getGramSize().ifPresent(gramSize -> field.getMatching().setGramSize(gramSize));
        parsed.getMaxLength().ifPresent(maxLength -> field.getMatching().maxLength(maxLength));
        parsed.getMatchAlgorithm().ifPresent
            (matchingAlgorithm -> field.setMatchingAlgorithm(matchingAlgorithm));
        parsed.getExactTerminator().ifPresent
            (exactMatchTerminator -> field.getMatching().setExactMatchTerminator(exactMatchTerminator));
    }

    void convertSorting(SDField field, ParsedSorting parsed, String name) {
        Attribute attribute = field.getAttributes().get(name);
        if (attribute == null) {
            attribute = new Attribute(name, field.getDataType());
            field.addAttribute(attribute);
        }
        Sorting sorting = attribute.getSorting();
        if (parsed.getAscending()) {
            sorting.setAscending();
        } else {
            sorting.setDescending();
        }
        parsed.getFunction().ifPresent(function -> sorting.setFunction(function));
        parsed.getStrength().ifPresent(strength -> sorting.setStrength(strength));
        parsed.getLocale().ifPresent(locale -> sorting.setLocale(locale));
    }

    void convertAttribute(SDField field, ParsedAttribute parsed) {
        String name = parsed.name();
        String fieldName = field.getName();
        Attribute attribute = null;
        if (fieldName.endsWith("." + name)) {
            attribute = field.getAttributes().get(field.getName());
        }
        if (attribute == null) {
            attribute = field.getAttributes().get(name);
            if (attribute == null) {
                attribute = new Attribute(name, field.getDataType());
                field.addAttribute(attribute);
            }
        }
        attribute.setHuge(parsed.getHuge());
        attribute.setPaged(parsed.getPaged());
        attribute.setFastSearch(parsed.getFastSearch());
        attribute.setFastAccess(parsed.getFastAccess());
        attribute.setMutable(parsed.getMutable());
        attribute.setEnableBitVectors(parsed.getEnableBitVectors());
        attribute.setEnableOnlyBitVector(parsed.getEnableOnlyBitVector());

        // attribute.setTensorType(?)

        for (String alias : parsed.getAliases()) {
            field.getAliasToName().put(alias, parsed.lookupAliasedFrom(alias));
        }
        var distanceMetric = parsed.getDistanceMetric();
        if (distanceMetric.isPresent()) {
            String upper = distanceMetric.get().toUpperCase(Locale.ENGLISH);
            attribute.setDistanceMetric(Attribute.DistanceMetric.valueOf(upper));
        }
        var sorting = parsed.getSorting();
        if (sorting.isPresent()) {
            convertSorting(field, sorting.get(), name);
        }
    }

    private void convertRankType(SDField field, String indexName, String rankType) {
        RankType type = RankType.fromString(rankType);
        if (indexName == null || indexName.equals("")) {
            field.setRankType(type); // Set default if the index is not specified.
        } else {
            Index index = field.getIndex(indexName);
            if (index == null) {
                index = new Index(indexName);
                field.addIndex(index);
            }
            index.setRankType(type);
        }
    }

    private void convertNormalizing(SDField field, String setting) {
        NormalizeLevel.Level level;
        if ("none".equals(setting)) {
            level = NormalizeLevel.Level.NONE;
        } else if ("codepoint".equals(setting)) {
            level = NormalizeLevel.Level.CODEPOINT;
        } else if ("lowercase".equals(setting)) {
            level = NormalizeLevel.Level.LOWERCASE;
        } else if ("accent".equals(setting)) {
            level = NormalizeLevel.Level.ACCENT;
        } else if ("all".equals(setting)) {
            level = NormalizeLevel.Level.ACCENT;
        } else {
            throw new IllegalArgumentException("invalid normalizing setting: " + setting);
        }
        field.setNormalizing(new NormalizeLevel(level, true));
    }

    // from grammar, things that can be inside struct-field block
    private void convertCommonFieldSettings(SDField field, ParsedField parsed) {
        convertMatchSettings(field, parsed.matchSettings());
        var indexing = parsed.getIndexing();
        if (indexing.isPresent()) {
            field.setIndexingScript(indexing.get().script());
        }
        parsed.getStemming().ifPresent(value -> field.setStemming(value));
        parsed.getNormalizing().ifPresent(value -> convertNormalizing(field, value));
        for (var attribute : parsed.getAttributes()) {
            convertAttribute(field, attribute);
        }
        // MISSING: parsed.getSummaryFields()
        for (String command : parsed.getQueryCommands()) {
            field.addQueryCommand(command);
        }
        for (var structField : parsed.getStructFields()) {
            convertStructField(field, structField);
        }
    }

    private void convertStructField(SDField field, ParsedField parsed) {
        SDField structField = field.getStructField(parsed.name());
        if (structField == null ) {
            throw new IllegalArgumentException("Struct field '" + parsed.name() + "' has not been defined in struct " +
                                               "for field '" + field.getName() + "'.");
        }
        convertCommonFieldSettings(structField, parsed);
    }

    private void convertExtraFieldSettings(SDField field, ParsedField parsed) {
        String name = parsed.name();
        for (var dictOp : parsed.getDictionaryOptions()) {
            var dictionary = field.getOrSetDictionary();
            switch (dictOp) {
            case HASH:    dictionary.updateType(Dictionary.Type.HASH); break;
            case BTREE:   dictionary.updateType(Dictionary.Type.BTREE); break;
            case CASED:   dictionary.updateMatch(Case.CASED); break;
            case UNCASED: dictionary.updateMatch(Case.UNCASED); break;
            }
        }
        // MISSING: parsed.getIndexes()
        for (var alias : parsed.getAliases()) {
            field.getAliasToName().put(alias, parsed.lookupAliasedFrom(alias));
        }
        parsed.getRankTypes().forEach((indexName, rankType) -> convertRankType(field, indexName, rankType));
        parsed.getSorting().ifPresent(sortInfo -> convertSorting(field, sortInfo, name));
        if (parsed.getBolding()) {
            // TODO must it be so ugly:
            SummaryField summaryField = field.getSummaryField(name, true);
            summaryField.addSource(name);
            summaryField.addDestination("default");
            summaryField.setTransform(summaryField.getTransform().bold());
        }
        if (parsed.getLiteral()) {
            field.getRanking().setLiteral(true);
        }
        if (parsed.getFilter()) {
            field.getRanking().setFilter(true);
        }
    }

}
