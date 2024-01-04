// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.path.Path;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXException;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CloudDataPlaneFilterValidatorTest {

    @TempDir
    public File applicationFolder;

    @Test
    void validator_accepts_distinct_client_certificates() throws IOException, SAXException {
        String certFile1 = "security/foo.pem";
        String certFile2 = "security/bar.pem";
        String servicesXml = """
                        <services version='1.0'>
                          <container version='1.0'>
                            <clients>
                              <client id="a" permissions="read,write">
                                <certificate file="%s"/>
                              </client>
                              <client id="b" permissions="read,write">
                                <certificate file="%s"/>
                              </client>
                            </clients>
                          </container>
                        </services>
                """.formatted(certFile1, certFile2);

        DeployState deployState = createDeployState(servicesXml,
                                                    Map.of(
                                                            certFile1, List.of(createCertificate("foo")),
                                                            certFile2, List.of(createCertificate("bar"))));

        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
        new CloudDataPlaneFilterValidator().validate(model, deployState);
    }

    @Test
    void validator_rejects_duplicate_client_certificates_different_files() throws IOException, SAXException {
        String certFile1 = "security/a.pem";
        String certFile2 = "security/b.pem";
        X509Certificate certificate = createCertificate("a");
        String servicesXml = """
                <services version='1.0'>
                  <container version='1.0'>
                    <clients>
                      <client id="a" permissions="read,write">
                        <certificate file="%s"/>
                      </client>
                      <client id="b" permissions="read,write">
                        <certificate file="%s"/>
                      </client>
                    </clients>
                  </container>
                </services>
                """.formatted(certFile1, certFile2);

        DeployState deployState = createDeployState(servicesXml,
                                                    Map.of(
                                                            certFile1, List.of(certificate),
                                                            certFile2, List.of(certificate)));

        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
        IllegalArgumentException illegalArgumentException = Assertions.assertThrows(IllegalArgumentException.class, () ->
                new CloudDataPlaneFilterValidator().validate(model, deployState));
        assertEquals(
                "Duplicate certificate(s) detected in files: [%s, %s]. Certificate subject of duplicates: [%s]".formatted(certFile1, certFile2, certificate.getSubjectX500Principal().getName()),
                illegalArgumentException.getMessage());
    }

    @Test
    void validator_rejects_duplicate_client_certificates_same_file() throws IOException, SAXException {
        String certFile1 = "security/a.pem";
        X509Certificate certificate = createCertificate("a");
        String servicesXml = """
                <services version='1.0'>
                  <container version='1.0'>
                    <clients>
                      <client id="a" permissions="read,write">
                        <certificate file="%s"/>
                      </client>
                    </clients>
                  </container>
                </services>
                """.formatted(certFile1);

        DeployState deployState = createDeployState(servicesXml,
                                                    Map.of(certFile1, List.of(certificate, certificate)));

        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
        IllegalArgumentException illegalArgumentException = Assertions.assertThrows(IllegalArgumentException.class, () ->
                new CloudDataPlaneFilterValidator().validate(model, deployState));
        assertEquals(
                "Duplicate certificate(s) detected in files: [%s]. Certificate subject of duplicates: [%s]".formatted(certFile1, certificate.getSubjectX500Principal().getName()),
                illegalArgumentException.getMessage());
    }


    private DeployState createDeployState(String servicesXml, Map<String, List<X509Certificate>> certificates) {

        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder()
                .withRoot(applicationFolder)
                .withServices(servicesXml)
                .build();

        applicationPackage.getFile(Path.fromString("security")).createDirectory();
        certificates.forEach((file, certList) ->
                                     applicationPackage.getFile(Path.fromString(file)).writeFile(new StringReader(X509CertificateUtils.toPem(certList))));

        return new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .properties(
                        new TestProperties()
                                .setEndpointCertificateSecrets(Optional.of(new EndpointCertificateSecrets("CERT", "KEY")))
                                .setHostedVespa(true))
                .endpoints(Set.of(new ContainerEndpoint("container", ApplicationClusterEndpoint.Scope.zone, List.of("c.example.com"))))
                .zone(new Zone(SystemName.PublicCd, Environment.dev, RegionName.defaultName()))
                .build();
    }

    static X509Certificate createCertificate(String cn) throws IOException {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        X500Principal subject = new X500Principal("CN=" + cn);
        return X509CertificateBuilder
                .fromKeypair(
                        keyPair, subject, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS), SignatureAlgorithm.SHA512_WITH_ECDSA, BigInteger.valueOf(1))
                .build();
    }

}
