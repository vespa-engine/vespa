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

    public TemplateDescriptor() {}

    public TemplateDescriptor(TemplateDescriptor that) {
        this.startDelimiter = that.startDelimiter;
        this.endDelimiter = that.endDelimiter;
    }

    /** Use these delimiters instead of the standard "%{" and "}" to start and end a template directive. */
    public TemplateDescriptor setDelimiters(String startDelimiter, String endDelimiter) {
        this.startDelimiter = Token.verifyDelimiter(startDelimiter);
        this.endDelimiter = Token.verifyDelimiter(endDelimiter);
        return this;
    }

    public String startDelimiter() { return startDelimiter; }
    public String endDelimiter() { return endDelimiter; }
}
