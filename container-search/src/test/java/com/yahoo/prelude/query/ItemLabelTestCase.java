// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.yahoo.search.Query;
import com.yahoo.prelude.query.textualrepresentation.Discloser;

public class ItemLabelTestCase {

    private static final class LabelCatcher implements Discloser {
        public String label = null;
        public void addProperty(String key, Object value) {
            if (key.equals("label")) {
                if (value == null) {
                    label = "null";
                } else {
                    label = (String) value;
                }
            }
        }
        public void setValue(Object value) {}
        public void addChild(Item item) {}
    }

    @Test
    final void testLabelVisibility() throws Exception {
        assertTrue(Modifier.isPublic(Item.class.getMethod("setLabel", String.class).getModifiers()));
        assertTrue(Modifier.isPublic(Item.class.getMethod("getLabel").getModifiers()));
    }

    @Test
    final void testLabelAccess() {
        Item item = new WordItem("word");
        assertFalse(item.hasUniqueID());
        assertNull(item.getLabel());
        item.setLabel("my_label");
        assertTrue(item.hasUniqueID());
        assertEquals("my_label", item.getLabel());
    }

    @Test
    final void testLabelDisclose() {
        LabelCatcher catcher = new LabelCatcher();
        Item item = new WordItem("word");
        item.disclose(catcher);
        assertNull(catcher.label);
        item.setLabel("my_other_label");
        item.disclose(catcher);
        assertEquals("my_other_label", item.getLabel());
    }

    @Test
    final void testLabelEncode() {
        Item w1 = new WordItem("w1");
        Item w2 = new WordItem("w2");
        Item w3 = new WordItem("w3");
        AndItem and = new AndItem();
        Query query = new Query();

        w1.setLabel("bar");
        w3.setLabel("foo");
        and.addItem(w1);
        and.addItem(w2);
        and.addItem(w3);
        and.setLabel("missing");
        query.getModel().getQueryTree().setRoot(and);
        query.prepare();
        assertEquals("3", query.getRanking().getProperties().get("vespa.label.foo.id").get(0));
        assertEquals("1", query.getRanking().getProperties().get("vespa.label.bar.id").get(0));

        // Conceptually, any node can have a label. However, only
        // taggable nodes are allowed to have a unique id. Taggable
        // nodes act as leaf nodes, but labels should be possible for
        // any combination of nodes in the query tree. Thus, generic
        // labeling is appropriate, but only those items that are also
        // taggable will propagate their labels to the bank-end. We
        // can live with this weakness for now, as the nodes we
        // typically need to label in the back-end are leaf-ish nodes.
        assertNull(query.getRanking().getProperties().get("vespa.label.missing.id"));
    }

}
