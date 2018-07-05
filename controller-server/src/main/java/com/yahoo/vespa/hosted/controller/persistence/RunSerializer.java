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
import com.yahoo.vespa.hosted.controller.deployment.Step.Status;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.failed;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deactivateReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deactivateTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployInitialReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installInitialReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.report;
import static com.yahoo.vespa.hosted.controller.deployment.Step.startTests;
import static com.yahoo.vespa.hosted.controller.deployment.Step.endTests;

/**
 * Serialises and deserialises RunStatus objects for persistent storage.
 *
 * @author jonmv
 */
public class RunSerializer {

    private static final String stepsField = "steps";
    private static final String applicationField = "id";
    private static final String jobTypeField = "type";
    private static final String numberField = "number";
    private static final String startField = "start";
    private static final String endField = "end";
    private static final String abortedField = "aborted";

    RunStatus runFromSlime(Slime slime) {
        return runFromSlime(slime.get());
    }

    Map<RunId, RunStatus> runsFromSlime(Slime slime) {
        Map<RunId, RunStatus> runs = new LinkedHashMap<>();
        Inspector runArray = slime.get();
        runArray.traverse((ArrayTraverser) (__, runObject) -> {
            RunStatus run = runFromSlime(runObject);
            runs.put(run.id(), run);
        });
        return runs;
    }

    private RunStatus runFromSlime(Inspector runObject) {
        EnumMap<Step, Status> steps = new EnumMap<>(Step.class);
        runObject.field(stepsField).traverse((ObjectTraverser) (step, status) -> {
            steps.put(stepOf(step), statusOf(status.asString()));
        });
        return new RunStatus(new RunId(ApplicationId.fromSerializedForm(runObject.field(applicationField).asString()),
                                       JobType.fromJobName(runObject.field(jobTypeField).asString()),
                                       runObject.field(numberField).asLong()),
                             steps,
                             Instant.ofEpochMilli(runObject.field(startField).asLong()),
                             Optional.of(runObject.field(endField))
                                     .filter(Inspector::valid)
                                     .map(end -> Instant.ofEpochMilli(end.asLong())),
                             runObject.field(abortedField).asBool());
    }

    Slime toSlime(Iterable<RunStatus> runs) {
        Slime slime = new Slime();
        Cursor runArray = slime.setArray();
        runs.forEach(run -> toSlime(run, runArray.addObject()));
        return slime;
    }

    Slime toSlime(RunStatus run) {
        Slime slime = new Slime();
        toSlime(run, slime.setObject());
        return slime;
    }

    private void toSlime(RunStatus run, Cursor runObject) {
        runObject.setString(applicationField, run.id().application().serializedForm());
        runObject.setString(jobTypeField, run.id().type().jobName());
        runObject.setLong(numberField, run.id().number());
        runObject.setLong(startField, run.start().toEpochMilli());
        run.end().ifPresent(end -> runObject.setLong(endField, end.toEpochMilli()));
        if (run.isAborted()) runObject.setBool(abortedField, true);
        Cursor stepsObject = runObject.setObject(stepsField);
        run.steps().forEach((step, status) -> stepsObject.setString(valueOf(step), valueOf(status)));
    }

    static String valueOf(Step step) {
        switch (step) {
            case deployInitialReal  : return "DIR";
            case installInitialReal : return "IIR";
            case deployReal         : return "DR" ;
            case installReal        : return "IR" ;
            case deactivateReal     : return "DAR";
            case deployTester       : return "DT" ;
            case installTester      : return "IT" ;
            case deactivateTester   : return "DAT";
            case startTests         : return "ST" ;
            case endTests           : return "ET" ;
            case report             : return "R"  ;
            default                 : throw new AssertionError("No value defined for '" + step + "'!");
        }
    }

    static Step stepOf(String step) {
        switch (step) {
            case "DIR" : return deployInitialReal ;
            case "IIR" : return installInitialReal;
            case "DR"  : return deployReal        ;
            case "IR"  : return installReal       ;
            case "DAR" : return deactivateReal    ;
            case "DT"  : return deployTester      ;
            case "IT"  : return installTester     ;
            case "DAT" : return deactivateTester  ;
            case "ST"  : return startTests        ;
            case "ET"  : return endTests          ;
            case "R"   : return report            ;
            default    : throw new IllegalArgumentException("No step defined by '" + step + "'!");
        }
    }

    static String valueOf(Status status) {
        switch (status) {
            case unfinished : return "U";
            case failed     : return "F";
            case succeeded  : return "S";
            default  : throw new AssertionError("No value defined for '" + status + "'!");
        }
    }

    static Status statusOf(String status) {
        switch (status) {
            case "U" : return unfinished;
            case "F" : return failed    ;
            case "S" : return succeeded ;
            default  : throw new IllegalArgumentException("No status defined by '" + status + "'!");
        }
    }

}
