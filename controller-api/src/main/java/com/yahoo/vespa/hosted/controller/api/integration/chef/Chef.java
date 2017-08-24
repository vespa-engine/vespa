// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.chef;


import com.yahoo.vespa.hosted.controller.api.integration.chef.rest.ChefEnvironment;
import com.yahoo.vespa.hosted.controller.api.integration.chef.rest.ChefNode;
import com.yahoo.vespa.hosted.controller.api.integration.chef.rest.ChefResource;
import com.yahoo.vespa.hosted.controller.api.integration.chef.rest.Client;
import com.yahoo.vespa.hosted.controller.api.integration.chef.rest.CookBook;
import com.yahoo.vespa.hosted.controller.api.integration.chef.rest.NodeResult;
import com.yahoo.vespa.hosted.controller.api.integration.chef.rest.PartialNodeResult;

import java.net.URL;
import java.util.List;

public interface Chef {

    ChefResource getApi();

    ChefNode getNode(String name);

    Client getClient(String name);

    ChefNode deleteNode(String name);

    Client deleteClient(String name);

    NodeResult searchNodeByFQDN(String fqdn);

    NodeResult searchNodes(String query);

    PartialNodeResult partialSearchNodes(String query, List<AttributeMapping> attributeMappings);

    void copyChefEnvironment(String fromEnvironmentName, String toEnvironmentName);

    ChefEnvironment getChefEnvironment(String environmentName);

    CookBook getCookbook(String cookbookName, String cookbookVersion);

    String downloadResource(URL resourceURL);

}
