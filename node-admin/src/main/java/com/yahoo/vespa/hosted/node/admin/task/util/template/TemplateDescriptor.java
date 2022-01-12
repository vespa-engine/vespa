// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

/**
 * Specifies the how to interpret a template text.
 *
 * @author hakonhall
 */
public class TemplateDescriptor {

    private String startDelimiter = "%{";
    private String endDelimiter = "}";
    private boolean removeNewline = true;

    public TemplateDescriptor() {}

    public TemplateDescriptor(TemplateDescriptor that) {
        this.startDelimiter = that.startDelimiter;
        this.endDelimiter = that.endDelimiter;
        this.removeNewline = that.removeNewline;
    }

    /** Use these delimiters instead of the standard "%{" and "}" to start and end a template directive. */
    public TemplateDescriptor setDelimiters(String startDelimiter, String endDelimiter) {
        this.startDelimiter = Token.verifyDelimiter(startDelimiter);
        this.endDelimiter = Token.verifyDelimiter(endDelimiter);
        return this;
    }

    /**
     * Whether to remove a newline that immediately follows a non-variable directive. The opposite
     * effect can be achieved by preceding the end delimiter with a "-" char, e.g. %{if foo-}.
     */
    public TemplateDescriptor setRemoveNewline(boolean removeNewline) {
        this.removeNewline = removeNewline;
        return this;
    }

    public String startDelimiter() { return startDelimiter; }
    public String endDelimiter() { return endDelimiter; }
    public boolean removeNewline() { return removeNewline; }
}
