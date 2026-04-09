// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.component.chain.Chain;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.RangeItem;
import com.yahoo.prelude.query.TrueItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.Query;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ElasticsearchQueryTransformerTest {

	private Execution execution;

	@BeforeEach
	void setUp() {
		execution = new Execution(new Chain<>(new ElasticsearchQueryTransformer()),
								  Execution.Context.createContextStub());
	}

	@Test
	void requireThatMatchQueryIsTranslatedToWordItem() {
		Item root = transformedRoot("{\"query\":{\"match\":{\"title\":\"vespa\"}}}");

		WordItem word = assertInstanceOf(WordItem.class, root);
		assertEquals("title", word.getIndexName());
		assertEquals("vespa", word.getWord());
	}

	@Test
	void requireThatTermQueryIsTranslatedToWordItem() {
		Item root = transformedRoot("{\"query\":{\"term\":{\"category\":\"search\"}}}");

		WordItem word = assertInstanceOf(WordItem.class, root);
		assertEquals("category", word.getIndexName());
		assertEquals("search", word.getWord());
	}

	@Test
	void requireThatRangeQueryIsTranslatedToRangeItem() {
		Item root = transformedRoot("{\"query\":{\"range\":{\"price\":{\"gte\":10,\"lt\":20}}}}");

		RangeItem range = assertInstanceOf(RangeItem.class, root);
		assertEquals("price", range.getIndexName());
		assertEquals(10L, range.getFrom().longValue());
		assertEquals(20L, range.getTo().longValue());
		assertTrue(range.getFromLimit().isInclusive());
		assertFalse(range.getToLimit().isInclusive());
	}

	@Test
	void requireThatBoolMustQueryIsTranslatedToAndItem() {
		Item root = transformedRoot("{\"query\":{\"bool\":{\"must\":[{\"match\":{\"title\":\"vespa\"}},{\"term\":{\"category\":\"search\"}}]}}}");

		AndItem and = assertInstanceOf(AndItem.class, root);
		assertEquals(2, and.getItemCount());

		WordItem first = assertInstanceOf(WordItem.class, and.getItem(0));
		WordItem second = assertInstanceOf(WordItem.class, and.getItem(1));
		assertEquals("title", first.getIndexName());
		assertEquals("vespa", first.getWord());
		assertEquals("category", second.getIndexName());
		assertEquals("search", second.getWord());
	}

	@Test
	void requireThatBoolShouldQueryIsTranslatedToOrItem() {
		Item root = transformedRoot("{\"query\":{\"bool\":{\"should\":[{\"term\":{\"status\":\"active\"}},{\"term\":{\"status\":\"pending\"}}]}}}");

		OrItem or = assertInstanceOf(OrItem.class, root);
		assertEquals(2, or.getItemCount());

		WordItem first = assertInstanceOf(WordItem.class, or.getItem(0));
		WordItem second = assertInstanceOf(WordItem.class, or.getItem(1));
		assertEquals("active", first.getWord());
		assertEquals("pending", second.getWord());
	}

	@Test
	void requireThatBoolMustNotQueryIsTranslatedToNotItem() {
		Item root = transformedRoot("{\"query\":{\"bool\":{\"must_not\":[{\"term\":{\"status\":\"deleted\"}}]}}}");

		NotItem not = assertInstanceOf(NotItem.class, root);
		assertEquals(2, not.getItemCount());
		assertInstanceOf(TrueItem.class, not.getItem(0));

		WordItem negated = assertInstanceOf(WordItem.class, not.getItem(1));
		assertEquals("status", negated.getIndexName());
		assertEquals("deleted", negated.getWord());
	}

	@Test
	void requireThatMalformedJsonThrowsIllegalInputException() {
		Query query = new Query("");
		query.properties().set("elasticsearch.query", "{\"query\":{\"match\":");

		assertThrows(IllegalInputException.class, () -> execution.search(query));
	}

	@Test
	void requireThatUnsupportedQueryTypeThrowsIllegalInputException() {
		Query query = new Query("");
		query.properties().set("elasticsearch.query", "{\"query\":{\"prefix\":{\"title\":\"ve\"}}}");

		assertThrows(IllegalInputException.class, () -> execution.search(query));
	}

	private Item transformedRoot(String elasticsearchQuery) {
		Query query = new Query("");
		query.properties().set("elasticsearch.query", elasticsearchQuery);
		execution.search(query);
		return query.getModel().getQueryTree().getRoot();
	}

}
