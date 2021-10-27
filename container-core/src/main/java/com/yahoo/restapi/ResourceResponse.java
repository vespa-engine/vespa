// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.slime.Slime;

import java.net.URI;
import java.util.List;

/**
 * Returns a response containing an array of links to sub-resources
 * 
 * @author bratseth
 */
public class ResourceResponse extends SlimeJsonResponse {

    public ResourceResponse(URI parentUrl, List<String> subResources) {
        super(200, toSlime(parentUrl, subResources));
    }

    public ResourceResponse(URI parentUrl, String ... subResources) {
        this(parentUrl, List.of(subResources));
    }

    public ResourceResponse(HttpRequest request, String ... subResources) {
        this(request.getUri(), subResources);
    }

    private static Slime toSlime(URI parentUrl, List<String> subResources) {
        var slime = new Slime();
        var resourceArray = slime.setObject().setArray("resources");
        for (var subResource : subResources) {
            var resourceEntry = resourceArray.addObject();
            resourceEntry.setString("url", new UriBuilder(parentUrl).append(subResource)
                                                             .withTrailingSlash()
                                                             .toString());
        }
        return slime;
    }

}
