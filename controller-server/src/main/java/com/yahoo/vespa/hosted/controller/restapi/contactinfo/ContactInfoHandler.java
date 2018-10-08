// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.contactinfo;

import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.io.IOUtils;
import com.yahoo.restapi.Path;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Organization;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.vespa.hosted.controller.restapi.SlimeJsonResponse;
import com.yahoo.vespa.hosted.controller.restapi.StringResponse;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Contact;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * @author olaa
 */
public class ContactInfoHandler extends LoggingRequestHandler {

    private final Controller controller;
    private final Organization organization;

    public ContactInfoHandler(Context ctx, Controller controller, Organization organization) {
        super(ctx);
        this.controller = controller;
        this.organization = organization;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET:
                    return get(request);
                case POST:
                    return post(request);
                default:
                    return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is unsupported");
            }
        }
        catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse get(HttpRequest request) {
        Path path = new Path(request.getUri().getPath());
        if (path.matches("/contactinfo/v1/tenant/{tenant}")) return getContactInfo(path.get("tenant"), request);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse post(HttpRequest request) {
        Path path = new Path(request.getUri().getPath());
        if (path.matches("/contactinfo/v1/tenant/{tenant}")) return postContactInfo(path.get("tenant"), request);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse getContactInfo(String tenantName, HttpRequest request) {
        Optional<AthenzTenant> tenant = controller.tenants().athenzTenant(TenantName.from(tenantName));
        if (! tenant.isPresent()) {
            return ErrorResponse.notFoundError("Invalid tenant " + tenantName);
        }
        boolean useOpsDb = getUseOpsDbFromRequest(request);
        Optional<Contact> contact = Optional.empty();
        if (useOpsDb) {
            contact = findContactFromOpsDb(tenant.get());
        } else  {
            contact = tenant.get().contact();
        }
        if (contact.isPresent()) {
            return new SlimeJsonResponse(contact.get().toSlime());
        }
        return ErrorResponse.notFoundError("Could not find contact info for " + tenantName);
    }
    
    private HttpResponse postContactInfo(String tenantName, HttpRequest request) {
        try {
            Contact contact = getContactFromRequest(request);
            controller.tenants().lockIfPresent(TenantName.from(tenantName),
                    lockedTenant -> controller.tenants().store(lockedTenant.with(contact)));
            return new StringResponse("Added contact info for " + tenantName + " - " + contact.toString());
        }
        catch (URISyntaxException | IOException e) {
            return ErrorResponse.notFoundError("Unable to create Contact object from request data");
        }
    }

    private boolean getUseOpsDbFromRequest(HttpRequest request) {
        String query = request.getUri().getQuery();
        if (query == null) {
            return false;
        }
        
        HashMap<String, String> keyValPair = new HashMap<>();
        Arrays.stream(query.split("&")).forEach(pair -> {
            String[] splitPair = pair.split("=");
            keyValPair.put(splitPair[0], splitPair[1]);
        });

        if (keyValPair.containsKey("useOpsDb")) {
            return Boolean.valueOf(keyValPair.get("useOpsDb"));
        }
        return false;
    }
    private PropertyId getPropertyIdFromRequest(HttpRequest request) {
        return new PropertyId(request.getProperty("propertyId"));
    }

    private Contact getContactFromRequest(HttpRequest request) throws IOException, URISyntaxException {
        Slime slime = SlimeUtils.jsonToSlime(IOUtils.readBytes(request.getData(), 1000 * 1000));
        return Contact.fromSlime(slime);
    }

    private Optional<Contact> findContactFromOpsDb(AthenzTenant tenant) {
        if (!tenant.propertyId().isPresent()) {
            return Optional.empty();
        }
        List<List<String>> persons = organization.contactsFor(tenant.propertyId().get())
                .stream()
                .map(personList -> personList.stream()
                        .map(User::displayName)
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());
        return Optional.of(new Contact(organization.contactsUri(tenant.propertyId().get()),
                organization.propertyUri(tenant.propertyId().get()),
                organization.issueCreationUri(tenant.propertyId().get()),
                persons));
    }

}
