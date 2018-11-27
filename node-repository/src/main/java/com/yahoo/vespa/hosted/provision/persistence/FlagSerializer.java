// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.provision.flag.Flag;
import com.yahoo.vespa.hosted.provision.flag.FlagId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author mpolden
 */
public class FlagSerializer {

    private static final String featureField = "feature";
    private static final String enabledField = "enabled";
    private static final String hostnamesField = "hostnames";
    private static final String applicationsField = "applications";

    public static byte[] toJson(Flag flag) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();

        root.setString(featureField, flag.id().serializedValue());
        root.setBool(enabledField, flag.isEnabled());

        Cursor nodeArray = root.setArray(hostnamesField);
        flag.hostnames().forEach(nodeArray::addString);

        Cursor applicationArray = root.setArray(applicationsField);
        flag.applications().forEach(application -> applicationArray.addString(application.serializedForm()));

        try {
            return SlimeUtils.toJsonBytes(slime);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Flag fromJson(byte[] data) {
        Inspector inspect = SlimeUtils.jsonToSlime(data).get();

        Set<String> hostnames = new LinkedHashSet<>();
        inspect.field(hostnamesField).traverse((ArrayTraverser) (i, hostname) -> hostnames.add(hostname.asString()));

        Set<ApplicationId> applications = new LinkedHashSet<>();
        inspect.field(applicationsField).traverse((ArrayTraverser) (i, application) -> {
            applications.add(ApplicationId.fromSerializedForm(application.asString()));
        });

        return new Flag(FlagId.fromSerializedForm(inspect.field(featureField).asString()),
                        inspect.field(enabledField).asBool(),
                        hostnames,
                        applications);
    }

}
