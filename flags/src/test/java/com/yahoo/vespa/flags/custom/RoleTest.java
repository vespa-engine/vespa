// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.yahoo.test.json.Jackson;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RoleTest {

    @Test
    void deSerializesCorrectly() throws JsonProcessingException {
        String json = """
                    {
                    "roles": [
                    {
                        "name": "role1",
                            "members": ["u1@example.com", "u2@example.com"]
                    },
                    {
                        "name": "role2",
                            "members": [ "u1@example.com" ]
                    }
                   ]
                }
                """;
        var mapper = Jackson.mapper();
        RoleList roleList = mapper.readValue(json, RoleList.class);
        assertEquals(2, roleList.roles().size());
        Optional<Role> role1 = roleList.roles().stream()
                .filter(r -> r.getName().equals("role1"))
                .findFirst();
        assertEquals(2, role1.get().getMembers().size());

        Optional<Role> role2 = roleList.roles().stream()
                .filter(r -> r.getName().equals("role2"))
                .findFirst();
        assertEquals(1, role2.get().getMembers().size());
    }

    @Test
    void serializeCorrectly() throws JsonProcessingException {
        Role role1 = new Role("role1", List.of("u1", "u2"));
        Role role2 = new Role("role2", List.of("u1"));
        RoleList roleList = new RoleList(List.of(role1, role2));
        var mapper = Jackson.mapper();
        String serialized = mapper.writeValueAsString(roleList);
        RoleList deserialized = mapper.readValue(serialized, RoleList.class);
        assertEquals(roleList, deserialized);
    }
}