// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.controller;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.restapi.SlimeJsonResponse;

/**
 * A response containing maintenance job status
 *
 * @author bratseth
 */
public class JobsResponse extends SlimeJsonResponse {

    public JobsResponse(JobControl jobControl) {
        super(toSlime(jobControl));
    }

    private static Slime toSlime(JobControl jobControl) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();

        Cursor jobArray = root.setArray("jobs");
        for (String jobName : jobControl.jobs())
            jobArray.addObject().setString("name", jobName);

        Cursor inactiveArray = root.setArray("inactive");
        for (String jobName : jobControl.inactiveJobs())
            inactiveArray.addString(jobName);

        return slime;
    }

}
