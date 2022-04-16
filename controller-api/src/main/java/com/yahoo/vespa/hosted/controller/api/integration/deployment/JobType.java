// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.vespa.validation.Validation.require;
import static com.yahoo.config.provision.Environment.prod;
import static com.yahoo.config.provision.Environment.staging;
import static com.yahoo.config.provision.Environment.test;
import static com.yahoo.config.provision.SystemName.Public;
import static com.yahoo.config.provision.SystemName.PublicCd;
import static com.yahoo.config.provision.SystemName.cd;
import static com.yahoo.config.provision.SystemName.main;

/** Job types that exist in the build system */
public final class JobType implements Comparable<JobType> {
//     | enum name ------------| job name ------------------| Zone in main system ---------------------------------------| Zone in CD system -------------------------------------------
    public static final JobType systemTest         = of("system-test",
                            Map.of(main    , ZoneId.from("test", "us-east-1"),
                                   cd      , ZoneId.from("test", "cd-us-west-1"),
                                   PublicCd, ZoneId.from("test", "aws-us-east-1c"),
                                   Public  , ZoneId.from("test", "aws-us-east-1c")));

    public static final JobType stagingTest        = of("staging-test",
                            Map.of(main    , ZoneId.from("staging", "us-east-3"),
                                   cd      , ZoneId.from("staging", "cd-us-west-1"),
                                   PublicCd, ZoneId.from("staging", "aws-us-east-1c"),
                                   Public  , ZoneId.from("staging", "aws-us-east-1c")));

    public static final JobType productionUsEast3  = prod("us-east-3");

    public static final JobType testUsEast3        = test("us-east-3");

    public static final JobType productionUsWest1  = prod("us-west-1");

    public static final JobType testUsWest1        = test("us-west-1");

    public static final JobType productionUsCentral1 = prod("us-central-1");

    public static final JobType testUsCentral1     = test("us-central-1");

    public static final JobType productionApNortheast1 = prod("ap-northeast-1");

    public static final JobType testApNortheast1   = test("ap-northeast-1");

    public static final JobType productionApNortheast2 = prod("ap-northeast-2");

    public static final JobType testApNortheast2   = test("ap-northeast-2");

    public static final JobType productionApSoutheast1 = prod("ap-southeast-1");

    public static final JobType testApSoutheast1   = test("ap-southeast-1");

    public static final JobType productionEuWest1  = prod("eu-west-1");

    public static final JobType testEuWest1        = test("eu-west-1");

    public static final JobType productionAwsUsEast1a= prod("aws-us-east-1a");

    public static final JobType testAwsUsEast1a    = test("aws-us-east-1a");

    public static final JobType productionAwsUsEast1c= prod("aws-us-east-1c");

    public static final JobType testAwsUsEast1c    = test("aws-us-east-1c");

    public static final JobType productionAwsApNortheast1a= prod("aws-ap-northeast-1a");

    public static final JobType testAwsApNortheast1a = test("aws-ap-northeast-1a");

    public static final JobType productionAwsEuWest1a= prod("aws-eu-west-1a");

    public static final JobType testAwsEuWest1a     = test("aws-eu-west-1a");

    public static final JobType productionAwsUsWest2a= prod("aws-us-west-2a");

    public static final JobType testAwsUsWest2a    = test("aws-us-west-2a");

    public static final JobType productionAwsUsEast1b= prod("aws-us-east-1b");

    public static final JobType testAwsUsEast1b    = test("aws-us-east-1b");

    public static final JobType devUsEast1         = dev("us-east-1");

    public static final JobType devAwsUsEast2a     = dev("aws-us-east-2a");

    public static final JobType productionCdAwsUsEast1a = prod("cd-aws-us-east-1a");

    public static final JobType testCdAwsUsEast1a  = test("cd-aws-us-east-1a");

    public static final JobType productionCdUsCentral1  = prod("cd-us-central-1");

    public static final JobType testCdUsCentral1   = test("cd-us-central-1");

    public static final JobType productionCdUsCentral2  = prod("cd-us-central-2");

    public static final JobType testCdUsCentral2   = test("cd-us-central-2");

    public static final JobType productionCdUsEast1= prod("cd-us-east-1");

    public static final JobType testCdUsEast1      = test("cd-us-east-1");

    public static final JobType productionCdUsWest1= prod("cd-us-west-1");

