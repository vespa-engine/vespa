// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.io.IOException;

/**
 * Invokes the response filter the first time anything is output to the underlying ServletOutputStream.
 * The filter must be invoked before the first output call since this might cause the response
 * to be committed, i.e. locked and potentially put on the wire.
 * Any changes to the response after it has been committed might be ignored or cause exceptions.
 *
 * @author Tony Vaagenes
 */
class FilterInvokingServletOutputStream extends ServletOutputStream {
    private final ServletOutputStream delegate;
    private final OneTimeRunnable filterInvoker;

    public FilterInvokingServletOutputStream(ServletOutputStream delegate, OneTimeRunnable filterInvoker) {
        this.delegate = delegate;
        this.filterInvoker = filterInvoker;
    }

    @Override
    public boolean isReady() {
        return delegate.isReady();
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        delegate.setWriteListener(writeListener);
    }


    private void runFilterIfFirstInvocation() {
        filterInvoker.runIfFirstInvocation();
    }

    @Override
    public void write(int b) throws IOException {
        runFilterIfFirstInvocation();
        delegate.write(b);
    }


    @Override
    public void write(byte[] b) throws IOException {
        runFilterIfFirstInvocation();
        delegate.write(b);
    }

    @Override
    public void print(String s) throws IOException {
        runFilterIfFirstInvocation();
        delegate.print(s);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        runFilterIfFirstInvocation();
        delegate.write(b, off, len);
    }

    @Override
    public void print(boolean b) throws IOException {
        runFilterIfFirstInvocation();
        delegate.print(b);
    }

    @Override
    public void flush() throws IOException {
        runFilterIfFirstInvocation();
        delegate.flush();
    }

    @Override
    public void print(char c) throws IOException {
        runFilterIfFirstInvocation();
        delegate.print(c);
    }

    @Override
    public void close() throws IOException {
        runFilterIfFirstInvocation();
        delegate.close();
    }

    @Override
    public void print(int i) throws IOException {
        runFilterIfFirstInvocation();
        delegate.print(i);
    }

    @Override
    public void print(long l) throws IOException {
        runFilterIfFirstInvocation();
        delegate.print(l);
    }

    @Override
    public void print(float f) throws IOException {
        runFilterIfFirstInvocation();
        delegate.print(f);
    }

    @Override
    public void print(double d) throws IOException {
        runFilterIfFirstInvocation();
        delegate.print(d);
    }

    @Override
    public void println() throws IOException {
        runFilterIfFirstInvocation();
        delegate.println();
    }

    @Override
    public void println(String s) throws IOException {
        runFilterIfFirstInvocation();
        delegate.println(s);
    }

    @Override
    public void println(boolean b) throws IOException {
        runFilterIfFirstInvocation();
        delegate.println(b);
    }

    @Override
    public void println(char c) throws IOException {
        runFilterIfFirstInvocation();
        delegate.println(c);
    }

    @Override
    public void println(int i) throws IOException {
        runFilterIfFirstInvocation();
        delegate.println(i);
    }

    @Override
    public void println(long l) throws IOException {
        runFilterIfFirstInvocation();
        delegate.println(l);
    }

    @Override
    public void println(float f) throws IOException {
        runFilterIfFirstInvocation();
        delegate.println(f);
    }

    @Override
    public void println(double d) throws IOException {
        runFilterIfFirstInvocation();
        delegate.println(d);
    }

    @Override
    public String toString() {
        return getClass().getCanonicalName() + " (" + delegate.toString() + ")";
    }
}
