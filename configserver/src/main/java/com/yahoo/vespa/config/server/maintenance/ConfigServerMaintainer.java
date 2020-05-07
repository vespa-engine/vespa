// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.concurrent.maintenance.JobControl;
import com.yahoo.concurrent.maintenance.Maintainer;
import com.yahoo.concurrent.maintenance.StringSetSerializer;
import com.yahoo.path.Path;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.curator.Curator;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A maintainer is some job which runs at a fixed interval to perform some maintenance task in the config server.
 *
 * @author hmusum
 */
public abstract class ConfigServerMaintainer extends Maintainer {

    protected final ApplicationRepository applicationRepository;

    ConfigServerMaintainer(ApplicationRepository applicationRepository, Curator curator, Duration initialDelay, Duration interval) {
        super(null, interval, initialDelay, new JobControl(new JobControlDb(curator)));
        this.applicationRepository = applicationRepository;
    }

    private static class JobControlDb implements JobControl.Db {

        private static final Logger log = Logger.getLogger(JobControlDb.class.getName());

        private static final Path root = Path.fromString("/configserver/v1/");
        private static final Path lockRoot = root.append("locks");
        private static final Path inactiveJobsPath = root.append("inactiveJobs");

        private final Curator curator;
        private final StringSetSerializer serializer = new StringSetSerializer();

        public JobControlDb(Curator curator) {
            this.curator = curator;
        }

        @Override
        public Set<String> readInactiveJobs() {
            try {
                return curator.getData(inactiveJobsPath)
                              .filter(data -> data.length > 0)
                              .map(serializer::fromJson).orElseGet(HashSet::new);
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Error reading inactive jobs, deleting inactive state");
                writeInactiveJobs(Set.of());
                return new HashSet<>();
            }
        }

        @Override
        public void writeInactiveJobs(Set<String> inactiveJobs) {
            curator.set(inactiveJobsPath, serializer.toJson(inactiveJobs));
        }

        @Override
        public Mutex lockInactiveJobs() {
            return curator.lock(lockRoot.append("inactiveJobsLock"), Duration.ofSeconds(1));
        }

        @Override
        public Mutex lockMaintenanceJob(String job) {
            return curator.lock(lockRoot.append(job), Duration.ofSeconds(1));
        }

    }

}
