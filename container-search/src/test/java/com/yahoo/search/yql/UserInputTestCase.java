// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import static org.junit.Assert.*;

import com.yahoo.search.query.QueryTree;
import org.apache.http.client.utils.URIBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

import java.util.Arrays;

import static com.yahoo.container.protect.Error.INVALID_QUERY_PARAMETER;

/**
 * Tests where you really test YqlParser but need the full Query infrastructure.
 *
 * @author steinar
 */
public class UserInputTestCase {

    private Chain<Searcher> searchChain;
    private Execution.Context context;
    private Execution execution;

    @Before
    public void setUp() throws Exception {
        searchChain = new Chain<>(new MinimalQueryInserter());
        context = Execution.Context.createContextStub(null);
        execution = new Execution(searchChain, context);
    }

    @After
    public void tearDown() {
        searchChain = null;
        context = null;
        execution = null;
    }

    @Test
    public void testSimpleUserInput() {
        {
            URIBuilder builder = searchUri();
            builder.setParameter("yql",
                    "select * from sources * where userInput(\"nalle\");");
            Query query = searchAndAssertNoErrors(builder);
            assertEquals("select * from sources * where default contains \"nalle\";", query.yqlRepresentation());
        }
        {
            URIBuilder builder = searchUri();
            builder.setParameter("nalle", "bamse");
            builder.setParameter("yql",
                    "select * from sources * where userInput(@nalle);");
            Query query = searchAndAssertNoErrors(builder);
            assertEquals("select * from sources * where default contains \"bamse\";", query.yqlRepresentation());
        }
        {
            URIBuilder builder = searchUri();
            builder.setParameter("nalle", "bamse");
            builder.setParameter("yql",
                    "select * from sources * where userInput(nalle);");
            Query query = new Query(builder.toString());
            Result r = execution.search(query);
            assertNotNull(r.hits().getError());
        }
    }

