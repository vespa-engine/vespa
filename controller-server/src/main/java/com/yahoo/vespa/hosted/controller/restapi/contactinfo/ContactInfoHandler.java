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
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.vespa.hosted.controller.restapi.SlimeJsonResponse;
import com.yahoo.vespa.hosted.controller.restapi.StringResponse;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Contact;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

/**
 * This implements the contactinfo/v1 API which allows getting and feeding
 * contact information for a given tenant.
 *
 * @author olaa
 */
public class ContactInfoHandler extends LoggingRequestHandler {

    private final Controller controller;

    public ContactInfoHandler(Context ctx, Controller controller) {
        super(ctx);
        this.controller = controller;
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
        if (path.matches("/contactinfo/v1/tenant/{tenant}")) return getContactInfo(path.get("tenant"));
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse post(HttpRequest request) {
        Path path = new Path(request.getUri().getPath());
        if (path.matches("/contactinfo/v1/tenant/{tenant}")) return postContactInfo(path.get("tenant"), request);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse getContactInfo(String tenantName) {
        Optional<AthenzTenant> tenant = controller.tenants().athenzTenant(TenantName.from(tenantName));
        if (!tenant.isPresent()) {
            return ErrorResponse.notFoundError("Invalid tenant " + tenantName);
        }
        Optional<Contact> contact = tenant.get().contact();
        if (contact.isPresent()) {
            return new SlimeJsonResponse(contactToSlime(contact.get()));
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
        catch (IOException e) {
            return ErrorResponse.notFoundError("Unable to create Contact object from request data");
        }
    }

    private Contact getContactFromRequest(HttpRequest request) throws IOException {
        Slime slime = SlimeUtils.jsonToSlime(IOUtils.readBytes(request.getData(), 1000 * 1000));
        return contactFromSlime(slime);
    }

    protected static Slime contactToSlime(Contact contact) {
        Slime slime = new Slime();
        Cursor cursor = slime.setObject();
        cursor.setString("url", contact.url().toString());
        cursor.setString("issueTrackerUrl", contact.issueTrackerUrl().toString());
        cursor.setString("propertyUrl", contact.propertyUrl().toString());
        Cursor personsCursor = cursor.setArray("persons");
        for (List<String> personList : contact.persons()) {
            Cursor sublist = personsCursor.addArray();
            for(String person : personList) {
                sublist.addString(person);
            }
        }
        return slime;
    }

    protected static Contact contactFromSlime(Slime slime) {
        Inspector inspector = slime.get();
        URI propertyUrl = URI.create(inspector.field("propertyUrl").asString());
        URI url = URI.create(inspector.field("url").asString());
        URI issueTrackerUrl = URI.create(inspector.field("issueTrackerUrl").asString());
        Inspector personInspector = inspector.field("persons");
        List<List<String>> personList = new ArrayList<>();
        personInspector.traverse((ArrayTraverser) (index, entry) -> {
            List<String> subList = new ArrayList<>();
            entry.traverse((ArrayTraverser) (idx, subEntry) -> {
                subList.add(subEntry.asString());
            });
            personList.add(subList);
        });
        return new Contact(url, propertyUrl, issueTrackerUrl, personList);
    }

}
