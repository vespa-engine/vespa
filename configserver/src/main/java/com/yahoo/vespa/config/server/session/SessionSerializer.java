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
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.yolean.Exceptions;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

/**
 * Serialization and deserialization of session data to/from ZooKeeper.
 * @author hmusum
 */
public class SessionSerializer {

    private static final Logger log = Logger.getLogger(SessionSerializer.class.getName());

    void write(SessionZooKeeperClient zooKeeperClient, ApplicationId applicationId,
               Instant created, Optional<FileReference> fileReference, Optional<DockerImage> dockerImageRepository,
               Version vespaVersion, Optional<AthenzDomain> athenzDomain, Optional<Quota> quota,
               List<TenantSecretStore> tenantSecretStores, List<X509Certificate> operatorCertificates,
               Optional<CloudAccount> cloudAccount, List<DataplaneToken> dataplaneTokens,
               BooleanFlag writeSessionData) {
        zooKeeperClient.writeApplicationId(applicationId);
        zooKeeperClient.writeApplicationPackageReference(fileReference);
        zooKeeperClient.writeVespaVersion(vespaVersion);
        zooKeeperClient.writeDockerImageRepository(dockerImageRepository);
        zooKeeperClient.writeAthenzDomain(athenzDomain);
        zooKeeperClient.writeQuota(quota);
        zooKeeperClient.writeTenantSecretStores(tenantSecretStores);
        zooKeeperClient.writeOperatorCertificates(operatorCertificates);
        zooKeeperClient.writeCloudAccount(cloudAccount);
        zooKeeperClient.writeDataplaneTokens(dataplaneTokens);
        if (writeSessionData.value())
            zooKeeperClient.writeSessionData(new SessionData(applicationId,
                                                             fileReference,
                                                             vespaVersion,
                                                             created,
                                                             dockerImageRepository,
                                                             athenzDomain,
                                                             quota,
                                                             tenantSecretStores,
                                                             operatorCertificates,
                                                             cloudAccount,
                                                             dataplaneTokens));
    }

    SessionData read(SessionZooKeeperClient zooKeeperClient, BooleanFlag readSessionData) {
        if (readSessionData.value() && zooKeeperClient.sessionDataExists())
            try {
                return zooKeeperClient.readSessionData();
            } catch (Exception e) {
                log.log(WARNING, "Unable to read session data for session " + zooKeeperClient.sessionId() +
                        ": " + Exceptions.toMessageString(e));
            }

        return readSessionDataFromLegacyPaths(zooKeeperClient);
    }

    private static SessionData readSessionDataFromLegacyPaths(SessionZooKeeperClient zooKeeperClient) {
        return new SessionData(zooKeeperClient.readApplicationId(),
                               zooKeeperClient.readApplicationPackageReference(),
                               zooKeeperClient.readVespaVersion(),
                               zooKeeperClient.readCreateTime(),
                               zooKeeperClient.readDockerImageRepository(),
                               zooKeeperClient.readAthenzDomain(),
                               zooKeeperClient.readQuota(),
                               zooKeeperClient.readTenantSecretStores(),
                               zooKeeperClient.readOperatorCertificates(),
                               zooKeeperClient.readCloudAccount(),
                               zooKeeperClient.readDataplaneTokens());
    }

}
