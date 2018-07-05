// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.templates;

import com.yahoo.log.LogLevel;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.Writer;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Delegates to another UserTemplate, but handles any exceptions(except IOException) by logging them.
 * 
 * @author Tony Vaagenes
 * @deprecated use a renderer instead
 */
@SuppressWarnings("deprecation")
@Deprecated // TODO: Remove on Vespa 7
public class LogExceptionUserTemplateDelegator<T extends Writer> extends UserTemplate<T> {

    private static Logger log = Logger.getLogger(LogExceptionUserTemplateDelegator.class.getName());
    private final UserTemplate<T> delegate;

    public LogExceptionUserTemplateDelegator(UserTemplate<T> delegate) {
        super(LogExceptionUserTemplateDelegator.class.getSimpleName());
        this.delegate = delegate;
    }

    @Override
    public Context createContext() {
        return delegate.createContext();
    }

    @Override
    public T wrapWriter(Writer writer) {
        return delegate.wrapWriter(writer);
    }

    @Override
    public boolean isDefaultTemplateSet() {
        return delegate.isDefaultTemplateSet();
    }

    @Override
    public String getSummaryClass() {
        return delegate.getSummaryClass();
    }

    @Override
    public String getBoldOpenTag() {
        return delegate.getBoldOpenTag();
    }

    @Override
    public String getBoldCloseTag() {
        return delegate.getBoldCloseTag();
    }

    @Override
    public String getSeparatorTag() {
        return delegate.getSeparatorTag();
    }

    @Override
    public void setSummaryClass(String summaryClass) {
        delegate.setSummaryClass(summaryClass);
    }

    @Override
    public void setHighlightTags(String start, String end, String sep) {
        delegate.setHighlightTags(start, end, sep);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getMimeType() {
        return delegate.getMimeType();
    }

    @Override
    public String getEncoding() {
        return delegate.getEncoding();
    }

    @Override
    public Template<T> getTemplate(String templateName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTemplate(String templateName, Template<? extends Writer> template) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTemplateNotNull(String templateName, Template<? extends Writer> template) {
        throw new UnsupportedOperationException();
    }

    /*** Template

    @Override
    public void <methodName>(Context context, T writer) throws IOException {
        try {
            delegate.<methodName>(context, writer);
        } catch (Exception e) {
            handleException(e);
        }
    }

    ***/

    /*** Begin expanded template for
         header, footer, hit, hitFooter, error, noHits, queryContext,
         Thanks java, for giving me the opportunely to use copy-paste ***/


    @Override
    public void header(Context context, T writer) throws IOException {
        try {
            delegate.header(context, writer);
        } catch (Exception e) {
            handleException(e);
        }
    }

    @Override
    public void footer(Context context, T writer) throws IOException {
        try {
            delegate.footer(context, writer);
        } catch (Exception e) {
            handleException(e);
        }
    }

    @Override
    public void hit(Context context, T writer) throws IOException {
        try {
            delegate.hit(context, writer);
        } catch (Exception e) {
            handleException(e);
        }
    }

    @Override
    public void hitFooter(Context context, T writer) throws IOException {
        try {
            delegate.hitFooter(context, writer);
        } catch (Exception e) {
            handleException(e);
        }
    }

    @Override
    public void error(Context context, T writer) throws IOException {
        try {
            delegate.error(context, writer);
        } catch (Exception e) {
            handleException(e);
        }
    }

    @Override
    public void noHits(Context context, T writer) throws IOException {
        try {
            delegate.noHits(context, writer);
        } catch (Exception e) {
            handleException(e);
        }
    }

    @Override
    public void queryContext(Context context, T writer) throws IOException {
        try {
            delegate.queryContext(context, writer);
        } catch (Exception e) {
            handleException(e);
        }
    }

    /*** End expanded template. ***/

    private void handleException(Exception e) throws IOException {
        if (e instanceof IOException) {
            throw (IOException) e;
        } else {
            log.log(LogLevel.WARNING, "Exception thrown in " + getName()
                    + ": " + Exceptions.toMessageString(e), e);
        }
    }

    UserTemplate<T> getDelegate() {
        return delegate;
    }
}
