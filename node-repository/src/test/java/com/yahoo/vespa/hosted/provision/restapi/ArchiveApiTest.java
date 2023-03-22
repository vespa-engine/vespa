// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.SystemName;
import com.yahoo.text.Utf8;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/**
 * Test of the REST APIs provided by the node repository.
 * 
 * Note: This class is referenced from our operations documentation and must not be renamed/moved without updating that.
 * 
 * @author bratseth
 */
public class ArchiveApiTest {

    private RestApiTester tester;

    @Before
    public void createTester() {
        tester = new RestApiTester(SystemName.Public, CloudAccount.from("111222333444"));
    }

    @After
    public void closeTester() {
        tester.close();
    }

    @Test
    public void archive_uris() throws IOException {
        tester.assertPartialResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com"), "archiveUri", false);
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/archive"), "{\"archives\":[]}");

        assertResponse(new Request("http://localhost:8080/nodes/v2/archive/tenant/tenant3", Utf8.toBytes("{\"uri\": \"ftp://host/dir\"}"), Request.Method.PATCH),
                "{\"message\":\"Updated archive URI for tenant3\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/archive/tenant/tenant2", Utf8.toBytes("{\"uri\": \"s3://my-bucket/dir\"}"), Request.Method.PATCH),
                "{\"message\":\"Updated archive URI for tenant2\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/archive/account/777888999000", Utf8.toBytes("{\"uri\": \"s3://acc-bucket\"}"), Request.Method.PATCH),
                "{\"message\":\"Updated archive URI for 777888999000\"}");


        tester.assertPartialResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com"), "\"archiveUri\":\"ftp://host/dir/tenant3/application3/instance3/id3/host4/\"", true);
        tester.assertPartialResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost2.yahoo.com"), "\"archiveUri\":\"s3://acc-bucket/zoneapp/zoneapp/zoneapp/node-admin/dockerhost2/\"", true);
        assertFile(new Request("http://localhost:8080/nodes/v2/archive"), "archives.json");

        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/archive/tenant/tenant3", new byte[0], Request.Method.DELETE), "{\"message\":\"Removed archive URI for tenant3\"}");
        tester.assertPartialResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com"), "archiveUri", false);
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/archive/account/777888999000", new byte[0], Request.Method.DELETE), "{\"message\":\"Removed archive URI for 777888999000\"}");
        tester.assertPartialResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost2.yahoo.com"), "archiveUri", false);
    }


    private void assertFile(Request request, String file) throws IOException {
        tester.assertFile(request, file);
    }

    private void assertResponse(Request request, String response) throws IOException {
        tester.assertResponse(request, response);
    }

}
