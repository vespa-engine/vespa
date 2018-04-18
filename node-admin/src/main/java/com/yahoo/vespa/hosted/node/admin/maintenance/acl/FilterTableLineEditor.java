package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.Acl;
import com.yahoo.vespa.hosted.node.admin.task.util.file.LineEdit;
import com.yahoo.vespa.hosted.node.admin.task.util.file.LineEditor;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An editor that assumes all rules in the filter table are exactly as the the wanted rules
 */
class FilterTableLineEditor implements LineEditor {

    private final List<String> wantedRules;
    private boolean removeRemaining = false;

    FilterTableLineEditor(List<String> wantedRules) {
        this.wantedRules = new ArrayList<>(wantedRules);
    }

    static FilterTableLineEditor from(Acl acl, IPVersion ipVersion) {
        List<String> rules = Arrays.asList(acl.toRules(ipVersion).split("\n"));
        return new FilterTableLineEditor(rules);
    }

    @Override
    public LineEdit edit(String line) {
        if (removeRemaining) {
            return LineEdit.remove();
        }
        if (wantedRules.indexOf(line) == 0) {
            wantedRules.remove(line);
            return LineEdit.none();
        } else {
            removeRemaining = true;
            return LineEdit.remove();
        }
    }

    @Override
    public List<String> onComplete() {
        return this.wantedRules;
    }
}