package com.yahoo.search.searchers;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.SameElementItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class ValidateSameElementTestCase {

    @Test
    public void oneAndIsFlattened() {
        var root = new SameElementItem("myField");
        var and = new AndItem();
        and.addItem(new WordItem("a"));
        and.addItem(new WordItem("b"));
        root.addItem(and);
        assertEquals("myField:{(AND a b)}", root.toString());
        var processedRoot = search(root).hits().getQuery().getModel().getQueryTree().getRoot();
        assertEquals("myField:{a b}", processedRoot.toString());
    }

    @Test
    public void multipleAndsAreFlattened() {
        var root = new SameElementItem("myField");
        root.addItem(new WordItem("a"));
        var and1 = new AndItem();
        and1.addItem(new WordItem("and1_a"));
        and1.addItem(new WordItem("and1_b"));
        root.addItem(and1);
        root.addItem(new WordItem("b"));
        var and2 = new AndItem();
        and2.addItem(new WordItem("and2_a"));
        root.addItem(and2);
        root.addItem(new WordItem("c"));
        assertEquals("myField:{a (AND and1_a and1_b) b (AND and2_a) c}", root.toString());
        var processedRoot = search(root).hits().getQuery().getModel().getQueryTree().getRoot();
        assertEquals("myField:{a and1_a and1_b b and2_a c}", processedRoot.toString());
    }

    private Result search(CompositeItem root) {
        var query = new Query();
        query.getModel().getQueryTree().setRoot(root);
        return new Execution(new ValidateSameElementSearcher(),
                             Execution.Context.createContextStub()).search(query);
    }

}
