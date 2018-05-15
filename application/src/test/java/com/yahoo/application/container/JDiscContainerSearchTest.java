// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import com.yahoo.application.Networking;
import com.yahoo.application.container.searchers.AddHitSearcher;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.search.Query;
import com.yahoo.search.Result;

/**
 *
 * @author gjoranv
 * @since 5.1.15
 */
public class JDiscContainerSearchTest {
	@Test
	public void processing_and_rendering_works() throws Exception {
		final String searcherId = AddHitSearcher.class.getName();

		try (JDisc container = containerWithSearch(searcherId)) {
			byte[] rendered = container.search().processAndRender(ComponentSpecification.fromString("mychain"),
					ComponentSpecification.fromString("DefaultRenderer"), new Query(""));
			String renderedAsString = new String(rendered, "utf-8");
			assertThat(renderedAsString, containsString(searcherId));
		}
	}

	@Test
	public void searching_works() throws Exception {
		final String searcherId = AddHitSearcher.class.getName();

		try (JDisc container = containerWithSearch(searcherId)) {
			Search searching = container.search();
			Result result = searching.process(ComponentSpecification.fromString("mychain"), new Query(""));
			String hitTitle = result.hits().get(0).getField("title").toString();
			assertThat(hitTitle, is(searcherId));
		}
	}

	public JDisc containerWithSearch(String searcherId) {
		return JDisc.fromServicesXml("<container version=\"1.0\">" + //
				"<search>" + //
				"<chain id=\"mychain\">" + //
				"<searcher id=\"" + searcherId + "\"/>" + //
				"</chain>" + //
				"</search>" + //
				"</container>", Networking.disable);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void retrieving_search_from_container_without_search_is_illegal() throws Exception {
		try (JDisc container = JDisc.fromServicesXml("<container version=\"1.0\" />", Networking.disable)) {
			container.search(); // throws
		}
	}
}
