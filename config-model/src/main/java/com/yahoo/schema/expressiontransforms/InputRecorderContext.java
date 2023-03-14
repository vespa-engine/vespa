// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.expressiontransforms;

import com.yahoo.schema.RankProfile;
import com.yahoo.searchlib.rankingexpression.transform.TransformContext;

import java.util.HashSet;
import java.util.Set;

class InputRecorderContext extends TransformContext {

    private final RankProfileTransformContext parent;
    private final Set<String> localVariables = new HashSet<>();

    public RankProfile rankProfile() { return parent.rankProfile(); }
    public Set<String> localVariables() { return localVariables; }

    public InputRecorderContext(RankProfileTransformContext parent) {
        super(parent.constants(), parent.types());
        this.parent = parent;
    }

    public InputRecorderContext(InputRecorderContext parent) {
        super(parent.constants(), parent.types());
        this.parent = parent.parent;
        this.localVariables.addAll(parent.localVariables);
    }
}
