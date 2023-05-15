package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.SourceRevision;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;

import java.time.Instant;
import java.util.Optional;

import static com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage.calculateHash;

/**
 * @author jonmv
 */
public class Submission {

    private final ApplicationPackage applicationPackage;
    private final byte[] testPackage;
    private final Optional<String> sourceUrl;
    private final Optional<SourceRevision> source;
    private final Optional<String> authorEmail;
    private final Optional<String> description;
    private final Instant now;
    private final int risk;

    public Submission(ApplicationPackage applicationPackage, byte[] testPackage, Optional<String> sourceUrl,
                      Optional<SourceRevision> source, Optional<String> authorEmail, Optional<String> description,
                      Instant now, int risk) {
        this.applicationPackage = applicationPackage;
        this.testPackage = testPackage;
        this.sourceUrl = sourceUrl;
        this.source = source;
        this.authorEmail = authorEmail;
        this.description = description;
        this.now = now;
        this.risk = risk;
    }

    public ApplicationVersion toApplicationVersion(long number) {
        return ApplicationVersion.forProduction(RevisionId.forProduction(number),
                                                source,
                                                authorEmail,
                                                applicationPackage.compileVersion(),
                                                applicationPackage.deploymentSpec().majorVersion(),
                                                applicationPackage.buildTime(),
                                                sourceUrl,
                                                source.map(SourceRevision::commit),
                                                Optional.of(applicationPackage.bundleHash() + calculateHash(testPackage)),
                                                description,
                                                now,
                                                risk);
    }

    public ApplicationPackage applicationPackage() { return applicationPackage; }

    public byte[] testPackage() { return testPackage; }

}
