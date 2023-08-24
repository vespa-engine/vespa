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

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Serialization and deserialization of session data to/from ZooKeeper.
 * @author hmusum
 */
public class SessionSerializer {

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
        if (readSessionData.value())
            return zooKeeperClient.readSessionData();
        else
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
