// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.yahoo.application.Application;
import com.yahoo.application.Networking;
import com.yahoo.application.container.handler.Request;
import com.yahoo.container.Container;
import com.yahoo.jdisc.http.server.jetty.JettyHttpServer;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Paths;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertThat;

public class RestApiTest {

    Application application;

    @Before
    public void setup() throws Exception {
        application = Application.fromApplicationPackage(Paths.get("src/test/rest-api-application"), Networking.enable);
    }

    @After
    public void tearDown() throws Exception {
        application.close();
    }

    String post_test_uri = "/document/v1/namespace/testdocument/docid/c";
    String post_test_doc = "{\n" +
            "\"foo\" :  \"bar\"," +
            "\"fields\": {\n" +
            "\"title\": \"This is the title\",\n" +
            "\"body\": \"This is the body\"" +
            "}" +
            "}";
    String post_test_response = "{\"id\":\"id:namespace:testdocument::c\"," +
            "\"pathId\":\"/document/v1/namespace/testdocument/docid/c\"}";

    // Run this test to manually do request against the REST-API with backend mock.
    @Ignore
    @Test
    public void blockingTest() throws Exception {
        System.out.println("Running on port " + getFirstListenPort());
        Thread.sleep(Integer.MAX_VALUE);
    }

    @Test
    public void testbasicPost() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + post_test_uri);
        HttpPost httpPost = new HttpPost(request.getUri());
        StringEntity entity = new StringEntity(post_test_doc, ContentType.create("application/json"));
        httpPost.setEntity(entity);
        String x  = doRest(httpPost);
        assertThat(x, is(post_test_response));
    }

    String post_test_uri_cond = "/document/v1/namespace/testdocument/docid/c?condition=foo";
    String post_test_doc_cond = "{\n" +
            "\"foo\" :  \"bar\"," +
            "\"fields\": {\n" +
            "\"title\": \"This is the title\",\n" +
            "\"body\": \"This is the body\"" +
            "}" +
            "}";
    String post_test_response_cond = "{\"id\":\"id:namespace:testdocument::c\"," +
            "\"pathId\":\"/document/v1/namespace/testdocument/docid/c\"}";

    @Test
    public void testConditionalPost() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + post_test_uri_cond);
        HttpPost httpPost = new HttpPost(request.getUri());
        StringEntity entity = new StringEntity(post_test_doc_cond, ContentType.create("application/json"));
        httpPost.setEntity(entity);
        String x  = doRest(httpPost);
        assertThat(x, is(post_test_response_cond));
    }

    @Test
    public void testEmptyPost() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + post_test_uri);
        HttpPost httpPost = new HttpPost(request.getUri());
        StringEntity entity = new StringEntity("", ContentType.create("application/json"));
        httpPost.setEntity(entity);
        String x  = doRest(httpPost);
        assertThat(x, containsString("Could not read document, no document?"));
    }

    String update_test_uri = "/document/v1/namespace/testdocument/docid/c";
    String update_test_doc = "{\n" +
            "\t\"fields\": {\n" +
            "\"title\": {\n" +
            "\"assign\": \"Oh lala\"\n" +
            "}\n" +
            "}\n" +
            "}\n";

    String update_test_response = "{\"id\":\"id:namespace:testdocument::c\"," +
            "\"pathId\":\"/document/v1/namespace/testdocument/docid/c\"}";

    @Test
    public void testbasicUpdate() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + update_test_uri);
        HttpPut httpPut = new HttpPut(request.getUri());
        StringEntity entity = new StringEntity(update_test_doc, ContentType.create("application/json"));
        httpPut.setEntity(entity);
        assertThat(doRest(httpPut), is(update_test_response));
        assertThat(getLog(), not(containsString("CREATE IF NON EXISTING IS TRUE")));
    }

    @Test
    public void testbasicUpdateCreateTrue() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + update_test_uri + "?create=true");
        HttpPut httpPut = new HttpPut(request.getUri());
        StringEntity entity = new StringEntity(update_test_doc, ContentType.create("application/json"));
        httpPut.setEntity(entity);
        assertThat(doRest(httpPut), is(update_test_response));
        assertThat(getLog(), containsString("CREATE IF NON EXISTENT IS TRUE"));
    }

    String update_test_create_if_non_existient_uri = "/document/v1/namespace/testdocument/docid/c";
    String update_test_create_if_non_existient_doc = "{\n" +
            "\"create\":true," +
            "\t\"fields\": {\n" +
            "\"title\": {\n" +
            "\"assign\": \"Oh lala\"\n" +
            "}\n" +
            "}\n" +
            "}\n";

    String update_test_create_if_non_existing_response = "{\"id\":\"id:namespace:testdocument::c\"," +
            "\"pathId\":\"/document/v1/namespace/testdocument/docid/c\"}";

    @Test
    public void testCreateIfNonExistingUpdateInDocTrue() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + update_test_create_if_non_existient_uri);
        HttpPut httpPut = new HttpPut(request.getUri());
        StringEntity entity = new StringEntity(update_test_create_if_non_existient_doc, ContentType.create("application/json"));
        httpPut.setEntity(entity);
        assertThat(doRest(httpPut), is(update_test_create_if_non_existing_response));
        assertThat(getLog(), containsString("CREATE IF NON EXISTENT IS TRUE"));

    }

    @Test
    public void testCreateIfNonExistingUpdateInDocTrueButQueryParamsFalse() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + update_test_create_if_non_existient_uri + "?create=false");
        HttpPut httpPut = new HttpPut(request.getUri());
        StringEntity entity = new StringEntity(update_test_create_if_non_existient_doc, ContentType.create("application/json"));
        httpPut.setEntity(entity);
        assertThat(doRest(httpPut), is(update_test_create_if_non_existing_response));
        assertThat(getLog(), not(containsString("CREATE IF NON EXISTENT IS TRUE")));

    }

    // Get logs through some hackish fetch method. Logs is something the mocked backend write.
    String getLog() throws IOException {
        // The mocked backend will throw a runtime exception wtih a log if delete is called three times..
        Request request = new Request("http://localhost:" + getFirstListenPort() + remove_test_uri);
        HttpDelete delete = new HttpDelete(request.getUri());
        doRest(delete);
        return doRest(delete);
    }


    String remove_test_uri = "/document/v1/namespace/testdocument/docid/c";
    String remove_test_response = "{\"id\":\"id:namespace:testdocument::c\"," +
            "\"pathId\":\"/document/v1/namespace/testdocument/docid/c\"}";

    @Test
    public void testbasicRemove() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + remove_test_uri);
        HttpDelete delete = new HttpDelete(request.getUri());
        assertThat(doRest(delete), is(remove_test_response));
    }

    String get_test_uri = "/document/v1/namespace/document-type/docid/c";
    String get_response_part1 = "\"pathId\":\"/document/v1/namespace/document-type/docid/c\"";
    String get_response_part2 = "\"id\":\"id:namespace:document-type::c\"";


    @Test
    public void testbasicGet() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + get_test_uri);
        HttpGet get = new HttpGet(request.getUri());
        final String rest = doRest(get);
        assertThat(rest, containsString(get_response_part1));
        assertThat(rest, containsString(get_response_part2));
    }

    String id_test_uri = "/document/v1/namespace/document-type/docid/f/u/n/n/y/!";
    String id_response_part1 = "\"pathId\":\"/document/v1/namespace/document-type/docid/f/u/n/n/y/!\"";
    String id_response_part2 = "\"id\":\"id:namespace:document-type::f/u/n/n/y/!\"";

    @Test
    public void testSlashesInId() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + id_test_uri);
        HttpGet get = new HttpGet(request.getUri());
        final String rest = doRest(get);
        assertThat(rest, containsString(id_response_part1));
        assertThat(rest, containsString(id_response_part2));
    }


    String get_enc_id = "!\":æøå@/& Q1+";
    // Space encoded as %20, not encoding !
    String get_enc_id_encoded_v1 =      "!%22%3A%C3%A6%C3%B8%C3%A5%40%2F%26%20Q1%2B";
    // Space encoded as +
    String get_enc_id_encoded_v2 = "%21%22%3A%C3%A6%C3%B8%C3%A5%40%2F%26+Q1%2B";
    String get_enc_test_uri_v1 = "/document/v1/namespace/document-type/docid/" + get_enc_id_encoded_v1;
    String get_enc_test_uri_v2 = "/document/v1/namespace/document-type/docid/" + get_enc_id_encoded_v2;
    String get_enc_response_part1 = "\"pathId\":\"/document/v1/namespace/document-type/docid/" + get_enc_id_encoded_v1 + "\"";
    String get_enc_response_part1_v2 = "\"pathId\":\"/document/v1/namespace/document-type/docid/" + get_enc_id_encoded_v2 + "\"";

    // JSON encode " as \"
    String get_enc_response_part2 = "\"id\":\"id:namespace:document-type::" + get_enc_id.replace("\"", "\\\"") + "\"";


    @Test
    public void testbasicEncodingV1() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + get_enc_test_uri_v1);
        HttpGet get = new HttpGet(request.getUri());
        final String rest = doRest(get);
        assertThat(rest, containsString(get_enc_response_part1));
        assertThat(rest, containsString(get_enc_response_part2));
    }

    @Test
    public void testbasicEncodingV2() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + get_enc_test_uri_v2);
        HttpGet get = new HttpGet(request.getUri());
        String rest = doRest(get);
        assertThat(rest, containsString(get_enc_response_part1_v2));
        assertThat(rest, containsString(get_enc_response_part2));
    }

    @Test
    public void get_fieldset_parameter_is_propagated() throws IOException {
        Request request = new Request(String.format("http://localhost:%s/document/v1/namespace/document-type/docid/bar?fieldSet=foo,baz", getFirstListenPort()));
        HttpGet get = new HttpGet(request.getUri());
        String rest = doRest(get);
        assertThat(rest, containsString("\"fieldset\":\"foo,baz\""));
    }

    String visit_test_uri = "/document/v1/namespace/document-type/docid/?continuation=abc";
    String visit_response_part1 = "\"documents\":[List of json docs, cont token abc, doc selection: '']";
    String visit_response_part2 = "\"continuation\":\"token\"";
    String visit_response_part3 = "\"pathId\":\"/document/v1/namespace/document-type/docid/\"";

    @Test
    public void testbasicVisit() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + visit_test_uri);
        HttpGet get = new HttpGet(request.getUri());
        String rest = doRest(get);
        assertThat(rest, containsString(visit_response_part1));
        assertThat(rest, containsString(visit_response_part2));
        assertThat(rest, containsString(visit_response_part3));
    }

    // TODO why is this a limitation?
    String visit_test_bad_uri = "/document/v1/namespace/document-type/group/abc?continuation=abc&selection=foo";
    String visit_test_bad_response = "Visiting does not support setting value for group/value in combination with expression";


    @Test
    public void testBadVisit() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + visit_test_bad_uri);
        HttpGet get = new HttpGet(request.getUri());
        String rest = doRest(get);
        assertThat(rest, containsString(visit_test_bad_response));
    }

    String visit_test_uri_selection_rewrite = "/document/v1/namespace/document-type/group/abc?continuation=abc";
    String visit_test_response_selection_rewrite = "doc selection: 'id.group='abc''";


    @Test
    public void testUseExpressionOnVisit() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + visit_test_uri_selection_rewrite);
        HttpGet get = new HttpGet(request.getUri());
        String rest = doRest(get);
        assertThat(rest, containsString(visit_test_response_selection_rewrite));
    }

    @Test
    public void wanted_document_count_returned_parameter_is_propagated() throws IOException {
        Request request = new Request(String.format("http://localhost:%s/document/v1/namespace/document-type/docid/?wantedDocumentCount=321", getFirstListenPort()));
        HttpGet get = new HttpGet(request.getUri());
        String rest = doRest(get);
        assertThat(rest, containsString("min docs returned: 321"));
    }

    @Test
    public void invalid_wanted_document_count_parameter_returns_error_response() throws IOException {
        Request request = new Request(String.format("http://localhost:%s/document/v1/namespace/document-type/docid/?wantedDocumentCount=aardvark", getFirstListenPort()));
        HttpGet get = new HttpGet(request.getUri());
        String rest = doRest(get);
        assertThat(rest, containsString("Invalid 'wantedDocumentCount' value. Expected positive integer"));
    }

    @Test
    public void negative_document_count_parameter_returns_error_response() throws IOException {
        Request request = new Request(String.format("http://localhost:%s/document/v1/namespace/document-type/docid/?wantedDocumentCount=-1", getFirstListenPort()));
        HttpGet get = new HttpGet(request.getUri());
        String rest = doRest(get);
        assertThat(rest, containsString("Invalid 'wantedDocumentCount' value. Expected positive integer"));
    }

    @Test
    public void visit_fieldset_parameter_is_propagated() throws IOException {
        Request request = new Request(String.format("http://localhost:%s/document/v1/namespace/document-type/docid/?fieldSet=foo,baz", getFirstListenPort()));
        HttpGet get = new HttpGet(request.getUri());
        String rest = doRest(get);
        assertThat(rest, containsString("field set: 'foo,baz'"));
    }

    @Test
    public void visit_concurrency_parameter_is_propagated() throws IOException {
        Request request = new Request(String.format("http://localhost:%s/document/v1/namespace/document-type/docid/?concurrency=42", getFirstListenPort()));
        HttpGet get = new HttpGet(request.getUri());
        String rest = doRest(get);
        assertThat(rest, containsString("concurrency: 42"));
    }

    @Test
    public void invalid_visit_concurrency_parameter_returns_error_response() throws IOException {
        Request request = new Request(String.format("http://localhost:%s/document/v1/namespace/document-type/docid/?concurrency=badgers", getFirstListenPort()));
        HttpGet get = new HttpGet(request.getUri());
        String rest = doRest(get);
        assertThat(rest, containsString("Invalid 'concurrency' value. Expected positive integer"));
    }

    private String doRest(HttpRequestBase request) throws IOException {
        HttpClient client = HttpClientBuilder.create().build();
        HttpResponse response = client.execute(request);
        assertThat(response.getEntity().getContentType().getValue().toString(), startsWith("application/json;"));
        HttpEntity entity = response.getEntity();
        return EntityUtils.toString(entity);
    }

    private String getFirstListenPort() {
        JettyHttpServer serverProvider =
                (JettyHttpServer) Container.get().getServerProviderRegistry().allComponents().get(0);
        return Integer.toString(serverProvider.getListenPort());
    }

}
