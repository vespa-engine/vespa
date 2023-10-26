// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.matchers;

import com.yahoo.vdslib.state.Node;
import com.yahoo.vespa.clustercontroller.core.NodeStateReason;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

import java.util.Map;

public class HasStateReasonForNode extends BaseMatcher<Map<Node, NodeStateReason>> {
    private final Node node;
    private final NodeStateReason expected;

    private HasStateReasonForNode(Node node, NodeStateReason expected) {
        this.node = node;
        this.expected = expected;
    }

    @Override
    public boolean matches(Object o) {
        if (o == null || !(o instanceof Map)) {
            return false;
        }
        return expected == ((Map)o).get(node);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(String.format("has node state reason %s", expected.toString()));
    }

    @Override
    public void describeMismatch(Object item, Description description) {
        @SuppressWarnings("unchecked")
        Map<Node, NodeStateReason> other = (Map<Node, NodeStateReason>)item;
        if (other.containsKey(node)) {
            description.appendText(String.format("has reason %s", other.get(node).toString()));
        } else {
            description.appendText("has no entry for node");
        }
    }

    public static HasStateReasonForNode hasStateReasonForNode(Node node, NodeStateReason reason) {
        return new HasStateReasonForNode(node, reason);
    }
}
