// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.chef;

import com.yahoo.vespa.hosted.controller.api.integration.chef.rest.ChefEnvironment;
import com.yahoo.vespa.hosted.controller.api.integration.chef.rest.ChefNode;
import com.yahoo.vespa.hosted.controller.api.integration.chef.rest.ChefResource;
import com.yahoo.vespa.hosted.controller.api.integration.chef.rest.Client;
import com.yahoo.vespa.hosted.controller.api.integration.chef.rest.CookBook;
import com.yahoo.vespa.hosted.controller.api.integration.chef.rest.NodeResult;
import com.yahoo.vespa.hosted.controller.api.integration.chef.rest.PartialNode;
import com.yahoo.vespa.hosted.controller.api.integration.chef.rest.PartialNodeResult;

import javax.ws.rs.NotFoundException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author mpolden
 */
public class ChefMock implements Chef {

    private final NodeResult result;
    private final PartialNodeResult partialResult;
    private final List<String> chefEnvironments;

    public ChefMock() {
        result = new NodeResult();
        result.rows = new ArrayList<>();
        partialResult = new PartialNodeResult();
        partialResult.rows = new ArrayList<>();
        chefEnvironments = new ArrayList<>();
        chefEnvironments.add("hosted-verified-prod");
        chefEnvironments.add("hosted-infra-cd");
    }

    @Override
    public ChefResource getApi() {
        return null;
    }

    @Override
    public ChefNode getNode(String name) {
        return null;
    }

    @Override
    public Client getClient(String name) {
        return null;
    }

    @Override
    public ChefNode deleteNode(String name) {
        return null;
    }

    @Override
    public Client deleteClient(String name) {
        return null;
    }

    public ChefMock addSearchResult(ChefNode node) {
        result.rows.add(node);
        return this;
    }

    public ChefMock addPartialResult(List<PartialNode> partialNodes) {
        partialResult.rows.addAll(partialNodes);
        return this;
    }

    @Override
    public NodeResult searchNodeByFQDN(String fqdn) {
        return result;
    }

    @Override
    public NodeResult searchNodes(String query) {
        return result;
    }

    @Override
    public PartialNodeResult partialSearchNodes(String query, List<AttributeMapping> returnAttributes) {
        PartialNodeResult partialNodeResult = new PartialNodeResult();
        partialNodeResult.rows = new ArrayList<>();
        partialNodeResult.rows.addAll(partialResult.rows);
        result.rows.stream()
                   .map(chefNode -> {
                       Map<String, String> data = new HashMap<>();
                       data.put("fqdn", chefNode.name);
                       return new PartialNode(data);
                   })
                   .forEach(node -> partialNodeResult.rows.add(node));
        return partialNodeResult;
    }

    @Override
    public void copyChefEnvironment(String fromEnvironmentName, String toEnvironmentName) {
        if(!chefEnvironments.contains(fromEnvironmentName)) {
            throw new NotFoundException(String.format("Source chef environment %s does not exist", fromEnvironmentName));
        }
        chefEnvironments.add(toEnvironmentName);
    }

    @Override
    public ChefEnvironment getChefEnvironment(String environmentName) {
        return null;
    }

    @Override
    public CookBook getCookbook(String cookbookName, String cookbookVersion) {
        return null;
    }

    @Override
    public String downloadResource(URL resourceURL) {
        return "";
    }
}

