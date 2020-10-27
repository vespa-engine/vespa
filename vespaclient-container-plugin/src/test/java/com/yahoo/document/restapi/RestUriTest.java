// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi;

import org.apache.http.client.utils.URIBuilder;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThrows;

public class RestUriTest {

    URI createUri(String path, String query) throws URISyntaxException {
        return new URIBuilder()
	    .addParameter("foo", "bar")
	    .setHost("host")
	    .setScheme("http")
	    .setPort(666)
	    .setPath(path)
	    .setCustomQuery(query)
	    .setFragment("fargment").build();
    }

    @Test
	public void testBasic() throws Exception {
        RestUri restUri = new RestUri(createUri("/document/v1/namespace/doctype/docid/myid", "query"));
        assertThat(restUri.getDocId(), is("myid"));
        assertThat(restUri.getDocumentType(), is("doctype"));
        assertThat(restUri.getNamespace(), is("namespace"));
        assertThat(restUri.getGroup(), is(Optional.<RestUri.Group>empty()));
        assertThat(restUri.generateFullId(), is("id:namespace:doctype::myid"));
    }

    @Test
    public void encodingSlashes() throws Exception {
        // Try with slashes encoded.
        final String id = " !\"øæåp/:;&,.:;'1Q";
        String encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8.name());
        RestUri restUri = new RestUri(URI.create("/document/v1/namespace/doctype/docid/" + encodedId));
        assertThat(restUri.getDocId(), is(id));
        assertThat(restUri.getDocumentType(), is("doctype"));
        assertThat(restUri.getNamespace(), is("namespace"));
        assertThat(restUri.getGroup(), is(Optional.<RestUri.Group>empty()));
        assertThat(restUri.generateFullId(), is("id:namespace:doctype::" + id));
    }

    @Test
    public void encodingSlashes2() throws Exception {
        // This will decode the slashes.
        final String id = " !\"øæåp/:;&,.:;'1Q ";
        RestUri restUri = new RestUri(createUri("/document/v1/namespace/doctype/docid/" + id, "query"));
        assertThat(restUri.getDocId(), is(id));
        assertThat(restUri.getDocumentType(), is("doctype"));
        assertThat(restUri.getNamespace(), is("namespace"));
        assertThat(restUri.getGroup(), is(Optional.<RestUri.Group>empty()));
        assertThat(restUri.generateFullId(), is("id:namespace:doctype::" + id));
    }


    @Test
    public void testVisit() throws Exception {
        RestUri restUri = new RestUri(createUri("/document/v1/namespace/doctype/docid/", "query"));
        assertThat(restUri.getDocId(), is(""));
        assertThat(restUri.getDocumentType(), is("doctype"));
        assertThat(restUri.getNamespace(), is("namespace"));
        assertThat(restUri.getGroup(), is(Optional.<RestUri.Group>empty()));
        assertThat(restUri.generateFullId(), is("id:namespace:doctype::"));
    }

    @Test
	public void testOneSlashTooMuchWhichIsFine() throws Exception {
        RestUri restUri = new RestUri(createUri("/document/v1/namespace/doctype/docid/myid:342:23/wrong", ""));
        assertThat(restUri.getDocId(), is("myid:342:23/wrong"));
    }

    @Test
	public void testGroupG() throws Exception {
        RestUri restUri = new RestUri(createUri("/document/v1/namespace/doctype/group/group/myid", ""));
        assertThat(restUri.getDocId(), is("myid"));
        assertThat(restUri.getDocumentType(), is("doctype"));
        assertThat(restUri.getGroup().get().name, is('g'));
        assertThat(restUri.getGroup().get().value, is("group"));
        assertThat(restUri.generateFullId(), is("id:namespace:doctype:g=group:myid"));
    }

    @Test
    public void testGroupUrlDecode() throws Exception {
        RestUri restUri = new RestUri(createUri("/document/v1/namespace/doctype/group/group#123/myid", ""));
        assertThat(restUri.getDocId(), is("myid"));
        assertThat(restUri.getDocumentType(), is("doctype"));
        assertThat(restUri.getGroup().get().name, is('g'));
        assertThat(restUri.getGroup().get().value, is("group#123"));
        assertThat(restUri.generateFullId(), is("id:namespace:doctype:g=group#123:myid"));
    }

    @Test
	public void testGroupN() throws Exception {
        RestUri restUri = new RestUri(createUri("/document/v1/namespace/doctype/number/group/myid", ""));
        assertThat(restUri.getGroup().get().name, is('n'));
        assertThat(restUri.getGroup().get().value, is("group"));
    }

    @Test
	public void testGroupUnknown() {
        assertThrows(RestApiException.class, () -> new RestUri(createUri("/document/v1/namespace/doctype/Q/myid", "")));
    }

    @Test
    public void testDocIdAsIs() throws Exception {
        RestUri restUri = new RestUri(new URI("/document/v1/test/newsarticle/docid/http%3a%2f%2fvn.news.yahoo.com%2fgi-th-ng-t-n-ng-khoa-h-205000458.html").normalize());
        assertThat(restUri.getNamespace(), is("test"));
        assertThat(restUri.getDocumentType(), is("newsarticle"));
        assertThat(restUri.getDocId(), is("http://vn.news.yahoo.com/gi-th-ng-t-n-ng-khoa-h-205000458.html"));
        assertThat(restUri.generateFullId(), is("id:test:newsarticle::http://vn.news.yahoo.com/gi-th-ng-t-n-ng-khoa-h-205000458.html"));
    }

}
