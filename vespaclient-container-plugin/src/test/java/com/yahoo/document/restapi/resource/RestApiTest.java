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
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.function.Function;

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

    private static class Response {
        final int code;
        final String body;

        Response(int code, String body) {
            this.code = code;
            this.body = body;
        }
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
        Response response = doRest(httpPost);
        assertThat(response.code, is(200));
        assertThat(response.body, is(post_test_response));
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
        Response response = doRest(httpPost);
        assertThat(response.code, is(200));
        assertThat(response.body, is(post_test_response_cond));
    }

    @Test
    public void testEmptyPost() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + post_test_uri);
        HttpPost httpPost = new HttpPost(request.getUri());
        StringEntity entity = new StringEntity("", ContentType.create("application/json"));
        httpPost.setEntity(entity);
        assertHttp400ResponseContains(doRest(httpPost), "Could not read document, no document?");
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
        Response response = doRest(httpPut);
        assertThat(response.code, is(200));
        assertThat(response.body, is(update_test_response));
        assertThat(getLog(), not(containsString("CREATE IF NON EXISTING IS TRUE")));
    }

    @Test
    public void testbasicUpdateCreateTrue() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + update_test_uri + "?create=true");
        HttpPut httpPut = new HttpPut(request.getUri());
        StringEntity entity = new StringEntity(update_test_doc, ContentType.create("application/json"));
        httpPut.setEntity(entity);
        Response response = doRest(httpPut);
        assertThat(response.code, is(200));
        assertThat(response.body, is(update_test_response));
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
        assertThat(doRest(httpPut).body, is(update_test_create_if_non_existing_response));
        assertThat(getLog(), containsString("CREATE IF NON EXISTENT IS TRUE"));
    }

    @Test
    public void testCreateIfNonExistingUpdateInDocTrueButQueryParamsFalse() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + update_test_create_if_non_existient_uri + "?create=false");
        HttpPut httpPut = new HttpPut(request.getUri());
        StringEntity entity = new StringEntity(update_test_create_if_non_existient_doc, ContentType.create("application/json"));
        httpPut.setEntity(entity);
        assertThat(doRest(httpPut).body, is(update_test_create_if_non_existing_response));
        assertThat(getLog(), not(containsString("CREATE IF NON EXISTENT IS TRUE")));
    }

    @Test
    public void bogus_create_parameter_value_returns_http_400_error() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + update_test_uri + "?create=batman");
        HttpPut httpPut = new HttpPut(request.getUri());
        StringEntity entity = new StringEntity(update_test_doc, ContentType.create("application/json"));
        httpPut.setEntity(entity);
        assertHttp400ResponseContains(doRest(httpPut), "Invalid value for 'create' parameter: Must be empty, true, or false but was 'batman'");
    }

    // Get logs through some hackish fetch method. Logs is something the mocked backend write.
    String getLog() throws IOException {
        // The mocked backend will throw a runtime exception with a log if delete is called three times..
        Request request = new Request("http://localhost:" + getFirstListenPort() + remove_test_uri);
        HttpDelete delete = new HttpDelete(request.getUri());
        doRest(delete);
        return doRest(delete).body;
    }


    String remove_test_uri = "/document/v1/namespace/testdocument/docid/c";
    String remove_test_response = "{\"id\":\"id:namespace:testdocument::c\"," +
            "\"pathId\":\"/document/v1/namespace/testdocument/docid/c\"}";

    @Test
    public void testbasicRemove() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + remove_test_uri);
        HttpDelete delete = new HttpDelete(request.getUri());
        Response response = doRest(delete);
        assertThat(response.code, is(200));
        assertThat(response.body, is(remove_test_response));
    }

    String get_test_uri = "/document/v1/namespace/document-type/docid/c";
    String get_response_part1 = "\"pathId\":\"/document/v1/namespace/document-type/docid/c\"";
    String get_response_part2 = "\"id\":\"id:namespace:document-type::c\"";


    @Test
    public void testbasicGet() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + get_test_uri);
        HttpGet get = new HttpGet(request.getUri());
        Response response = doRest(get);
        assertThat(response.code, is(404)); // Mock returns Not Found
        assertThat(response.body, containsString(get_response_part1));
        assertThat(response.body, containsString(get_response_part2));
    }

    String id_test_uri = "/document/v1/namespace/document-type/docid/f/u/n/n/y/!";
    String id_response_part1 = "\"pathId\":\"/document/v1/namespace/document-type/docid/f/u/n/n/y/!\"";
    String id_response_part2 = "\"id\":\"id:namespace:document-type::f/u/n/n/y/!\"";

    @Test
    public void testSlashesInId() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + id_test_uri);
        HttpGet get = new HttpGet(request.getUri());
        Response response = doRest(get);
        assertThat(response.code, is(404)); // Mock returns Not Found
        assertThat(response.body, containsString(id_response_part1));
        assertThat(response.body, containsString(id_response_part2));
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
        Response response = doRest(get);
        assertThat(response.code, is(404)); // Mock returns Not Found
        assertThat(response.body, containsString(get_enc_response_part1));
        assertThat(response.body, containsString(get_enc_response_part2));
    }

    @Test
    public void testbasicEncodingV2() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + get_enc_test_uri_v2);
        HttpGet get = new HttpGet(request.getUri());
        Response response = doRest(get);
        assertThat(response.code, is(404)); // Mock returns Not Found
        assertThat(response.body, containsString(get_enc_response_part1_v2));
        assertThat(response.body, containsString(get_enc_response_part2));
    }

    @Test
    public void get_fieldset_parameter_is_propagated() {
        Request request = new Request(String.format("http://localhost:%s/document/v1/namespace/document-type/docid/bar?fieldSet=foo,baz", getFirstListenPort()));
        HttpGet get = new HttpGet(request.getUri());
        assertHttp200ResponseContains(doRest(get), "\"fieldset\":\"foo,baz\"");
    }

    @Test
    public void get_cluster_parameter_is_propagated() {
        Request request = new Request(String.format("http://localhost:%s/document/v1/namespace/document-type/docid/bar?cluster=my_cool_cluster", getFirstListenPort()));
        HttpGet get = new HttpGet(request.getUri());
        assertHttp200ResponseContains(doRest(get), "\"cluster\":\"my_cool_cluster\"");
    }

    String visit_test_uri = "/document/v1/namespace/document-type/docid/?continuation=abc";
    String visit_response_part1 = "\"documents\":[List of json docs, cont token abc, doc selection: '']";
    String visit_response_part2 = "\"continuation\":\"token\"";
    String visit_response_part3 = "\"pathId\":\"/document/v1/namespace/document-type/docid/\"";

    @Test
    public void testbasicVisit() throws Exception {
        Request request = new Request("http://localhost:" + getFirstListenPort() + visit_test_uri);
        HttpGet get = new HttpGet(request.getUri());
        Response response = doRest(get);
        assertThat(response.code, is(200));
        assertThat(response.body, containsString(visit_response_part1));
        assertThat(response.body, containsString(visit_response_part2));
        assertThat(response.body, containsString(visit_response_part3));
    }

    private static String encoded(String original) {
        try {
            return URLEncoder.encode(original, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String defaultPathPrefix() {
        return "namespace/document-type/";
    }

    private Response performV1RestCall(String pathPrefix, String pathSuffix, Function<Request, HttpRequestBase> methodOp) {
        try {
            Request request = new Request(String.format("http://localhost:%s/document/v1/%s%s",
                    getFirstListenPort(), pathPrefix, pathSuffix));
            HttpRequestBase restOp = methodOp.apply(request);
            return doRest(restOp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Response performV1GetRestCall(String pathSuffix) {
        return performV1RestCall(defaultPathPrefix(), pathSuffix, (request) -> new HttpGet(request.getUri()));
    }

    private void doTestRootPathNotAccepted(Function<Request, HttpRequestBase> methodOpFactory) {
        Response response = performV1RestCall("", "", methodOpFactory);
        assertHttp400ResponseContains(response, "Root /document/v1/ requests only supported for HTTP GET");
    }

    @Test
    public void root_api_path_not_accepted_for_http_put() {
        doTestRootPathNotAccepted((request) -> new HttpPut(request.getUri()));
    }

    @Test
    public void root_api_path_not_accepted_for_http_post() {
        doTestRootPathNotAccepted((request) -> new HttpPost(request.getUri()));
    }

    @Test
    public void root_api_path_not_accepted_for_http_delete() {
        doTestRootPathNotAccepted((request) -> new HttpDelete(request.getUri()));
    }

    private void assertResultingDocumentSelection(String suffix, String expected) {
        Response response = performV1GetRestCall(suffix);
        assertHttp200ResponseContains(response, String.format("doc selection: '%s'", expected));
    }

    @Test
    public void testUseExpressionOnVisit() throws Exception {
        assertResultingDocumentSelection("group/abc?continuation=xyz", "id.group=='abc'");
    }

    private void assertGroupDocumentSelection(String group, String expected) {
        assertResultingDocumentSelection("group/" + encoded(group), expected);
    }

    @Test
    public void group_strings_are_escaped() {
        assertGroupDocumentSelection("'", "id.group=='\\''");
        assertGroupDocumentSelection("hello 'world'", "id.group=='hello \\'world\\''");
        assertGroupDocumentSelection("' goodbye moon", "id.group=='\\' goodbye moon'");
    }

    private void assertNumericIdFailsParsing(String id) {
        Response response = performV1GetRestCall(String.format("number/%s", encoded(id)));
        assertHttp400ResponseContains(response, "Failed to parse numeric part of selection URI");
    }

    @Test
    public void invalid_numeric_id_returns_error() {
        assertNumericIdFailsParsing("123a");
        assertNumericIdFailsParsing("a123");
        assertNumericIdFailsParsing("0x1234");
        assertNumericIdFailsParsing("\u0000");
    }

    @Test
    public void non_text_group_string_character_returns_error() {
        Response response = performV1GetRestCall(String.format("group/%s", encoded("\u001f")));
        assertHttp400ResponseContains(response, "Failed to parse group part of selection URI; contains invalid text code point U001F");
    }

    @Test
    public void can_specify_numeric_id_without_explicit_selection() {
        assertResultingDocumentSelection("number/1234", "id.user==1234");
    }

    @Test
    public void can_specify_group_id_without_explicit_selection() {
        assertResultingDocumentSelection("group/foo", "id.group=='foo'");
    }

    @Test
    public void can_specify_both_numeric_id_and_explicit_selection() {
        assertResultingDocumentSelection(String.format("number/1234?selection=%s", encoded("1 != 2")),
                "id.user==1234 and (1 != 2)");
    }

    @Test
    public void can_specify_both_group_id_and_explicit_selection() {
        assertResultingDocumentSelection(String.format("group/bar?selection=%s", encoded("3 != 4")),
                "id.group=='bar' and (3 != 4)");
    }

    private void assertDocumentSelectionFailsParsing(String expression) {
        Response response = performV1GetRestCall(String.format("number/1234?selection=%s", encoded(expression)));
        assertHttp400ResponseContains(response, "Failed to parse expression given in 'selection' parameter. Must be a complete and valid sub-expression.");
    }

    // Make sure that typoing the selection parameter doesn't corrupt the entire selection expression
    @Test
    public void explicit_selection_sub_expression_is_validated_for_completeness() {
        assertDocumentSelectionFailsParsing("1 +");
        assertDocumentSelectionFailsParsing(") or true");
        assertDocumentSelectionFailsParsing("((1 + 2)");
        assertDocumentSelectionFailsParsing("true) or (true");
    }

    @Test
    public void wanted_document_count_returned_parameter_is_propagated() {
        Request request = new Request(String.format("http://localhost:%s/document/v1/namespace/document-type/docid/?wantedDocumentCount=321", getFirstListenPort()));
        HttpGet get = new HttpGet(request.getUri());
        assertHttp200ResponseContains(doRest(get), "min docs returned: 321");
    }

    @Test
    public void invalid_wanted_document_count_parameter_returns_error_response() {
        Request request = new Request(String.format("http://localhost:%s/document/v1/namespace/document-type/docid/?wantedDocumentCount=aardvark", getFirstListenPort()));
        HttpGet get = new HttpGet(request.getUri());
        assertHttp400ResponseContains(doRest(get), "Invalid 'wantedDocumentCount' value. Expected positive integer");
    }

    @Test
    public void negative_document_count_parameter_returns_error_response() {
        Request request = new Request(String.format("http://localhost:%s/document/v1/namespace/document-type/docid/?wantedDocumentCount=-1", getFirstListenPort()));
        HttpGet get = new HttpGet(request.getUri());
        assertHttp400ResponseContains(doRest(get), "Invalid 'wantedDocumentCount' value. Expected positive integer");
    }

    @Test
    public void visit_fieldset_parameter_is_propagated() {
        Request request = new Request(String.format("http://localhost:%s/document/v1/namespace/document-type/docid/?fieldSet=foo,baz", getFirstListenPort()));
        HttpGet get = new HttpGet(request.getUri());
        assertHttp200ResponseContains(doRest(get), "field set: 'foo,baz'");
    }

    @Test
    public void visit_concurrency_parameter_is_propagated() {
        Request request = new Request(String.format("http://localhost:%s/document/v1/namespace/document-type/docid/?concurrency=42", getFirstListenPort()));
        HttpGet get = new HttpGet(request.getUri());
        assertHttp200ResponseContains(doRest(get), "concurrency: 42");
    }

    @Test
    public void root_api_visit_cluster_parameter_is_propagated() {
        Request request = new Request(String.format("http://localhost:%s/document/v1/?cluster=vaffel", getFirstListenPort()));
        HttpGet get = new HttpGet(request.getUri());
        assertHttp200ResponseContains(doRest(get), "cluster: 'vaffel'");
    }

    @Test
    public void root_api_visit_selection_parameter_is_propagated() {
        Request request = new Request(String.format("http://localhost:%s/document/v1/?cluster=foo&selection=yoshi", getFirstListenPort()));
        HttpGet get = new HttpGet(request.getUri());
        assertHttp200ResponseContains(doRest(get), "doc selection: 'yoshi'");
    }

    @Test
    public void root_api_visit_bucket_space_parameter_is_propagated() {
        Request request = new Request(String.format("http://localhost:%s/document/v1/?cluster=foo&bucketSpace=global", getFirstListenPort()));
        HttpGet get = new HttpGet(request.getUri());
        assertHttp200ResponseContains(doRest(get), "bucket space: 'global'");
    }

    @Test
    public void invalid_visit_concurrency_parameter_returns_error_response() {
        Request request = new Request(String.format("http://localhost:%s/document/v1/namespace/document-type/docid/?concurrency=badgers", getFirstListenPort()));
        HttpGet get = new HttpGet(request.getUri());
        assertHttp400ResponseContains(doRest(get), "Invalid 'concurrency' value. Expected positive integer");
    }

    private void assertHttpResponseContains(Response response, int expectedStatusCode, String expectedSubstring) {
        assertThat(response.code, is(expectedStatusCode));
        assertThat(response.body, containsString(expectedSubstring));
    }

    private void assertHttp200ResponseContains(Response response, String expectedSubstring) {
        assertHttpResponseContains(response, 200, expectedSubstring);
    }

    private void assertHttp400ResponseContains(Response response, String expectedSubstring) {
        assertHttpResponseContains(response, 400, expectedSubstring);
    }

    private Response doRest(HttpRequestBase request) {
        HttpClient client = HttpClientBuilder.create().build();
        try {
            HttpResponse response = client.execute(request);
            assertThat(response.getEntity().getContentType().getValue().toString(), startsWith("application/json;"));
            HttpEntity entity = response.getEntity();
            return new Response(response.getStatusLine().getStatusCode(), EntityUtils.toString(entity));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String getFirstListenPort() {
        JettyHttpServer serverProvider =
                (JettyHttpServer) Container.get().getServerProviderRegistry().allComponents().get(0);
        return Integer.toString(serverProvider.getListenPort());
    }

}
