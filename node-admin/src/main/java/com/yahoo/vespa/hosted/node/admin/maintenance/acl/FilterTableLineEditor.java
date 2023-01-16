// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.Acl;
import com.yahoo.vespa.hosted.node.admin.task.util.file.LineEdit;
import com.yahoo.vespa.hosted.node.admin.task.util.file.LineEditor;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;

import java.util.List;

/**
 * An editor that assumes all rules in the filter table are exactly as the wanted rules
 *
 * @author smorgrav
 */
class FilterTableLineEditor implements LineEditor {

    private final List<String> wantedRules;
    private int position = 0;

    private FilterTableLineEditor(List<String> wantedRules) {
        this.wantedRules = List.copyOf(wantedRules);
    }

    static FilterTableLineEditor from(Acl acl, IPVersion ipVersion) {
        List<String> rules = acl.toRules(ipVersion);
        return new FilterTableLineEditor(rules);
    }

    @Override
    public LineEdit edit(String line) {
        int index = indexOf(wantedRules, line, position);
        // Unwanted rule, remove
        if (index < 0) return LineEdit.remove();

        // Wanted rule at the expected position, no diff
        if (index == position) {
            position++;
            return LineEdit.none();
        }

        // Insert the rules between position and index before index
        List<String> toInsert = wantedRules.subList(position, index);
        position = ++index;
        return LineEdit.insertBefore(toInsert);
    }

    @Override
    public List<String> onComplete() {
        return wantedRules.subList(position, wantedRules.size());
    }

    private static <T> int indexOf(List<T> list, T value, int startPos) {
        for (int i = startPos; i < list.size(); i++) {
            if (value.equals(list.get(i)))
                return i;
        }

        return -1;
    }
}
