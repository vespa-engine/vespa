// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentId;
import com.yahoo.document.datatypes.StringFieldValue;

import java.util.Objects;
import java.util.Set;

/**
 * Expression exposing the id of the document being processed as a string, or, given an optional
 * argument, a single structural part of it (its namespace, document type, group, number or
 * namespace-specific part).
 *
 * @author Dainius Jocas
 */
public final class DocumentIdExpression extends Expression {

    private static final Set<String> PARTS = Set.of("namespace", "documenttype", "group", "number", "specific");

    private final String part;

    public DocumentIdExpression() { this(null); }

    public DocumentIdExpression(String part) {
        if (part != null && !PARTS.contains(part))
            throw new IllegalArgumentException("documentid: unknown part '" + part + "', expected one of " + PARTS);
        this.part = part;
    }

    public String getPart() { return part; }

    @Override
    public boolean requiresInput() { return false; }

    @Override
    public DataType setInputType(DataType inputType, TypeContext context) {
        super.setInputType(inputType, context);
        return DataType.STRING;
    }

    @Override
    public DataType setOutputType(DataType outputType, TypeContext context) {
        super.setOutputType(DataType.STRING, outputType, null, context);
        return AnyDataType.instance;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setCurrentValue(new StringFieldValue(extract(context.getDocumentId().orElse(null))));
    }

    private String extract(DocumentId id) {
        if (id == null) return "";
        if (part == null) return id.toString();
        return switch (part) {
            case "namespace" -> id.getScheme().getNamespace();
            case "documenttype" -> id.hasDocType() ? id.getDocType() : "";
            case "group" -> id.getScheme().hasGroup() ? id.getScheme().getGroup() : "";
            case "number" -> id.getScheme().hasNumber() ? String.valueOf(id.getScheme().getNumber()) : "";
            case "specific" -> id.getScheme().getNamespaceSpecific();
            default -> throw new IllegalStateException("Unreachable, part is validated in the constructor");
        };
    }

    @Override
    public String toString() { return part == null ? "documentid" : "documentid " + part; }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DocumentIdExpression rhs)) return false;
        return Objects.equals(part, rhs.part);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + Objects.hashCode(part);
    }

}
