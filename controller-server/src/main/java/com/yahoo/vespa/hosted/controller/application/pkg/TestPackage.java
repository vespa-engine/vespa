package com.yahoo.vespa.hosted.controller.application.pkg;

import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.DeploymentSpec.Step;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.path.Path;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.text.Text;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud.Suite;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterId;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackageStream.Replacer;
import com.yahoo.vespa.hosted.controller.config.ControllerConfig;
import com.yahoo.vespa.hosted.controller.config.ControllerConfig.Steprunner.Testerapp;
import com.yahoo.yolean.Exceptions;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

import static com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud.Suite.of;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud.Suite.production;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud.Suite.staging;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud.Suite.staging_setup;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud.Suite.system;
import static com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage.deploymentFile;
import static com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage.servicesFile;
import static java.io.InputStream.nullInputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

/**
 * Validation and manipulation of test package.
 *
 * @author jonmv
 */
public class TestPackage {

    // Must match exactly the advertised resources of an AWS instance type. Also consider that the container
    // will have ~1.8 GB less memory than equivalent resources in AWS (VESPA-16259).
    static final NodeResources DEFAULT_TESTER_RESOURCES_AWS = new NodeResources(2, 8, 50, 0.3, NodeResources.DiskSpeed.any);
    static final NodeResources DEFAULT_TESTER_RESOURCES = new NodeResources(1, 4, 50, 0.3, NodeResources.DiskSpeed.any);

    private final ApplicationPackageStream applicationPackageStream;
    private final X509Certificate certificate;

