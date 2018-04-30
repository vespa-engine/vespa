package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.Acl;
import com.yahoo.vespa.hosted.node.admin.task.util.file.LineEdit;
import com.yahoo.vespa.hosted.node.admin.task.util.file.LineEditor;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;

import java.util.LinkedList;
import java.util.List;

/**
 * An editor that assumes all rules in the filter table are exactly as the the wanted rules
 */
class FilterTableLineEditor implements LineEditor {

    private final LinkedList<String> wantedRules;

    private FilterTableLineEditor(List<String> wantedRules) {
        this.wantedRules = new LinkedList<>(wantedRules);
    }

    static FilterTableLineEditor from(Acl acl, IPVersion ipVersion) {
        List<String> rules = acl.toRules(ipVersion);
        return new FilterTableLineEditor(rules);
    }

    @Override
    public LineEdit edit(String line) {
        String wantedRule = wantedRules.pop();
        return wantedRule.equals(line) ? LineEdit.none() : LineEdit.replaceWith(wantedRule);
    }

    @Override
    public List<String> onComplete() {
        return this.wantedRules;
    }
}