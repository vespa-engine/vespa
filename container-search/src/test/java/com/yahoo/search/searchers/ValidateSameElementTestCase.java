package com.yahoo.search.searchers;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.CorrelateByItem;
import com.yahoo.prelude.query.EquivItem;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.RankItem;
import com.yahoo.prelude.query.SameElementItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.schema.Cluster;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        var result = search(root);
        assertNull(result.hits().getError());
    }

    @Test
    public void testValidSameElementWithNot() {
        var root = new SameElementItem("myField");
        var not = new NotItem();
        not.addItem(new WordItem("a"));
        not.addItem(new WordItem("and"));
        root.addItem(not);
        var result = search(root);
        assertNull(result.hits().getError());
    }

    @Test
    public void testInvalidSameElement() {
        var root = new SameElementItem("myField");
        addChildrenWithWeakAnd(root);
        var result = search(root);
        assertNotNull(result.hits().getError());
        assertEquals("SameElementItem cannot contain '(WEAKAND a b)'", result.hits().getError().getDetailedMessage());
    }

    @Test
    public void testInvalidNestedSameElement() {
        var root = new RankItem();
        var child1 = new SameElementItem("myField");
        root.addItem(child1);
        root.addItem(new WordItem("myField"));
        addChildrenWithWeakAnd(child1);
        var result = search(root);
        assertNotNull(result.hits().getError());
        assertEquals("SameElementItem cannot contain '(WEAKAND a b)'", result.hits().getError().getDetailedMessage());
    }

    @Test
    public void testNoSameElement() {
        var root = new RankItem();
        addChildrenWithWeakAnd(root);
        var result = search(root);
        assertNull(result.hits().getError());
    }

    @Test
    void correlationIsRejectedWhenStreamingIsAmongTheSelectedClusters() {
        var query = correlationQuery();
        var result = search(query, mixedSchemaInfo());
        assertNotNull(result.hits().getError());
        assertEquals("correlateBy is only supported by indexed search", result.hits().getError().getDetailedMessage());
    }

    @Test
    void correlationIsAcceptedWhenSourcesSelectOnlyTheIndexedCluster() {
        var query = correlationQuery();
        query.getModel().getSources().add("indexed-cluster");
        assertNull(search(query, mixedSchemaInfo()).hits().getError());
    }

    @Test
    void correlationIsAcceptedWhenRestrictExcludesTheStreamingSchema() {
        var query = correlationQuery();
        query.getModel().getRestrict().add("indexed-document");
        assertNull(search(query, mixedSchemaInfo()).hits().getError());
    }

    @Test
    void correlationIsRejectedWhenTheStreamingSchemaIsSelectedDirectly() {
        var query = correlationQuery();
        query.getModel().getSources().add("streaming-document");
        assertNotNull(search(query, mixedSchemaInfo()).hits().getError());
    }

    @Test
    void indexedGroupingFiltersAreRejectedWhenStreamingIsSelected() {
        for (String filter : List.of("onceperdoc(signals.unit)", "not(correlated(\"sel\", \"unit\", signals.unit))",
                                     "correlated(\"sel\", \"unit\", signals.unit) and onceperdoc(signals.unit)")) {
            var query = new Query("?query=shoe");
            GroupingRequest.newInstance(query).setRootOperation(GroupingOperation.fromString(
                    "all(group(signals.unit) filter(" + filter + ") each(output(count())))"));
            var result = search(query, mixedSchemaInfo());
            assertNotNull(result.hits().getError());
            assertEquals("correlated() and onceperdoc() grouping filters are only supported by indexed search",
                         result.hits().getError().getDetailedMessage());
            query.getModel().getSources().add("indexed-cluster");
            assertNull(search(query, mixedSchemaInfo()).hits().getError());
        }
    }

    @Test
    void ordinaryGroupingFiltersRemainValidForStreaming() {
        var query = new Query("?query=shoe");
        GroupingRequest.newInstance(query).setRootOperation(GroupingOperation.fromString(
                "all(group(signals.unit) filter(regex(\"a.*\", signals.unit)) each(output(count())))"));
        assertNull(search(query, mixedSchemaInfo()).hits().getError());
    }

    private Query correlationQuery() {
        var clause = new SameElementItem("signals");
        clause.addItem(new WordItem("size", "key"));
        var correlation = new CorrelateByItem();
        correlation.addClause(new CorrelateByItem.Clause(clause, Map.of("unit", "unit_id")));
        var root = new AndItem();
        root.addItem(new WordItem("shoe"));
        root.addItem(correlation);
        var query = new Query();
        query.getModel().getQueryTree().setRoot(root);
        return query;
    }

    private SchemaInfo mixedSchemaInfo() {
        return new SchemaInfo(List.of(new Schema.Builder("indexed-document").build(),
                                      new Schema.Builder("streaming-document").build()),
                              List.of(new Cluster.Builder("indexed-cluster").addSchema("indexed-document").build(),
                                      new Cluster.Builder("streaming-cluster").setStreaming(true)
                                          .addSchema("streaming-document").build()));
    }

    private Result search(Query query, SchemaInfo schemaInfo) {
        return new Execution(new ValidateSameElementSearcher(),
                             Execution.Context.createContextStub(schemaInfo)).search(query);
    }

    private void addChildrenWithWeakAnd(CompositeItem root) {
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
    }

    private Result search(CompositeItem root) {
        var query = new Query();
        query.getModel().getQueryTree().setRoot(root);
        return new Execution(new ValidateSameElementSearcher(),
                             Execution.Context.createContextStub()).search(query);
    }

}
