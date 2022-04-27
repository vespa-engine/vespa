// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author mortent
 */
public class RetriggerEntrySerializer {

    private static final String JOB_ID_KEY = "jobId";
    private static final String APPLICATION_ID_KEY = "applicationId";
    private static final String JOB_TYPE_KEY = "jobType";
    private static final String MIN_REQUIRED_RUN_ID_KEY = "minimumRunId";

    public List<RetriggerEntry> fromSlime(Slime slime) {
        return SlimeUtils.entriesStream(slime.get().field("entries"))
                .map(this::deserializeEntry)
                .collect(Collectors.toList());
    }

    public Slime toSlime(List<RetriggerEntry> entryList) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor entries = root.setArray("entries");
        entryList.forEach(e -> serializeEntry(entries, e));
        return slime;
    }

    private void serializeEntry(Cursor array, RetriggerEntry entry) {
        Cursor root = array.addObject();
        Cursor jobid = root.setObject(JOB_ID_KEY);
        jobid.setString(APPLICATION_ID_KEY, entry.jobId().application().serializedForm());
        jobid.setString(JOB_TYPE_KEY, entry.jobId().type().serialized());
        root.setLong(MIN_REQUIRED_RUN_ID_KEY, entry.requiredRun());
    }

    private RetriggerEntry deserializeEntry(Inspector inspector) {
        Inspector jobid = inspector.field(JOB_ID_KEY);
        ApplicationId applicationId = ApplicationId.fromSerializedForm(require(jobid, APPLICATION_ID_KEY).asString());
        JobType jobType = JobType.ofSerialized(require(jobid, JOB_TYPE_KEY).asString());
        long minRequiredRunId = require(inspector, MIN_REQUIRED_RUN_ID_KEY).asLong();
        return new RetriggerEntry(new JobId(applicationId, jobType), minRequiredRunId);
    }

    private Inspector require(Inspector inspector, String fieldName) {
        Inspector field = inspector.field(fieldName);
        if (!field.valid()) {
            throw new IllegalStateException("Could not deserialize, field not found in json: " + fieldName);
        }
        return field;
    }

}
