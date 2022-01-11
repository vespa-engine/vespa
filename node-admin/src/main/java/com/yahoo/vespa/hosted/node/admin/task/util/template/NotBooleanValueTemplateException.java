// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

/**
 * @author hakonhall
 */
public class NotBooleanValueTemplateException extends TemplateException {
    public NotBooleanValueTemplateException(String name) {
        super(name + " was set to a non-boolean value: must be true or false");
    }
}
