// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.concurrent.maintenance.JobControl;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;

import java.io.IOException;
import java.io.OutputStream;
import java.util.TreeSet;

/** A response containing maintenance job status */
public class JobsResponse extends HttpResponse {

    private final JobControl jobControl;

    public JobsResponse(JobControl jobControl) {
        super(200);
        this.jobControl = jobControl;
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor jobArray = root.setArray("jobs");
        for (String jobName : jobControl.jobs())
            jobArray.addObject().setString("name", jobName);

        Cursor inactiveArray = root.setArray("inactive");
        for (String jobName : new TreeSet<>(jobControl.inactiveJobs()))
            inactiveArray.addString(jobName);

        new JsonFormat(true).encode(stream, slime);
    }

    @Override
    public String getContentType() { return "application/json"; }

}
