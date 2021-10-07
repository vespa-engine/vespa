// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.document.select.DocumentSelector;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.document.select.rule.DocumentNode;
import com.yahoo.vespa.model.content.DocumentTypeVisitor;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;

/**
 * @author thomasg
 */
public class DocumentSelectionBuilder {

    private static class AllowedDocumentTypesChecker extends DocumentTypeVisitor {
        String allowedType;

        private AllowedDocumentTypesChecker(String allowedType) {
            this.allowedType = allowedType;
        }

        @Override
        public void visit(DocumentNode documentNode) {
            if (!documentNode.getType().equals(this.allowedType)) {
                if (this.allowedType == null) {
                    throw new IllegalArgumentException("Document type references are not allowed " +
                            "in global <documents> tag selection attribute (found reference to type '" +
                            documentNode.getType() + "')");
                } else {
                    throw new IllegalArgumentException("Selection for document type '" +
                            this.allowedType + "' can not contain references to other " +
                            "document types (found reference to type '" + documentNode.getType() + "')");
                }
            }
        }
    }

    private void validateSelectionExpression(String sel, String allowedType) {
        DocumentSelector s;
        try {
            s = new DocumentSelector(sel);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Could not parse document routing selection: " + sel, e);
        }
        AllowedDocumentTypesChecker checker = new AllowedDocumentTypesChecker(allowedType);
        s.visit(checker);
    }

    public String build(ModelElement elem) {
        StringBuilder sb = new StringBuilder();
        if (elem != null) {
            for (ModelElement e : elem.subElements("document")) {
                if (sb.length() > 0) {
                    sb.append(" OR ");
                }
                sb.append('(');
                String type = e.stringAttribute("type");
                sb.append(type);
                String selection = e.stringAttribute("selection");
                if (selection != null) {
                    validateSelectionExpression(selection, type);
                    sb.append(" AND (");
                    sb.append(selection);
                    sb.append(')');
                }
                sb.append(')');
            }

            String globalSelection = elem.stringAttribute("selection");
            if (globalSelection != null) {
                validateSelectionExpression(globalSelection, null);
                return "(" + globalSelection + ") AND (" + sb + ")";
            }
        }
        return sb.toString();
    }

}
