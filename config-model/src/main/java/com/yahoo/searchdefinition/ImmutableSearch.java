// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.searchdefinition.document.ImmutableSDField;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.documentmodel.SummaryField;

import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * An interface containing the non-mutating methods of {@link Search}.
 * For description of the methods see {@link Search}.
 *
 * @author bjorncs
 */
public interface ImmutableSearch {

    String getName();
    Index getIndex(String name);
    SDField getConcreteField(String name);
    List<SDField> allConcreteFields();
    List<Index> getExplicitIndices();
    Reader getRankingExpression(String fileName);
    ApplicationPackage applicationPackage();
    RankingConstants rankingConstants();
    Stream<ImmutableSDField> allImportedFields();

    ImmutableSDField getField(String name);

    default Stream<ImmutableSDField> allFields() {
        return allFieldsList().stream();
    }
    List<ImmutableSDField> allFieldsList();

    Map<String, SummaryField> getSummaryFields(ImmutableSDField field);
}