    @Test
    public void testRawUserInput() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                "select * from sources * where [{\"grammar\": \"raw\"}]userInput(\"nal le\");");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where default contains \"nal le\";", query.yqlRepresentation());
    }

    @Test
    public void testSegmentedUserInput() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                "select * from sources * where [{\"grammar\": \"segment\"}]userInput(\"nal le\");");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where default contains ([{\"origin\": {\"original\": \"nal le\", \"offset\": 0, \"length\": 6}}]phrase(\"nal\", \"le\"));", query.yqlRepresentation());
    }

    @Test
    public void testSegmentedNoiseUserInput() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                "select * from sources * where [{\"grammar\": \"segment\"}]userInput(\"^^^^^^^^\");");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where default contains \"^^^^^^^^\";", query.yqlRepresentation());
    }

    @Test
    public void testCustomDefaultIndexUserInput() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                "select * from sources * where [{\"defaultIndex\": \"glompf\"}]userInput(\"nalle\");");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where glompf contains \"nalle\";", query.yqlRepresentation());
    }

    @Test
    public void testAnnotatedUserInputStemming() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                "select * from sources * where [{\"stem\": false}]userInput(\"nalle\");");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals(
                "select * from sources * where default contains ([{\"stem\": false}]\"nalle\");",
                query.yqlRepresentation());
    }

    @Test
    public void testNegativeNumberComparison() {
        URIBuilder builder = searchUri();
        builder.setParameter("myinput", "-5");
        builder.setParameter("yql",
                             "select * from ecitem where rank(([{\"defaultIndex\":\"myfield\"}](userInput(@myinput))));");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from ecitem where rank(myfield = (-5));", query.yqlRepresentation());
        assertEquals("RANK myfield:-5", query.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    public void testAnnotatedUserInputUnrankedTerms() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                "select * from sources * where [{\"ranked\": false}]userInput(\"nalle\");");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals(
                "select * from sources * where default contains ([{\"ranked\": false}]\"nalle\");",
                query.yqlRepresentation());
    }

    @Test
    public void testAnnotatedUserInputFiltersTerms() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                "select * from sources * where [{\"filter\": true}]userInput(\"nalle\");");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals(
                "select * from sources * where default contains ([{\"filter\": true}]\"nalle\");",
                query.yqlRepresentation());
    }

    @Test
    public void testAnnotatedUserInputCaseNormalization() {
        URIBuilder builder = searchUri();
        builder.setParameter(
                "yql",
                "select * from sources * where [{\"normalizeCase\": false}]userInput(\"nalle\");");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals(
                "select * from sources * where default contains ([{\"normalizeCase\": false}]\"nalle\");",
                query.yqlRepresentation());
    }

    @Test
    public void testAnnotatedUserInputAccentRemoval() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                "select * from sources * where [{\"accentDrop\": false}]userInput(\"nalle\");");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals(
                "select * from sources * where default contains ([{\"accentDrop\": false}]\"nalle\");",
                query.yqlRepresentation());
    }

    @Test
    public void testAnnotatedUserInputPositionData() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                "select * from sources * where [{\"usePositionData\": false}]userInput(\"nalle\");");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals(
                "select * from sources * where default contains ([{\"usePositionData\": false}]\"nalle\");",
                query.yqlRepresentation());
    }

    @Test
    public void testQueryPropertiesAsStringArguments() {
        URIBuilder builder = searchUri();
        builder.setParameter("nalle", "bamse");
        builder.setParameter("meta", "syntactic");
        builder.setParameter("yql",
                "select * from sources * where foo contains @nalle and foo contains phrase(@nalle, @meta, @nalle);");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where (foo contains \"bamse\" AND foo contains phrase(\"bamse\", \"syntactic\", \"bamse\"));", query.yqlRepresentation());
    }

    @Test
    public void testReferenceInComparison() {
        URIBuilder builder = searchUri();
        builder.setParameter("varref", "1980");
        builder.setParameter("yql", "select * from sources * where year > @varref;");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where year > 1980;", query.yqlRepresentation());
    }

    @Test
    public void testReferenceInContinuation() {
        URIBuilder builder = searchUri();
        builder.setParameter("continuation", "BCBCBCBEBG");
        builder.setParameter("yql",
                             "select * from sources * where myfield contains 'token'" +
                             "| [{'continuations':[@continuation, 'BCBKCBACBKCCK'] }] all(group(f) each(output(count())));");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where myfield contains \"token\" | [{ 'continuations':['BCBCBCBEBG', 'BCBKCBACBKCCK'] }]all(group(f) each(output(count())));", query.yqlRepresentation());
    }

    @Test
    public void testReferenceInEquiv() {
        URIBuilder builder = searchUri();
        builder.setParameter("term", "A");
        builder.setParameter("yql",
                             "select foo from bar where fieldName contains equiv(@term,'B');");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select foo from bar where fieldName contains equiv(\"A\", \"B\");", query.yqlRepresentation());
    }

    private Query searchAndAssertNoErrors(URIBuilder builder) {
        Query query = new Query(builder.toString());
        Result r = execution.search(query);
        assertNull(stackTraceIfAny(r), r.hits().getError());
        return query;
    }

    private String stackTraceIfAny(Result r) {
        if (r.hits().getError() == null) return "";
        if (r.hits().getError().getCause() == null) return "";
        return Arrays.toString(r.hits().getError().getCause().getStackTrace());
    }

    private URIBuilder searchUri() {
        URIBuilder builder = new URIBuilder();
        builder.setPath("search/");
        return builder;
    }

    @Test
    public void testEmptyUserInput() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql", "select * from sources * where userInput(\"\");");
        assertQueryFails(builder);
    }

    @Test
    public void testEmptyUserInputFromQueryProperty() {
        URIBuilder builder = searchUri();
        builder.setParameter("foo", "");
        builder.setParameter("yql", "select * from sources * where userInput(@foo);");
        assertQueryFails(builder);
    }

    @Test
    public void testEmptyQueryProperty() {
        URIBuilder builder = searchUri();
        builder.setParameter("foo", "");
        builder.setParameter("yql", "select * from sources * where bar contains \"a\" and nonEmpty(foo contains @foo);");
        assertQueryFails(builder);
    }

    @Test
    public void testEmptyQueryPropertyInsideExpression() {
        URIBuilder builder = searchUri();
        builder.setParameter("foo", "");
        builder.setParameter("yql",
                "select * from sources * where bar contains \"a\" and nonEmpty(bar contains \"bar\" and foo contains @foo);");
        assertQueryFails(builder);
    }

    @Test
    public void testCompositeWithoutArguments() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql", "select * from sources * where bar contains \"a\" and foo contains phrase();");
        searchAndAssertNoErrors(builder);
        builder = searchUri();
        builder.setParameter("yql", "select * from sources * where bar contains \"a\" and nonEmpty(foo contains phrase());");
        assertQueryFails(builder);
    }

    @Test
    public void testAnnoyingPlacementOfNonEmpty() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                "select * from sources * where bar contains \"a\" and foo contains nonEmpty(phrase(\"a\", \"b\"));");
        assertQueryFails(builder);
    }

    private void assertQueryFails(URIBuilder builder) {
        Result r = execution.search(new Query(builder.toString()));
        assertEquals(INVALID_QUERY_PARAMETER.code, r.hits().getError().getCode());
    }

    @Test
    public void testAllowEmptyUserInput() {
        URIBuilder builder = searchUri();
        builder.setParameter("foo", "");
        builder.setParameter("yql", "select * from sources * where [{\"allowEmpty\": true}]userInput(@foo);");
        searchAndAssertNoErrors(builder);
    }

    @Test
    public void testAllowEmptyNullFromQueryParsing() {
        URIBuilder builder = searchUri();
        builder.setParameter("foo", ",,,,,,,,");
        builder.setParameter("yql", "select * from sources * where [{\"allowEmpty\": true}]userInput(@foo);");
        searchAndAssertNoErrors(builder);
    }

    @Test
    public void testDisallowEmptyNullFromQueryParsing() {
        URIBuilder builder = searchUri();
        builder.setParameter("foo", ",,,,,,,,");
        builder.setParameter("yql", "select * from sources * where userInput(@foo);");
        assertQueryFails(builder);
    }

}
