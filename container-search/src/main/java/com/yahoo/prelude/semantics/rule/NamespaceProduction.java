// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import com.yahoo.prelude.semantics.RuleBaseException;
import com.yahoo.prelude.semantics.engine.RuleEvaluation;

/**
 * A production in a specified namespace
 *
 * @author bratseth
 */
public class NamespaceProduction extends Production {

    /** The label in this namespace */
    private String namespace;

    /** The key ito set in the namespace */
    private String key;

    /** The value to set in the namespace */
    private String value;

    /** Creates a produced template term with no label and the default type */
    public NamespaceProduction(String namespace, String key, String value) {
        setNamespace(namespace);
        this.key = key;
        this.value = value;
    }

    public String getNamespace() { return namespace; }

    public final void setNamespace(String namespace) {
        if (!namespace.equals("parameter"))
            throw new RuleBaseException("Can not produce into namespace '" + namespace +
                                        ". Only the 'parameter' name space can be referenced currently");
        this.namespace = namespace;
    }

    public String getKey() { return key; }

    public void setKey(String key) { this.key = key; }

    public String getValue() { return value; }

    public void setValue(String value) { this.value = value; }

    public void produce(RuleEvaluation e, int offset) {
        e.getEvaluation().getQuery().properties().set(key, value);
    }

    /** All instances of this produces a parseable string output */
    public String toInnerString() {
        return namespace + "." + key + "='" + value + "'";
    }

}
