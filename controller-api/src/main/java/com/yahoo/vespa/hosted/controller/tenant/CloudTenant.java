// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.yahoo.config.provision.TenantName;
import com.yahoo.text.Text;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.TenantSecretStore;

import java.security.Principal;
import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A paying tenant in a Vespa cloud service.
 *
 * @author jonmv
 */
public class CloudTenant extends Tenant {

    private static final Pattern VALID_ARCHIVE_ACCESS_ROLE_PATTERN = Pattern.compile("arn:aws:iam::\\d{12}:.+");

    private final Optional<Principal> creator;
    private final BiMap<PublicKey, Principal> developerKeys;
    private final TenantInfo info;
    private final List<TenantSecretStore> tenantSecretStores;
    private final Optional<String> archiveAccessRole;

    /** Public for the serialization layer — do not use! */
    public CloudTenant(TenantName name, Instant createdAt, LastLoginInfo lastLoginInfo, Optional<Principal> creator,
                       BiMap<PublicKey, Principal> developerKeys, TenantInfo info,
                       List<TenantSecretStore> tenantSecretStores, Optional<String> archiveAccessRole) {
        super(name, createdAt, lastLoginInfo, Optional.empty());
        this.creator = creator;
        this.developerKeys = developerKeys;
        this.info = Objects.requireNonNull(info);
        this.tenantSecretStores = tenantSecretStores;
        this.archiveAccessRole = archiveAccessRole;
        if (!archiveAccessRole.map(role -> VALID_ARCHIVE_ACCESS_ROLE_PATTERN.matcher(role).matches()).orElse(true))
            throw new IllegalArgumentException(Text.format("Invalid archive access role '%s': Must match expected pattern: '%s'",
                    archiveAccessRole.get(), VALID_ARCHIVE_ACCESS_ROLE_PATTERN.pattern()));
        if (archiveAccessRole.map(role -> role.length() > 100).orElse(false))
            throw new IllegalArgumentException("Invalid archive access role too long, must be 100 or less characters");
    }

    /** Creates a tenant with the given name, provided it passes validation. */
    public static CloudTenant create(TenantName tenantName, Instant createdAt, Principal creator) {
        return new CloudTenant(requireName(tenantName),
                               createdAt,
                               LastLoginInfo.EMPTY,
                               Optional.ofNullable(creator),
                               ImmutableBiMap.of(), TenantInfo.EMPTY, List.of(), Optional.empty());
    }

    /** The user that created the tenant */
    public Optional<Principal> creator() {
        return creator;
    }

    /** Legal name, addresses etc */
    public TenantInfo info() {
        return info;
    }

    /** An iam role which is allowed to access the S3 (log, dump) archive) */
    public Optional<String> archiveAccessRole() {
        return archiveAccessRole;
    }

    /** Returns the set of developer keys and their corresponding developers for this tenant. */
    public BiMap<PublicKey, Principal> developerKeys() { return developerKeys; }

    /** List of configured secret stores */
    public List<TenantSecretStore> tenantSecretStores() {
        return tenantSecretStores;
    }

    @Override
    public Type type() {
        return Type.cloud;
    }

}
