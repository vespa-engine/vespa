// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.Cursor;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Parses a template String, see {@link Template} for details.
 *
 * @author hakonhall
 */
class TemplateParser {
    private final TemplateDescriptor descriptor;
    private final Cursor start;
    private final Cursor current;
    private final TemplateBuilder templateBuilder;

    static TemplateParser parse(String text, TemplateDescriptor descriptor) {
        return parse(new TemplateDescriptor(descriptor), new Cursor(text), EnumSet.of(Sentinel.EOT));
    }

    private static TemplateParser parse(TemplateDescriptor descriptor, Cursor start, EnumSet<Sentinel> sentinel) {
        var parser = new TemplateParser(descriptor, start);
        parser.parse(parser.templateBuilder.topLevelSectionList(), sentinel);
        return parser;
    }

    private enum Sentinel { ELSE, END, EOT }

    private TemplateParser(TemplateDescriptor descriptor, Cursor start) {
        this.descriptor = descriptor;
        this.start = new Cursor(start);
        this.current = new Cursor(start);
        this.templateBuilder = new TemplateBuilder(start);
    }

    Template template() { return templateBuilder.build(); }

    private Sentinel parse(SectionList sectionList, EnumSet<Sentinel> sentinels) {
        do {
            current.advanceTo(descriptor.startDelimiter());
            if (!current.equals(start)) {
                sectionList.appendLiteralSection(current);
            }

            if (current.eot()) {
                if (!sentinels.contains(Sentinel.EOT)) {
                    throw new BadTemplateException(start, "Missing end directive for section started");
                }
                return Sentinel.EOT;
            }

            Optional<Sentinel> sentinel = parseSection(sectionList, sentinels);
            if (sentinel.isPresent()) return sentinel.get();
        } while (true);
    }

    private Optional<Sentinel> parseSection(SectionList sectionList, EnumSet<Sentinel> sentinels) {
        current.skip(descriptor.startDelimiter());

        if (current.skip(Token.VARIABLE_DIRECTIVE_CHAR)) {
            parseVariableSection(sectionList);
        } else {
            var startOfType = new Cursor(current);
            String type = skipId().orElseThrow(() -> new BadTemplateException(current, "Missing section name"));

            switch (type) {
                case "else" -> {
                    if (!sentinels.contains(Sentinel.ELSE))
                        throw new BadTemplateException(startOfType, "Stray 'else'");
                    parseEndDirective();
                    return Optional.of(Sentinel.ELSE);
                }
                case "end" -> {
                    if (!sentinels.contains(Sentinel.END))
                        throw new BadTemplateException(startOfType, "Stray 'end'");
                    parseEndDirective();
                    return Optional.of(Sentinel.END);
                }
                case "if" -> parseIfSection(sectionList);
                case "list" -> parseListSection(sectionList);
                default -> throw new BadTemplateException(startOfType, "Unknown section '" + type + "'");
            }
        }

        return Optional.empty();
    }

    private void parseVariableSection(SectionList sectionList) {
        var nameStart = new Cursor(current);
        String name = parseId();
        parseEndDelimiter(false);
        sectionList.appendVariableSection(name, nameStart, current);
    }

    private void parseEndDirective() {
        parseEndDelimiter(true);
    }

    private void parseListSection(SectionList sectionList) {
        skipRequiredWhitespaces();
        var startOfName = new Cursor(current);
        String name = parseId();
        parseEndDelimiter(true);

        TemplateParser bodyParser = parse(descriptor, current, EnumSet.of(Sentinel.END));
        current.set(bodyParser.current);

        sectionList.appendListSection(name, startOfName, current, bodyParser.templateBuilder.build());
    }

    private void parseIfSection(SectionList sectionList) {
        skipRequiredWhitespaces();
        boolean negated = current.skip(Token.NEGATE_CHAR);
        current.skipWhitespaces();
        var startOfName = new Cursor(current);
        String name = parseId();
        parseEndDelimiter(true);

        SectionList ifSectionList = new SectionList(current, templateBuilder);
        Sentinel ifSentinel = parse(ifSectionList, EnumSet.of(Sentinel.ELSE, Sentinel.END));

        Optional<SectionList> elseSectionList = Optional.empty();
        if (ifSentinel == Sentinel.ELSE) {
            elseSectionList = Optional.of(new SectionList(current, templateBuilder));
            parse(elseSectionList.get(), EnumSet.of(Sentinel.END));
        }

        sectionList.appendIfSection(negated, name, startOfName, current, ifSectionList, elseSectionList);
    }

    private void skipRequiredWhitespaces() {
        if (!current.skipWhitespaces()) {
            throw new BadTemplateException(current, "Expected whitespace");
        }
    }

    private String parseId() {
        return skipId().orElseThrow(() -> new BadTemplateException(current, "Expected identifier"));
    }

    private Optional<String> skipId() { return Token.skipId(current); }

    private void parseEndDelimiter(boolean allowSkipNewline) {
        boolean removeNewlineCharPresent = current.skip(Token.REMOVE_NEWLINE_CHAR);

        if (!current.skip(descriptor.endDelimiter()))
            throw new BadTemplateException(current, "Expected section end (" + descriptor.endDelimiter() + ")");

        // The presence of the remove-newline-char means the opposite behavior is wanted.
        if (allowSkipNewline && (removeNewlineCharPresent != descriptor.removeNewline()))
            current.skip('\n');
    }
}
