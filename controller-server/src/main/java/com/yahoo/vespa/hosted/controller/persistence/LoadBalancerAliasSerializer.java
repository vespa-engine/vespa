// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.application.LoadBalancerAlias;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Serializer and deserializer for a {@link LoadBalancerAlias}.
 *
 * @author mortent
 */
public class LoadBalancerAliasSerializer {

    private static final String aliasesField = "aliases";
    private static final String idField = "id";
    private static final String aliasField = "alias";
    private static final String canonicalNameField = "canonicalName";

    public Slime toSlime(Set<LoadBalancerAlias> aliases) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor aliasArray = root.setArray(aliasesField);
        aliases.forEach(alias -> {
            Cursor nameObject = aliasArray.addObject();
            nameObject.setString(idField, alias.id());
            nameObject.setString(aliasField, alias.alias().value());
            nameObject.setString(canonicalNameField, alias.canonicalName().value());
        });
        return slime;
    }

    public Set<LoadBalancerAlias> fromSlime(ApplicationId owner, Slime slime) {
        Set<LoadBalancerAlias> names = new LinkedHashSet<>();
        slime.get().field(aliasesField).traverse((ArrayTraverser) (i, inspect) -> {
            names.add(new LoadBalancerAlias(owner,
                                            inspect.field(idField).asString(),
                                            HostName.from(inspect.field(aliasField).asString()),
                                            HostName.from(inspect.field(canonicalNameField).asString())));
        });

        return Collections.unmodifiableSet(names);
    }

}
