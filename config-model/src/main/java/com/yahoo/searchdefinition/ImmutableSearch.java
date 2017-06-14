// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.document.ImmutableSDField;
import com.yahoo.vespa.documentmodel.SummaryField;

import java.util.Map;
import java.util.stream.Stream;

/**
 * An interface containing the non-mutating methods of {@link Search}.
 * For description of the methods see {@link Search}.
 *
 * @author bjorncs
 */
public interface ImmutableSearch {

    Stream<ImmutableSDField> allImportedFields();

    ImmutableSDField getField(String name);

    Stream<ImmutableSDField> allFields();

    Map<String, SummaryField> getSummaryFields(ImmutableSDField field);
}
