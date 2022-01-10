// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.Cursor;

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
    private final FormEndsIn formEndsIn;

    static TemplateParser parse(String text, TemplateDescriptor descriptor) {
        return parse(new TemplateDescriptor(descriptor), new Cursor(text), FormEndsIn.EOT);
    }

    private static TemplateParser parse(TemplateDescriptor descriptor, Cursor start, FormEndsIn formEndsIn) {
        var parser = new TemplateParser(descriptor, start, formEndsIn);
        parser.parse();
        return parser;
    }

    private enum FormEndsIn { EOT, END }

    TemplateParser(TemplateDescriptor descriptor, Cursor start, FormEndsIn formEndsIn) {
        this.descriptor = descriptor;
        this.start = new Cursor(start);
        this.current = new Cursor(start);
        this.templateBuilder = new TemplateBuilder(start);
        this.formEndsIn = formEndsIn;
    }

    Template template() { return templateBuilder.build(); }

    private void parse() {
        do {
            current.advanceTo(descriptor.startDelimiter());
            if (!current.equals(start)) {
                templateBuilder.appendLiteralSection(current);
            }

            if (current.eot()) {
                if (formEndsIn == FormEndsIn.END) {
                    throw new BadTemplateException(current,
                                                   "Missing end directive for section started at " +
                                                   start.calculateLocation().lineAndColumnText());
                }
                return;
            }

            if (!parseSection()) return;
        } while (true);
    }

    /** Returns true if end was reached (according to formEndsIn). */
    private boolean parseSection() {
        var startOfDirective = new Cursor(current);
        current.skip(descriptor.startDelimiter());

        if (current.skip(descriptor.variableDirectiveChar())) {
            parseVariableSection();
        } else {
            var startOfType = new Cursor(current);
            String type = skipId().orElseThrow(() -> new BadTemplateException(current, "Missing section name"));

            switch (type) {
                case "end":
                    if (formEndsIn == FormEndsIn.EOT)
                        throw new BadTemplateException(startOfType, "Extraneous 'end'");
                    parseEndDirective();
                    return false;
                case "form":
                    parseSubformSection();
                    break;
                default:
                    throw new BadTemplateException(startOfType, "Unknown section '" + type + "'");
            }
        }

        return !current.eot();
    }

    private void parseVariableSection() {
        var nameStart = new Cursor(current);
        String name = parseId();
        parseEndDelimiter(true);
        templateBuilder.appendVariableSection(name, nameStart, current);
    }

    private void parseEndDirective() {
        parseEndDelimiter(true);
    }

    private void parseSubformSection() {
        skipRequiredWhitespaces();
        var startOfName = new Cursor(current);
        String name = parseId();
        parseEndDelimiter(true);

        TemplateParser bodyParser = parse(descriptor, current, FormEndsIn.END);
        current.set(bodyParser.current);

        templateBuilder.appendSubformSection(name, startOfName, current, bodyParser.template());
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

    private boolean parseEndDelimiter(boolean skipNewline) {
        boolean removeNewline = current.skip(descriptor.removeNewlineChar());
        if (!current.skip(descriptor.endDelimiter()))
            throw new BadTemplateException(current, "Expected section end (" + descriptor.endDelimiter() + ")");

        if (skipNewline && removeNewline)
            current.skip('\n');

        return removeNewline;
    }
}
