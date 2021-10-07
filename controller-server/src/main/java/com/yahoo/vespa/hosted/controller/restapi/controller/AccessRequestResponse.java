// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.restapi.controller;

import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzUser;

import java.util.Collection;

public class AccessRequestResponse extends SlimeJsonResponse {

    public AccessRequestResponse(Collection<AthenzUser> members) {
        super(toSlime(members));
    }

    private static Slime toSlime(Collection<AthenzUser> members) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor array = root.setArray("members");
        members.stream()
                .map(AthenzIdentity::getFullName)
                .forEach(array::addString);
        return slime;
    }
}
