package com.yahoo.vespa.tenant.cd.http;

import java.net.http.HttpClient;

public class HttpEndpoint {

    HttpClient client = HttpClient.newBuilder().build();

}
