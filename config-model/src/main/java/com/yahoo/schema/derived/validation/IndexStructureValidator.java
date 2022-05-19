// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived.validation;

import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.DerivedConfiguration;
import com.yahoo.schema.derived.IndexingScript;
import com.yahoo.vespa.indexinglanguage.ExpressionVisitor;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.OutputExpression;

/**
 * @author Mathias M Lidal
 */
public class IndexStructureValidator extends Validator {

    public IndexStructureValidator(DerivedConfiguration config, Schema schema) {
        super(config, schema);
    }

    public void validate() {
        IndexingScript script = config.getIndexingScript();
        for (Expression exp : script.expressions()) {
            new OutputVisitor(schema.getDocument(), exp).visit(exp);
        }
    }

    private static class OutputVisitor extends ExpressionVisitor {

        final SDDocumentType docType;
        final Expression exp;

        public OutputVisitor(SDDocumentType docType, Expression exp) {
            this.docType = docType;
            this.exp = exp;
        }

        @Override
        protected void doVisit(Expression exp) {
            if (!(exp instanceof OutputExpression)) return;

            String fieldName = ((OutputExpression)exp).getFieldName();
            if (docType.getField(fieldName) != null) return;

            throw new IllegalArgumentException("Indexing expression '" + this.exp + "' refers to field '" +
                                               fieldName + "' which does not exist in the index structure.");
        }
    }

}
