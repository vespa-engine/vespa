package com.yahoo.search.searchers;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.EquivItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.SameElementItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author bratseth
 */
public class ValidateSameElementTestCase {

    @Test
    public void testValidSameElement() {
        var root = new SameElementItem("myField");
        root.addItem(new WordItem("a"));
        var and1 = new AndItem();
        and1.addItem(new WordItem("and1_a"));
        and1.addItem(new WordItem("and1_b"));
        root.addItem(and1);
        root.addItem(new EquivItem(new WordItem("b")));
        var or2 = new OrItem();
        or2.addItem(new WordItem("or2_a"));
        root.addItem(or2);
        root.addItem(new WordItem("c"));
        assertEquals("myField:{a (AND and1_a and1_b) (EQUIV b) (OR or2_a) c}", root.toString());
        var processedRoot = search(root).hits().getQuery().getModel().getQueryTree().getRoot();
        assertEquals("myField:{a (AND and1_a and1_b) (EQUIV b) (OR or2_a) c}", processedRoot.toString());
    }

    @Test
    public void testInvalidSameElement() {
        var root = new SameElementItem("myField");
        root.addItem(new WordItem("a"));
        var and1 = new AndItem();
        and1.addItem(new WordItem("and1_a"));
        var weakAnd = new WeakAndItem();
        weakAnd.addItem(new WordItem("a"));
        weakAnd.addItem(new WordItem("b"));
        and1.addItem(weakAnd);
        and1.addItem(new WordItem("and1_b"));
        root.addItem(and1);
        root.addItem(new EquivItem(new WordItem("b")));
        assertEquals("myField:{a (AND and1_a (WEAKAND(100) a b) and1_b) (EQUIV b)}", root.toString());
        var result = search(root);
        assertNotNull(result.hits().getError());
        assertEquals("SameElementItem cannot contain '(WEAKAND(100) a b)'", result.hits().getError().getDetailedMessage());
    }

    private Result search(CompositeItem root) {
        var query = new Query();
        query.getModel().getQueryTree().setRoot(root);
        return new Execution(new ValidateSameElementSearcher(),
                             Execution.Context.createContextStub()).search(query);
    }

}
