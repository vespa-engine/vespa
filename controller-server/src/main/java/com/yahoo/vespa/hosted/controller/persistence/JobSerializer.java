package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;
import com.yahoo.vespa.hosted.controller.deployment.Step;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JobSerializer {

    private static final String stepsField = "steps";
    private static final String applicationField = "id";
    private static final String jobTypeField = "type";
    private static final String numberField = "number";
    private static final String startField = "start";
    private static final String endField = "end";

    public RunStatus runFromSlime(Slime slime) {
        return runFromSlime(slime.get());
    }

    public Map<RunId, RunStatus> runsFromSlime(Slime slime) {
        Map<RunId, RunStatus> runs = new LinkedHashMap<>();
        Inspector runArray = slime.get();
        runArray.traverse((ArrayTraverser) (__, runObject) -> {
            RunStatus run = runFromSlime(runObject);
            runs.put(run.id(), run);
        });

        return runs;
    }

    private RunStatus runFromSlime(Inspector runObject) {
        EnumMap<Step, Step.Status> steps = new EnumMap<>(Step.class);
        runObject.field(stepsField).traverse((ObjectTraverser) (step, status) -> {
            steps.put(Step.valueOf(step), Step.Status.valueOf(status.asString()));
        });
        return new RunStatus(new RunId(ApplicationId.fromSerializedForm(runObject.field(applicationField).asString()),
                                       JobType.fromJobName(runObject.field(jobTypeField).asString()),
                                       runObject.field(numberField).asLong()),
                             steps,
                             Instant.ofEpochMilli(runObject.field(startField).asLong()),
                             Optional.of(runObject.field(endField))
                                     .filter(Inspector::valid)
                                     .map(end -> Instant.ofEpochMilli(end.asLong())));
    }

    public Slime toSlime(RunStatus run) {
        Slime slime = new Slime();
        toSlime(run, slime.setObject());
        return slime;
    }

    public Slime toSlime(Iterable<RunStatus> runs) {
        Slime slime = new Slime();
        Cursor runArray = slime.setArray();
        runs.forEach(run -> toSlime(run, runArray.addObject()));
        return slime;
    }

    private void toSlime(RunStatus run, Cursor runObject) {
        runObject.setString(applicationField, run.id().application().serializedForm());
        runObject.setString(jobTypeField, run.id().type().jobName());
        runObject.setLong(numberField, run.id().number());
        runObject.setLong(startField, run.start().toEpochMilli());
        run.end().ifPresent(end -> runObject.setLong(endField, end.toEpochMilli()));
        Cursor stepsObject = runObject.setObject(stepsField);
        run.steps().forEach((step, status) -> stepsObject.setString(step.name(), status.name()));
    }

}
