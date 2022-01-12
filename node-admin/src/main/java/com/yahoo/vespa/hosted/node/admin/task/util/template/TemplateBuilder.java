// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.Cursor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author hakonhall
 */
class TemplateBuilder {
    /** The top-level section list in this template. */
    private final SectionList sectionList;
    private final List<Section> allSections = new ArrayList<>();
    private final Map<String, VariableSection> sampleVariables = new HashMap<>();
    private final Map<String, IfSection> sampleIfSections = new HashMap<>();
    private final Map<String, ListSection> lists = new HashMap<>();

    TemplateBuilder(Cursor start) {
        this.sectionList = new SectionList(start, this);
    }

    SectionList topLevelSectionList() { return sectionList; }

    void addLiteralSection(LiteralSection section) {
        allSections.add(section);
    }

    void addVariableSection(VariableSection section) {
        // It's OK if the same name is used in an if-directive (as long as the value is boolean,
        // determined when set on a template).

        ListSection existing = lists.get(section.name());
        if (existing != null)
            throw new NameAlreadyExistsTemplateException(section.name(), existing.nameOffset(),
                                                         section.nameOffset());

        sampleVariables.put(section.name(), section);
        allSections.add(section);
    }

    void addIfSection(IfSection section) {
        // It's OK if the same name is used in a variable section (as long as the value is boolean,
        // determined when set on a template).

        ListSection list = lists.get(section.name());
        if (list != null)
            throw new NameAlreadyExistsTemplateException(section.name(), list.nameOffset(),
                                                         section.nameOffset());

        sampleIfSections.put(section.name(), section);
        allSections.add(section);
    }

    void addListSection(ListSection section) {
        VariableSection variableSection = sampleVariables.get(section.name());
        if (variableSection != null)
            throw new NameAlreadyExistsTemplateException(section.name(), variableSection.nameOffset(),
                                                         section.nameOffset());

        IfSection ifSection = sampleIfSections.get(section.name());
        if (ifSection != null)
            throw new NameAlreadyExistsTemplateException(section.name(), ifSection.nameOffset(),
                                                         section.nameOffset());

        ListSection previous = lists.put(section.name(), section);
        if (previous != null)
            throw new NameAlreadyExistsTemplateException(section.name(), previous.nameOffset(),
                                                         section.nameOffset());
        allSections.add(section);
    }

    Template build() {
        var template = new Template(sectionList.range(), sectionList.sections(), lists);
        allSections.forEach(section -> section.setTemplate(template));
        return template;
    }
}