    public static final JobType testCdUsWest1      = test("cd-us-west-1");

    public static final JobType devCdUsCentral1    = dev("cd-us-central-1");

    public static final JobType devCdUsWest1       = dev("cd-us-west-1");

    public static final JobType devAwsUsEast1c     = dev("aws-us-east-1c");

    public static final JobType perfAwsUsEast1c     = perf("aws-us-east-1c");

    public static final JobType perfUsEast3        = perf("us-east-3");

    private static final JobType[] values = new JobType[] {
            systemTest,
            stagingTest,
            productionUsEast3,
            testUsEast3,
            productionUsWest1,
            testUsWest1,
            productionUsCentral1,
            testUsCentral1,
            productionApNortheast1,
            testApNortheast1,
            productionApNortheast2,
            testApNortheast2,
            productionApSoutheast1,
            testApSoutheast1,
            productionEuWest1,
            testEuWest1,
            productionAwsUsEast1a,
            testAwsUsEast1a,
            productionAwsUsEast1c,
            testAwsUsEast1c,
            productionAwsApNortheast1a,
            testAwsApNortheast1a,
            productionAwsEuWest1a,
            testAwsEuWest1a,
            productionAwsUsWest2a,
            testAwsUsWest2a,
            productionAwsUsEast1b,
            testAwsUsEast1b,
            devUsEast1,
            devAwsUsEast2a,
            productionCdAwsUsEast1a,
            testCdAwsUsEast1a,
            productionCdUsCentral1,
            testCdUsCentral1,
            productionCdUsCentral2,
            testCdUsCentral2,
            productionCdUsEast1,
            testCdUsEast1,
            productionCdUsWest1,
            testCdUsWest1,
            devCdUsCentral1,
            devCdUsWest1,
            devAwsUsEast1c,
            perfAwsUsEast1c,
            perfUsEast3
    };

    private final String jobName;
    final Map<SystemName, ZoneId> zones;
    private final boolean isProductionTest;

    private JobType(String jobName, Map<SystemName, ZoneId> zones, boolean isProductionTest) {
        if (zones.values().stream().map(ZoneId::environment).distinct().count() > 1)
            throw new IllegalArgumentException("All zones of a job must be in the same environment");

        this.jobName = jobName;
        this.zones = zones;
        this.isProductionTest = isProductionTest;
    }

    private static JobType of(String jobName, Map<SystemName, ZoneId> zones, boolean isProductionTest) {
        return new JobType(jobName, zones, isProductionTest);
    }

    private static JobType of(String jobName, Map<SystemName, ZoneId> zones) {
        return of(jobName, zones, false);
    }

    public String jobName() { return jobName; }

    /** Returns the zone for this job in the given system, or throws if this job does not have a zone */
    public ZoneId zone(SystemName system) {
        if ( ! zones.containsKey(system))
            throw new IllegalArgumentException(this + " does not have any zones in " + system);

        return zones.get(system);
    }

    /** A system test in a test zone, or throws if no test zones are present.. */
    public static JobType systemTest(ZoneRegistry zones) {
        return testIn(test, zones);
    }

    /** A staging test in a staging zone, or throws if no staging zones are present. */
    public static JobType stagingTest(ZoneRegistry zones){
        return testIn(staging, zones);
    }

