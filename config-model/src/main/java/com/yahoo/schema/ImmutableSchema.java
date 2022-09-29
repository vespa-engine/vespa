// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.vespa.documentmodel.SummaryField;

import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * An interface containing the non-mutating methods of {@link Schema}.
 * For description of the methods see {@link Schema}.
 *
 * @author bjorncs
 */
public interface ImmutableSchema {

    String getName();
    Optional<? extends ImmutableSchema> inherited();
    Index getIndex(String name);
    ImmutableSDField getConcreteField(String name);
    //TODO split in mutating/immutable by returning List<ImmutableSDField>
    List<SDField> allConcreteFields();
    List<Index> getExplicitIndices();
    Reader getRankingExpression(String fileName);
    ApplicationPackage applicationPackage();
    DeployLogger getDeployLogger();
    ModelContext.Properties getDeployProperties();
    Map<Reference, RankProfile.Constant> constants();
    LargeRankingExpressions rankExpressionFiles();
    Map<String, OnnxModel> onnxModels();
    Stream<ImmutableSDField> allImportedFields();
    SDDocumentType getDocument();
    ImmutableSDField getField(String name);

    default Stream<ImmutableSDField> allFields() {
        return allFieldsList().stream();
    }
    List<ImmutableSDField> allFieldsList();

    List<SummaryField> getSummaryFields(ImmutableSDField field);

}
