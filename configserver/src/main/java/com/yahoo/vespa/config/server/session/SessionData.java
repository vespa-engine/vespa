// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.component.Version;
import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.Quota;
import com.yahoo.config.model.api.TenantSecretStore;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.DataplaneToken;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.config.server.tenant.DataplaneTokenSerializer;
import com.yahoo.vespa.config.server.tenant.OperatorCertificateSerializer;
import com.yahoo.vespa.config.server.tenant.TenantSecretStoreSerializer;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.yahoo.slime.SlimeUtils.optionalString;

/**
 * Data class for session information, typically parameters supplied in a deployment request that needs
 * to be persisted in ZooKeeper. These will be used when creating a new session based on an existing one.
 *
 * @author hmusum
 */
public record SessionData(ApplicationId applicationId,
                          Optional<FileReference> applicationPackageReference,
                          Version version,
                          Instant created,
                          Optional<DockerImage> dockerImageRepository,
                          Optional<AthenzDomain> athenzDomain,
                          Optional<Quota> quota,
                          List<TenantSecretStore> tenantSecretStores,
                          List<X509Certificate> operatorCertificates,
                          Optional<CloudAccount> cloudAccount,
                          List<DataplaneToken> dataplaneTokens) {

    // NOTE: Any state added here MUST also be propagated in com.yahoo.vespa.config.server.deploy.Deployment.prepare()
    static final String APPLICATION_ID_PATH = "applicationId";
    static final String APPLICATION_PACKAGE_REFERENCE_PATH = "applicationPackageReference";
    static final String VERSION_PATH = "version";
    static final String CREATE_TIME_PATH = "createTime";
    static final String DOCKER_IMAGE_REPOSITORY_PATH = "dockerImageRepository";
    static final String ATHENZ_DOMAIN = "athenzDomain";
    static final String QUOTA_PATH = "quota";
    static final String TENANT_SECRET_STORES_PATH = "tenantSecretStores";
    static final String OPERATOR_CERTIFICATES_PATH = "operatorCertificates";
    static final String CLOUD_ACCOUNT_PATH = "cloudAccount";
    static final String DATAPLANE_TOKENS_PATH = "dataplaneTokens";
    static final String SESSION_DATA_PATH = "sessionData";

    public byte[] toJson() {
        try {
            Slime slime = new Slime();
            toSlime(slime.setObject());
            return SlimeUtils.toJsonBytes(slime);
        }
        catch (IOException e) {
            throw new RuntimeException("Serialization of session data to json failed", e);
        }
    }

    private void toSlime(Cursor object) {
        object.setString(APPLICATION_ID_PATH, applicationId.serializedForm());
        applicationPackageReference.ifPresent(ref -> object.setString(APPLICATION_PACKAGE_REFERENCE_PATH, ref.value()));
        object.setString(VERSION_PATH, version.toString());
        object.setLong(CREATE_TIME_PATH, created.toEpochMilli());
        dockerImageRepository.ifPresent(image -> object.setString(DOCKER_IMAGE_REPOSITORY_PATH, image.asString()));
        athenzDomain.ifPresent(domain -> object.setString(ATHENZ_DOMAIN, domain.value()));
        quota.ifPresent(q -> q.toSlime(object.setObject(QUOTA_PATH)));

        Cursor tenantSecretStoresArray = object.setArray(TENANT_SECRET_STORES_PATH);
        TenantSecretStoreSerializer.toSlime(tenantSecretStores, tenantSecretStoresArray);

        Cursor operatorCertificatesArray = object.setArray(OPERATOR_CERTIFICATES_PATH);
        OperatorCertificateSerializer.toSlime(operatorCertificates, operatorCertificatesArray);

        cloudAccount.ifPresent(account -> object.setString(CLOUD_ACCOUNT_PATH, account.value()));

        Cursor dataplaneTokensArray = object.setArray(DATAPLANE_TOKENS_PATH);
        DataplaneTokenSerializer.toSlime(dataplaneTokens, dataplaneTokensArray);
    }

    static SessionData fromSlime(Slime slime) {
        Cursor cursor = slime.get();
        return new SessionData(ApplicationId.fromSerializedForm(cursor.field(APPLICATION_ID_PATH).asString()),
                               optionalString(cursor.field(APPLICATION_PACKAGE_REFERENCE_PATH)).map(FileReference::new),
                               Version.fromString(cursor.field(VERSION_PATH).asString()),
                               Instant.ofEpochMilli(cursor.field(CREATE_TIME_PATH).asLong()),
                               optionalString(cursor.field(DOCKER_IMAGE_REPOSITORY_PATH)).map(DockerImage::fromString),
                               optionalString(cursor.field(ATHENZ_DOMAIN)).map(AthenzDomain::from),
                               SlimeUtils.isPresent(cursor.field(QUOTA_PATH))
                                       ? Optional.of(Quota.fromSlime(cursor.field(QUOTA_PATH)))
                                       : Optional.empty(),
                               TenantSecretStoreSerializer.listFromSlime(cursor.field(TENANT_SECRET_STORES_PATH)),
                               OperatorCertificateSerializer.fromSlime(cursor.field(OPERATOR_CERTIFICATES_PATH)),
                               optionalString(cursor.field(CLOUD_ACCOUNT_PATH)).map(CloudAccount::from),
                               DataplaneTokenSerializer.fromSlime(cursor.field(DATAPLANE_TOKENS_PATH)));
    }

}