    private static JobType testIn(Environment environment, ZoneRegistry zones) {
        return zones.zones().controllerUpgraded().in(environment).zones().stream().map(zone -> deploymentTo(zone.getId()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("no zones in " + environment + " among " + zones.zones().controllerUpgraded().zones()));
    }

    /** A deployment to the given dev region. */
    public static JobType dev(String region) {
        return deploymentTo(ZoneId.from("dev", region));
    }

    /** A deployment to the given perf region. */
    public static JobType perf(String region) {
        return deploymentTo(ZoneId.from("perf", region));
    }

    /** A deployment to the given prod region. */
    public static JobType prod(String region) {
        return deploymentTo(ZoneId.from("prod", region));
    }

    /** A production test in the given region. */
    public static JobType test(String region) {
        return productionTestOf(ZoneId.from("prod", region));
    }

    public static JobType deploymentTo(ZoneId zone) {
        String name;
        switch (zone.environment()) {
            case prod: name = "production-" + zone.region().value(); break;
            case test: name = "system-test"; break;
            case staging: name = "staging-test"; break;
            default: name = zone.environment().value() + "-" + zone.region().value();
        }
        return of(name, dummy(zone), false);
    }

    public static JobType productionTestOf(ZoneId zone) {
        return of("test-" + require(zone.environment() == prod, zone, "must be prod zone").region().value(),  dummy(zone), true);
    }

    private static Map<SystemName, ZoneId> dummy(ZoneId zone) {
        return Stream.of(SystemName.values()).collect(Collectors.toMap(Function.identity(), __ -> zone));
    }

    // TODO jonmv: use for serialisation
    public static JobType ofSerialized(String raw) {
        String[] parts = raw.split("\\.");
        if (parts.length == 2) return deploymentTo(ZoneId.from(parts[0], parts[1]));
        if (parts.length == 3 && "test".equals(parts[2])) return productionTestOf(ZoneId.from(parts[0], parts[1]));
        throw new IllegalArgumentException("illegal serialized job type '" + raw + "'");
    }

    public String serialized(SystemName system) {
        ZoneId zone = zone(system);
        return zone.environment().value() + "." + zone.region().value() + (isProductionTest ? ".test" : "");
    }

    public static List<JobType> allIn(ZoneRegistry zones) {
        return Stream.of(values).filter(job -> job.zones.containsKey(zones.system())).collect(Collectors.toUnmodifiableList());
        /*
        return zones.zones().controllerUpgraded().zones().stream()
                    .flatMap(zone -> zone.getEnvironment().isProduction() ? Stream.of(of(zone.getId()), ofTest(zone.getId()))
                                                                          : Stream.of(of(zone.getId())))
                    .collect(Collectors.toUnmodifiableList());
        */
    }

    static JobType[] values() {
        return Arrays.copyOf(values, values.length);
    }

    public boolean isSystemTest() {
        return environment() == test;
    }

    public boolean isStagingTest() {
        return environment() == staging;
    }

    /** Returns whether this is a production job */
    public boolean isProduction() { return environment() == prod; }

    /** Returns whether this job runs tests */
    public boolean isTest() { return isProductionTest || environment().isTest(); }

    /** Returns whether this job deploys to a zone */
    public boolean isDeployment() { return ! (isProduction() && isProductionTest); }

    /** Returns the environment of this job type */
    public Environment environment() {
        return zones.values().iterator().next().environment();
    }

    // TODO jonmv: require zones
    public static Optional<JobType> fromOptionalJobName(String jobName) {
        if (jobName.contains(".")) return Optional.of(ofSerialized(jobName)); // TODO jonmv: remove
        return Stream.of(values)
                     .filter(jobType -> jobType.jobName.equals(jobName))
                     .findAny();
    }

    // TODO jonmv: require zones
    public static JobType fromJobName(String jobName) {
        return fromOptionalJobName(jobName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown job name '" + jobName + "'"));
    }

    /** Returns the job type for the given zone */
    public static Optional<JobType> from(SystemName system, ZoneId zone, boolean isTest) {
        return Stream.of(values)
                     .filter(job -> zone.equals(job.zones.get(system)) && job.isTest() == isTest)
                     .findAny();
    }

    /** Returns the job type for the given zone */
    public static Optional<JobType> from(SystemName system, ZoneId zone) {
        return from(system, zone, zone.environment().isTest());
    }

    /** Returns the production test job type for the given environment and region or null if none */
    public static Optional<JobType> testFrom(SystemName system, RegionName region) {
        return from(system, ZoneId.from(prod, region), true);
    }

    /** Returns the job job type for the given environment and region or null if none */
    public static Optional<JobType> from(SystemName system, Environment environment, RegionName region) {
        switch (environment) {
            case test: return Optional.of(systemTest);
            case staging: return Optional.of(stagingTest);
        }
        return from(system, ZoneId.from(environment, region));
    }


    private static final Comparator<JobType> comparator = Comparator.comparing(JobType::environment)
                                                                    .thenComparing(JobType::isDeployment)
                                                                    .thenComparing(JobType::jobName);
    @Override
    public int compareTo(JobType other) {
        return comparator.compare(this, other);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobType jobType = (JobType) o;
        return jobName.equals(jobType.jobName);
    }

    @Override
    public int hashCode() {
        return jobName.hashCode();
    }

    @Override
    public String toString() {
        return jobName;
    }

}