    public TestPackage(Supplier<InputStream> inZip, boolean isPublicSystem, CloudName cloud, RunId id, Testerapp testerApp,
                       DeploymentSpec spec, Instant certificateValidFrom, Duration certificateValidDuration) {
        KeyPair keyPair;
        if (certificateValidFrom != null) {
            keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA, 2048);
            X500Principal subject = new X500Principal("CN=" + id.tester().id().toFullString() + "." + id.type() + "." + id.number());
            this.certificate = X509CertificateBuilder.fromKeypair(keyPair,
                                                                  subject,
                                                                  certificateValidFrom,
                                                                  certificateValidFrom.plus(certificateValidDuration),
                                                                  SignatureAlgorithm.SHA512_WITH_RSA,
                                                                  BigInteger.valueOf(1))
                                                     .build();
        }
        else {
            keyPair = null;
            this.certificate = null;
        }
        this.applicationPackageStream = new ApplicationPackageStream(inZip, () -> __ -> false, () -> new Replacer() {

            // Initially skips all declared entries, ensuring they're generated and appended after all input entries.
            final Map<String, UnaryOperator<InputStream>> entries = new HashMap<>();
            final Map<String, UnaryOperator<InputStream>> replacements = new HashMap<>();
            boolean hasLegacyTests = false;

            @Override
            public String next() {
                if (entries.isEmpty()) return null;
                String next = entries.keySet().iterator().next();
                replacements.put(next, entries.remove(next));
                return next;
            }

            @Override
            public InputStream modify(String name, InputStream in) {
                hasLegacyTests |= name.startsWith("artifacts/") && name.endsWith("-tests.jar");
                return entries.containsKey(name) ? null : replacements.getOrDefault(name, identity()).apply(in);
            }

            {
                // Copy contents of submitted application-test.zip, and ensure required directories exist within the zip.
                entries.put("artifacts/.ignore-" + UUID.randomUUID(), __ -> nullInputStream());
                entries.put("tests/.ignore-" + UUID.randomUUID(), __ -> nullInputStream());

                entries.put(servicesFile,
                            __ -> new ByteArrayInputStream(servicesXml( ! isPublicSystem,
                                                                       certificateValidFrom != null,
                                                                       hasLegacyTests,
                                                                       testerResourcesFor(id.type().zone(), spec.requireInstance(id.application().instance())),
                                                                       testerApp)));

                entries.put(deploymentFile,
                            __ -> new ByteArrayInputStream(deploymentXml(id.tester(), id.application().instance(), cloud, id.type().zone(), spec)));

                if (certificate != null) {
                    entries.put("artifacts/key", __ -> new ByteArrayInputStream(KeyUtils.toPem(keyPair.getPrivate()).getBytes(UTF_8)));
                    entries.put("artifacts/cert", __ -> new ByteArrayInputStream(X509CertificateUtils.toPem(certificate).getBytes(UTF_8)));
                }
            }
        });
    }

    public ApplicationPackageStream asApplicationPackage() {
        return applicationPackageStream;
    }

    public X509Certificate certificate() {
        return Objects.requireNonNull(certificate);
    }

    public static TestSummary validateTests(DeploymentSpec spec, byte[] testPackage) {
        return validateTests(expectedSuites(spec.steps()), testPackage);
    }

    static TestSummary validateTests(Collection<Suite> expectedSuites, byte[] testPackage) {
        List<String> problems = new ArrayList<>();
        Set<Suite> suites = new LinkedHashSet<>();
        ZipEntries.from(testPackage, __ -> true, 0, false).asList().stream()
                  .map(entry -> Path.fromString(entry.name()))
                  .collect(groupingBy(path -> path.elements().size() > 1 ? path.elements().get(0) : "",
                                      mapping(path -> (path.elements().size() > 1 ? path.getChildPath() : path).getRelative(), toList())))
                  .forEach((directory, paths) -> {
                      switch (directory) {
                          case "components": {
                              for (String path : paths) {
                                  if (path.endsWith("-tests.jar")) {
                                      try {
                                          byte[] testsJar = ZipEntries.readFile(testPackage, "components/" + path, 1 << 30);
                                          Manifest manifest = new JarInputStream(new ByteArrayInputStream(testsJar)).getManifest();
                                          String bundleCategoriesHeader = manifest.getMainAttributes().getValue("X-JDisc-Test-Bundle-Categories");
                                          if (bundleCategoriesHeader == null) continue;
                                          for (String suite : bundleCategoriesHeader.split(","))
                                              if ( ! suite.isBlank()) switch (suite.trim()) {
                                                  case "SystemTest" -> suites.add(system);
                                                  case "StagingSetup" -> suites.add(staging_setup);
                                                  case "StagingTest" -> suites.add(staging);
                                                  case "ProductionTest" -> suites.add(production);
                                                  default -> problems.add("unexpected test suite name '" + suite + "' in bundle manifest");
                                              }
                                      }
                                      catch (Exception e) {
                                          problems.add("failed reading test bundle manifest: " + Exceptions.toMessageString(e));
                                      }
                                  }
                              }
                          }
                          break;
                          case "tests": {
                              if (paths.stream().anyMatch(Pattern.compile("system-test/.+\\.json").asMatchPredicate())) suites.add(system);
                              if (paths.stream().anyMatch(Pattern.compile("staging-setup/.+\\.json").asMatchPredicate())) suites.add(staging_setup);
                              if (paths.stream().anyMatch(Pattern.compile("staging-test/.+\\.json").asMatchPredicate())) suites.add(staging);
                              if (paths.stream().anyMatch(Pattern.compile("production-test/.+\\.json").asMatchPredicate())) suites.add(production);
                          }
                          break;
                          case "artifacts": {
                              if (paths.stream().anyMatch(Pattern.compile(".+-tests.jar").asMatchPredicate()))
                                  suites.addAll(expectedSuites); // ಠ_ಠ

                              for (String forbidden : List.of("key", "cert"))
                                  if (paths.contains(forbidden))
                                      problems.add("test package contains 'artifacts/" + forbidden +
                                                   "'; this conflicts with credentials used to run tests in Vespa Cloud");
                          }
                          break;
                      }
                  });

        if (expectedSuites.contains(system) && ! suites.contains(system))
            problems.add("test package has no system tests, but <test /> is declared in deployment.xml");

        if (suites.contains(staging) != suites.contains(staging_setup))
            problems.add("test package has " + (suites.contains(staging) ? "staging tests" : "staging setup") +
                         ", so it should also include " + (suites.contains(staging) ? "staging setup" : "staging tests"));
        else if (expectedSuites.contains(staging) && ! suites.contains(staging))
            problems.add("test package has no staging setup and tests, but <staging /> is declared in deployment.xml");

        if (suites.contains(production) != expectedSuites.contains(production))
            problems.add("test package has " + (suites.contains(production) ? "" : "no ") + "production tests, " +
                         "but " + (suites.contains(production) ? "no " : "") + "production tests are declared in deployment.xml");

        if ( ! problems.isEmpty())
            problems.add("see https://docs.vespa.ai/en/testing.html for details on how to write system tests for Vespa");

        return new TestSummary(problems, suites);
    }

    static NodeResources testerResourcesFor(ZoneId zone, DeploymentInstanceSpec spec) {
        NodeResources nodeResources = spec.steps().stream()
                                          .filter(step -> step.concerns(zone.environment()))
                                          .findFirst()
                                          .flatMap(step -> step.zones().get(0).testerFlavor())
                                          .map(NodeResources::fromLegacyName)
                                          .orElse(zone.region().value().contains("aws-") ? DEFAULT_TESTER_RESOURCES_AWS
                                                                                         : DEFAULT_TESTER_RESOURCES);
        return nodeResources.with(NodeResources.DiskSpeed.any);
    }

    /** Returns the generated services.xml content for the tester application. */
    static byte[] servicesXml(boolean systemUsesAthenz, boolean useTesterCertificate, boolean hasLegacyTests,
                                     NodeResources resources, ControllerConfig.Steprunner.Testerapp config) {
        int jdiscMemoryGb = 2; // 2Gb memory for tester application which uses Maven.
        int jdiscMemoryPct = (int) Math.ceil(100 * jdiscMemoryGb / resources.memoryGb());

        // Of the remaining memory, split 50/50 between Surefire running the tests and the rest
        int testMemoryMb = (int) (1024 * (resources.memoryGb() - jdiscMemoryGb) / 2);

        String resourceString = Text.format("<resources vcpu=\"%.2f\" memory=\"%.2fGb\" disk=\"%.2fGb\" disk-speed=\"%s\" storage-type=\"%s\"/>",
                                            resources.vcpu(), resources.memoryGb(), resources.diskGb(), resources.diskSpeed().name(), resources.storageType().name());

        String runtimeProviderClass = config.runtimeProviderClass();
        String tenantCdBundle = config.tenantCdBundle();

        String servicesXml =
                "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<services xmlns:deploy='vespa' version='1.0'>\n" +
                "    <container version='1.0' id='tester'>\n" +
                "\n" +
                "        <component id=\"com.yahoo.vespa.hosted.testrunner.TestRunner\" bundle=\"vespa-testrunner-components\">\n" +
                "            <config name=\"com.yahoo.vespa.hosted.testrunner.test-runner\">\n" +
                "                <artifactsPath>artifacts</artifactsPath>\n" +
                "                <surefireMemoryMb>" + testMemoryMb + "</surefireMemoryMb>\n" +
                "                <useAthenzCredentials>" + systemUsesAthenz + "</useAthenzCredentials>\n" +
                "                <useTesterCertificate>" + useTesterCertificate + "</useTesterCertificate>\n" +
                "            </config>\n" +
                "        </component>\n" +
                "\n" +
                "        <handler id=\"com.yahoo.vespa.testrunner.TestRunnerHandler\" bundle=\"vespa-osgi-testrunner\">\n" +
                "            <binding>http://*/tester/v1/*</binding>\n" +
                "        </handler>\n" +
                "\n" +
                "        <component id=\"" + runtimeProviderClass + "\" bundle=\"" + tenantCdBundle + "\" />\n" +
                "\n" +
                "        <component id=\"com.yahoo.vespa.testrunner.JunitRunner\" bundle=\"vespa-osgi-testrunner\">\n" +
                "            <config name=\"com.yahoo.vespa.testrunner.junit-test-runner\">\n" +
                "                <artifactsPath>artifacts</artifactsPath>\n" +
                "                <useAthenzCredentials>" + systemUsesAthenz + "</useAthenzCredentials>\n" +
                "            </config>\n" +
                "        </component>\n" +
                "\n" +
                "        <component id=\"com.yahoo.vespa.testrunner.VespaCliTestRunner\" bundle=\"vespa-osgi-testrunner\">\n" +
                "            <config name=\"com.yahoo.vespa.testrunner.vespa-cli-test-runner\">\n" +
                "                <artifactsPath>artifacts</artifactsPath>\n" +
                "                <testsPath>tests</testsPath>\n" +
                "                <useAthenzCredentials>" + systemUsesAthenz + "</useAthenzCredentials>\n" +
                "            </config>\n" +
                "        </component>\n" +
                "\n" +
                "        <nodes count=\"1\">\n" +
                (hasLegacyTests ? "            <jvm allocated-memory=\"" + jdiscMemoryPct + "%\"/>\n" : "" ) +
                "            " + resourceString + "\n" +
                "        </nodes>\n" +
                "    </container>\n" +
                "</services>\n";

        return servicesXml.getBytes(UTF_8);
    }

    /** Returns a dummy deployment xml which sets up the service identity for the tester, if present. */
    static byte[] deploymentXml(TesterId id, InstanceName instance, CloudName cloud, ZoneId zone, DeploymentSpec original) {
        Optional<AthenzDomain> athenzDomain = original.athenzDomain();
        Optional<AthenzService> athenzService = original.requireInstance(instance)
                                                        .athenzService(zone.environment(), zone.region());
        Optional<CloudAccount> cloudAccount = Optional.of(original.cloudAccount(cloud, instance, zone))
                                                      .filter(account -> ! account.isUnspecified());
        Optional<Duration> hostTTL = (zone.environment().isProduction()
                                      ? original.requireInstance(instance)
                                                .steps().stream().filter(step -> step.isTest() && step.concerns(zone.environment(), Optional.of(zone.region())))
                                                .findFirst().flatMap(Step::hostTTL)
                                      : original.requireInstance(instance).hostTTL(zone.environment(), Optional.of(zone.region())))
                .filter(__ -> cloudAccount.isPresent());
        String deploymentSpec =
                "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<deployment version='1.0'" +
                athenzDomain.map(domain -> " athenz-domain='" + domain.value() + "'").orElse("") +
                athenzService.map(service -> " athenz-service='" + service.value() + "'").orElse("") +
                cloudAccount.map(account -> " cloud-account='" + account.value() + "'").orElse("") +
                hostTTL.map(ttl -> " empty-host-ttl='" + ttl.getSeconds() / 60 + "m'").orElse("") +
                ">" +
                "  <instance id='" + id.id().instance().value() + "' />" +
                "</deployment>";
        return deploymentSpec.getBytes(UTF_8);
    }

    static Set<Suite> expectedSuites(List<Step> steps) {
        Set<Suite> suites = new HashSet<>();
        if (steps.isEmpty()) return suites;
        for (Step step : steps) {
            if (step.isTest()) {
                if (step.concerns(Environment.prod)) suites.add(production);
                if (step.concerns(Environment.test)) suites.add(system);
                if (step.concerns(Environment.staging)) { suites.add(staging); suites.add(staging_setup); }
            }
            else
                suites.addAll(expectedSuites(step.steps()));
        }
        return suites;
    }


    public static class TestSummary {

        private final List<String> problems;
        private final List<Suite> suites;

        public TestSummary(List<String> problems, Set<Suite> suites) {
            this.problems = List.copyOf(problems);
            this.suites = List.copyOf(suites);
        }

        public List<String> problems() {
            return problems;
        }

        public List<Suite> suites() {
            return suites;
        }

    }

}
