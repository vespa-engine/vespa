// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.templates;

import java.io.Writer;


/**
 * A template turns a template string and some state into
 * an instantiated string. Add support for a particular
 * template mechanism by subclassing this.
 *
 * @author bratseth
 * @deprecated use a Renderer instead
 */
@SuppressWarnings("deprecation")
@Deprecated // TODO: Remove on Vespa 7
public abstract class Template<T extends Writer> {

    /**
     * Renders this template
     *
     * @param context the context to evaluate in
     * @param writer the writer to render to
     */
    public abstract void render(Context context,T writer) throws java.io.IOException;


    /**
     * Get template name
     *
     * @return template name
     */
    public abstract String getName();

}
