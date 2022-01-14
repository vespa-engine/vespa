// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.Cursor;
import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

import java.util.Optional;

/**
 * @author hakonhall
 */
class IfSection extends Section {
    private final boolean negated;
    private final String name;
    private final Cursor nameOffset;
    private final SectionList ifSections;
    private final Optional<SectionList> elseSections;

    IfSection(CursorRange range, boolean negated, String name, Cursor nameOffset,
              SectionList ifSections, Optional<SectionList> elseSections) {
        super("if", range);
        this.negated = negated;
        this.name = name;
        this.nameOffset = nameOffset;
        this.ifSections = ifSections;
        this.elseSections = elseSections;
    }

    String name() { return name; }
    Cursor nameOffset() { return nameOffset; }

    @Override
    void appendTo(StringBuilder buffer) {
        Optional<String> stringValue = template().getVariableValue(name);
        if (stringValue.isEmpty())
            throw new TemplateNameNotSetException(name, nameOffset);

        final boolean value;
        if (stringValue.get().equals("true")) {
            value = true;
        } else if (stringValue.get().equals("false")) {
            value = false;
        } else {
            throw new NotBooleanValueTemplateException(name);
        }

        boolean condition = negated ? !value : value;
        if (condition) {
            ifSections.sections().forEach(section -> section.appendTo(buffer));
        } else if (elseSections.isPresent()) {
            elseSections.get().sections().forEach(section -> section.appendTo(buffer));
        }
    }

    @Override
    void appendCopyTo(SectionList sectionList) {
        SectionList ifSectionCopy = new SectionList(ifSections.range().start(), sectionList.templateBuilder());
        ifSections.sections().forEach(section -> section.appendCopyTo(ifSectionCopy));

        Optional<SectionList> elseSectionCopy = elseSections.map(elseSections2 -> {
            SectionList elseSectionCopy2 = new SectionList(elseSections2.range().start(),
                                                           sectionList.templateBuilder());
            elseSections2.sections().forEach(section -> section.appendCopyTo(elseSectionCopy2));
            return elseSectionCopy2;
        });

        sectionList.appendIfSection(negated, name, nameOffset, range().end(), ifSectionCopy, elseSectionCopy);
    }
}
