package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;

import java.util.ArrayList;
import java.util.List;

public class JobSerializer {

    public List<RunStatus> fromSlime(Slime slime) {
        List<RunStatus> runs = new ArrayList<>();
        Inspector runArray = slime.get();
        runArray.traverse((ArrayTraverser) (__, runObject) ->
                runs.add(runFromSlime(runObject)));

        return runs;
    }

    private RunStatus runFromSlime(Inspector runObject) {
        throw new AssertionError();
    }

    public Slime toSlime(Iterable<RunStatus> runs) {
        Slime slime = new Slime();
        Cursor runArray = slime.setArray();
        runs.forEach(run -> toSlime(run, runArray.addObject()));
        return slime;
    }

    private Slime toSlime(RunStatus run, Cursor runObject) {
        throw new AssertionError();
    }

}
