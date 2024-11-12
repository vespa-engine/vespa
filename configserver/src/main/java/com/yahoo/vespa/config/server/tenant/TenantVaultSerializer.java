// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.TenantVault;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * @author gjoranv
 */
public class TenantVaultSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster, and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it, bro.

    private static final String idField = "id";
    private static final String nameField = "name";
    private static final String externalIdField = "externalId";
    private static final String secretsArray = "secrets";

    public static Slime toSlime(List<TenantVault> vaults) {
        Slime slime = new Slime();
        Cursor cursor = slime.setArray();
        toSlime(vaults, cursor);
        return slime;
    }

    public static void toSlime(List<TenantVault> vaults, Cursor cursor) {
        vaults.forEach(tenantVault -> toSlime(tenantVault, cursor.addObject()));
    }

    private static void toSlime(TenantVault vault, Cursor object) {
        object.setString(idField, vault.id());
        object.setString(nameField, vault.name());
        object.setString(externalIdField, vault.externalId());
        Cursor secrets = object.setArray(secretsArray);
        vault.secrets().forEach(secret -> toSlime(secret, secrets.addObject()));
    }

    private static void toSlime(TenantVault.Secret secret, Cursor object) {
        object.setString("name", secret.name());
        object.setString("id", secret.id());
    }

    public static TenantVault fromSlime(Inspector inspector) {
        if (inspector.type() == Type.OBJECT) {
            return new TenantVault(
                    inspector.field(idField).asString(),
                    inspector.field(nameField).asString(),
                    inspector.field(externalIdField).asString(),
                    secretsFromSlime(inspector.field(secretsArray)));
        }
        throw new IllegalArgumentException("Unknown format encountered for tenant vaults!");
    }

    private static List<TenantVault.Secret> secretsFromSlime(Inspector inspector) {
        List<TenantVault.Secret> secrets = new ArrayList<>();
        inspector.traverse(((ArrayTraverser)(idx, secret) -> secrets.add(secretFromSlime(secret))));
        return secrets;
    }

    private static TenantVault.Secret secretFromSlime(Inspector inspector) {
        return new TenantVault.Secret(
                inspector.field("name").asString(),
                inspector.field("id").asString());
    }

    public static List<TenantVault> listFromSlime(Inspector inspector) {
        List<TenantVault> tenantVaults = new ArrayList<>();
        inspector.traverse(((ArrayTraverser)(idx, vault) -> tenantVaults.add(fromSlime(vault))));
        return tenantVaults;
    }

}
