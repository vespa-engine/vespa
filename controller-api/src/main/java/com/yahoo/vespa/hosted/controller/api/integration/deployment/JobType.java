// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;

import java.util.Optional;
import java.util.stream.Stream;

/** Job types that exist in the build system */
public enum JobType {
//     | enum name ------------| job name ------------------| Zone in main system ---------------------------------------| Zone in CD system -------------------------------------------
    component              ("component"                   , null                                                       , null                                                          ),
    systemTest             ("system-test"                 , ZoneId.from("test"   , "us-east-1")      , ZoneId.from("test"   , "cd-us-central-1")  ),
    stagingTest            ("staging-test"                , ZoneId.from("staging", "us-east-3")      , ZoneId.from("staging", "cd-us-central-1")  ),
    productionUsEast3      ("production-us-east-3"        , ZoneId.from("prod"   , "us-east-3")      , null                                                          ),
    productionUsWest1      ("production-us-west-1"        , ZoneId.from("prod"   , "us-west-1")      , null                                                          ),
    productionUsCentral1   ("production-us-central-1"     , ZoneId.from("prod"   , "us-central-1")   , null                                                          ),
    productionApNortheast1 ("production-ap-northeast-1"   , ZoneId.from("prod"   , "ap-northeast-1") , null                                                          ),
    productionApNortheast2 ("production-ap-northeast-2"   , ZoneId.from("prod"   , "ap-northeast-2") , null                                                          ),
    productionApSoutheast1 ("production-ap-southeast-1"   , ZoneId.from("prod"   , "ap-southeast-1") , null                                                          ),
    productionEuWest1      ("production-eu-west-1"        , ZoneId.from("prod"   , "eu-west-1")      , null                                                          ),
    productionAwsUsEast1a  ("production-aws-us-east-1a"   , ZoneId.from("prod"   , "aws-us-east-1a") , null                                                          ),
    productionAwsUsWest2a  ("production-aws-us-west-2a"   , ZoneId.from("prod"   , "aws-us-west-2a") , null                                                          ),
    productionAwsUsEast1b  ("production-aws-us-east-1b"   , ZoneId.from("prod"   , "aws-us-east-1b") , null                                                          ),
    productionCdAwsUsEast1a("production-cd-aws-us-east-1a", null                                                       , ZoneId.from("prod"    , "cd-aws-us-east-1a")),
    productionCdUsCentral1 ("production-cd-us-central-1"  , null                                                       , ZoneId.from("prod"    , "cd-us-central-1")  ),
    // TODO: Cannot remove production-cd-us-central-2 until we know there are no serialized data in controller referencing it
    productionCdUsCentral2 ("production-cd-us-central-2"  , null                                                       , ZoneId.from("prod"    , "cd-us-central-2")  ),
    productionCdUsWest1    ("production-cd-us-west-1"     , null                                                       , ZoneId.from("prod"    , "cd-us-west-1")     );

    private final String jobName;
    private final ImmutableMap<SystemName, ZoneId> zones;

    JobType(String jobName, ZoneId mainZone, ZoneId cdZone) {
        this.jobName = jobName;
        ImmutableMap.Builder<SystemName, ZoneId> builder = ImmutableMap.builder();
        if (mainZone != null) builder.put(SystemName.main, mainZone);
        if (cdZone != null) builder.put(SystemName.cd, cdZone);
        this.zones = builder.build();
    }

    public String jobName() { return jobName; }

    /** Returns the zone for this job in the given system, or throws if this job does not have a zone */
    public ZoneId zone(SystemName system) {
        if ( ! zones.containsKey(system))
            throw new IllegalArgumentException(this + " does not have any zones in " + system);

        return zones.get(system);
    }

    /** Returns whether this is a production job */
    public boolean isProduction() { return environment() == Environment.prod; }

    /** Returns whether this is an automated test job */
    public boolean isTest() { return environment() != null && environment().isTest(); }

    /** Returns the environment of this job type, or null if it does not have an environment */
    public Environment environment() {
        switch (this) {
            case component: return null;
            case systemTest: return Environment.test;
            case stagingTest: return Environment.staging;
            default: return Environment.prod;
        }
    }

    public static Optional<JobType> fromOptionalJobName(String jobName) {
        return Stream.of(values())
                     .filter(jobType -> jobType.jobName.equals(jobName))
                     .findAny();
    }

    public static JobType fromJobName(String jobName) {
        return fromOptionalJobName(jobName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown job name '" + jobName + "'"));
    }

    /** Returns the job type for the given zone */
    public static Optional<JobType> from(SystemName system, ZoneId zone) {
        for (JobType job : values())
            if (zone.equals(job.zones.get(system)))
                return Optional.of(job);
        return Optional.empty();
    }

    /** Returns the job job type for the given environment and region or null if none */
    public static Optional<JobType> from(SystemName system, Environment environment, RegionName region) {
        switch (environment) {
            case test: return Optional.of(systemTest);
            case staging: return Optional.of(stagingTest);
        }
        return from(system, ZoneId.from(environment, region));
    }

}
