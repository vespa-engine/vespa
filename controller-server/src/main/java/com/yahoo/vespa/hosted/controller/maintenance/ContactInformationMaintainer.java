// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.api.authority.config.ApiAuthorityConfig;
import com.yahoo.config.provision.SystemName;
import com.yahoo.log.LogLevel;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Organization;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.tenant.Contact;
import com.yahoo.yolean.Exceptions;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Periodically fetch and store contact information for tenants.
 *
 * @author mpolden
 */
public class ContactInformationMaintainer extends Maintainer {

    private static final Logger log = Logger.getLogger(ContactInformationMaintainer.class.getName());

    private final Organization organization;
    private final String[] baseUris;
    private CloseableHttpClient httpClient = HttpClientBuilder.create().build();

    public ContactInformationMaintainer(Controller controller, Duration interval, JobControl jobControl, Organization organization, ApiAuthorityConfig apiAuthorityConfig) {
        super(controller, interval, jobControl, null, EnumSet.of(SystemName.cd, SystemName.main));
        this.organization = Objects.requireNonNull(organization, "organization must be non-null");
        this.baseUris = apiAuthorityConfig.authorities().split(",");
    }

    // The maintainer will eventually feed contact info to systems other than its own, determined by the baseUris list.
    @Override
    protected void maintain() {
        for (String baseUri : baseUris) {
            for (String tenantName : getTenantList(baseUri)) {
                Optional<PropertyId> tenantPropertyId = getPropertyId(tenantName, baseUri);
                if (!tenantPropertyId.isPresent())
                    continue;
                findContact(tenantPropertyId.get()).ifPresent(contact -> {
                    feedContact(tenantName, contact, baseUri);
                });
            }
        }
    }

    private void feedContact(String tenantName, Contact contact, String baseUri) {
        try  {
            CloseableHttpClient httpClient = HttpClientBuilder.create().build();
            String uri = baseUri + "contactinfo/v1/tenant/" + tenantName;
            HttpPost httpPost = new HttpPost(uri);
            httpPost.setEntity(contactToByteArrayEntity(contact));
            httpClient.execute(httpPost);
        } catch (Exception e) {
            log.log(LogLevel.WARNING, "Failed to update contact information for " + tenantName + ": " +
                    Exceptions.toMessageString(e) + ". Retrying in " +
                    maintenanceInterval());
        }
    }

    private ByteArrayEntity contactToByteArrayEntity(Contact contact) throws IOException {
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
        return new ByteArrayEntity(SlimeUtils.toJsonBytes(slime));
    }

    private List<String> getTenantList(String baseUri) {
        List<String> tenantList = new ArrayList<>();
        HttpGet getRequest = new HttpGet(baseUri + "application/v4/tenant/");
        try {
            HttpResponse response = httpClient.execute(getRequest);
            Slime slime = SlimeUtils.jsonToSlime(EntityUtils.toByteArray(response.getEntity()));
            Inspector inspector = slime.get();
            inspector.traverse((ArrayTraverser) (index, tenant) -> {
                String tenantType = tenant.field("metaData").field("type").asString();
                if (tenantType.equalsIgnoreCase("athens")) {
                    tenantList.add(tenant.field("tenant").asString());
                }
            });
        } catch (IOException e) {
            log.log(LogLevel.WARNING, "Failed to get tenant list from base URI: " + baseUri +
                    Exceptions.toMessageString(e) + ". Retrying in " +
                    maintenanceInterval());
        }
        return tenantList;
    }

    private Optional<PropertyId> getPropertyId(String tenantName, String baseUri) {
        Optional<PropertyId> propertyId = Optional.empty();
        HttpGet getRequest = new HttpGet(baseUri + "application/v4/tenant/" + tenantName);
        try {
            HttpResponse response = httpClient.execute(getRequest);
            Slime slime = SlimeUtils.jsonToSlime(EntityUtils.toByteArray(response.getEntity()));
            Inspector inspector = slime.get();
            if (!inspector.field("propertyId").valid()) {
                log.log(LogLevel.WARNING, "Unable to get property id for " + tenantName);
                return Optional.empty();
            }
            propertyId = Optional.of(new PropertyId(inspector.field("propertyId").asString()));
        } catch (IOException e) {
            log.log(LogLevel.WARNING, "Unable to get property idfor " + tenantName, e);
        }
        return propertyId;
    }

    /** Find contact information for given tenant */
    private Optional<Contact> findContact(PropertyId propertyId) {
        List<List<String>> persons = organization.contactsFor(propertyId)
                                                 .stream()
                                                 .map(personList -> personList.stream()
                                                                              .map(User::displayName)
                                                                              .collect(Collectors.toList()))
                                                 .collect(Collectors.toList());
        return Optional.of(new Contact(organization.contactsUri(propertyId),
                                       organization.propertyUri(propertyId),
                                       organization.issueCreationUri(propertyId),
                                       persons));
    }

}
