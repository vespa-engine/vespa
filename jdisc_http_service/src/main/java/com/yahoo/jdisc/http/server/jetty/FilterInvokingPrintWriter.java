// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Locale;

/**
 * Invokes the response filter the first time anything is output to the underlying PrintWriter.
 * The filter must be invoked before the first output call since this might cause the response
 * to be committed, i.e. locked and potentially put on the wire.
 * Any changes to the response after it has been committed might be ignored or cause exceptions.
 * @author Tony Vaagenes
 */
final class FilterInvokingPrintWriter extends PrintWriter {
    private final PrintWriter delegate;
    private final OneTimeRunnable filterInvoker;

    public FilterInvokingPrintWriter(PrintWriter delegate, OneTimeRunnable filterInvoker) {
        /* The PrintWriter class both
         * 1) exposes new methods, the PrintWriter "interface"
         * 2) implements PrintWriter and Writer methods that does some extra things before calling down to the writer methods.
         * If super was invoked with the delegate PrintWriter, the superclass would behave as a PrintWriter(PrintWriter),
         * i.e. the extra things in 2. would be done twice.
         * To avoid this, all the methods of PrintWriter are overridden with versions that forward directly to the underlying delegate
         * instead of going through super.
         * The super class is initialized with a non-functioning writer to catch mistakenly non-overridden methods.
         */
        super(new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                throwAssertionError();
            }

            private void throwAssertionError() {
                throw new AssertionError(FilterInvokingPrintWriter.class.getName() + " failed to delegate to the underlying writer");
            }

            @Override
            public void flush() throws IOException {
                throwAssertionError();
            }

            @Override
            public void close() throws IOException {
                throwAssertionError();
            }
        });

        this.delegate = delegate;
        this.filterInvoker = filterInvoker;
    }

    @Override
    public String toString() {
        return getClass().getName() + " (" + super.toString() + ")";
    }

    private void runFilterIfFirstInvocation() {
        filterInvoker.runIfFirstInvocation();
    }

    @Override
    public void flush() {
        runFilterIfFirstInvocation();
        delegate.flush();
    }

    @Override
    public void close() {
        runFilterIfFirstInvocation();
        delegate.close();
    }

    @Override
    public boolean checkError() {
        return delegate.checkError();
    }

    @Override
    public void write(int c) {
        runFilterIfFirstInvocation();
        delegate.write(c);
    }

    @Override
    public void write(char[] buf, int off, int len) {
        runFilterIfFirstInvocation();
        delegate.write(buf, off, len);
    }

    @Override
    public void write(char[] buf) {
        runFilterIfFirstInvocation();
        delegate.write(buf);
    }

    @Override
    public void write(String s, int off, int len) {
        runFilterIfFirstInvocation();
        delegate.write(s, off, len);
    }

    @Override
    public void write(String s) {
        runFilterIfFirstInvocation();
        delegate.write(s);
    }

    @Override
    public void print(boolean b) {
        runFilterIfFirstInvocation();
        delegate.print(b);
    }

    @Override
    public void print(char c) {
        runFilterIfFirstInvocation();
        delegate.print(c);
    }

    @Override
    public void print(int i) {
        runFilterIfFirstInvocation();
        delegate.print(i);
    }

    @Override
    public void print(long l) {
        runFilterIfFirstInvocation();
        delegate.print(l);
    }

    @Override
    public void print(float f) {
        runFilterIfFirstInvocation();
        delegate.print(f);
    }

    @Override
    public void print(double d) {
        runFilterIfFirstInvocation();
        delegate.print(d);
    }

    @Override
    public void print(char[] s) {
        runFilterIfFirstInvocation();
        delegate.print(s);
    }

    @Override
    public void print(String s) {
        runFilterIfFirstInvocation();
        delegate.print(s);
    }

    @Override
    public void print(Object obj) {
        runFilterIfFirstInvocation();
        delegate.print(obj);
    }

    @Override
    public void println() {
        runFilterIfFirstInvocation();
        delegate.println();
    }

    @Override
    public void println(boolean x) {
        runFilterIfFirstInvocation();
        delegate.println(x);
    }

    @Override
    public void println(char x) {
        runFilterIfFirstInvocation();
        delegate.println(x);
    }

    @Override
    public void println(int x) {
        runFilterIfFirstInvocation();
        delegate.println(x);
    }

    @Override
    public void println(long x) {
        runFilterIfFirstInvocation();
        delegate.println(x);
    }

    @Override
    public void println(float x) {
        runFilterIfFirstInvocation();
        delegate.println(x);
    }

    @Override
    public void println(double x) {
        runFilterIfFirstInvocation();
        delegate.println(x);
    }

    @Override
    public void println(char[] x) {
        runFilterIfFirstInvocation();
        delegate.println(x);
    }

    @Override
    public void println(String x) {
        runFilterIfFirstInvocation();
        delegate.println(x);
    }

    @Override
    public void println(Object x) {
        runFilterIfFirstInvocation();
        delegate.println(x);
    }

    @Override
    public PrintWriter printf(String format, Object... args) {
        runFilterIfFirstInvocation();
        return delegate.printf(format, args);
    }

    @Override
    public PrintWriter printf(Locale l, String format, Object... args) {
        runFilterIfFirstInvocation();
        return delegate.printf(l, format, args);
    }

    @Override
    public PrintWriter format(String format, Object... args) {
        runFilterIfFirstInvocation();
        return delegate.format(format, args);
    }

    @Override
    public PrintWriter format(Locale l, String format, Object... args) {
        runFilterIfFirstInvocation();
        return delegate.format(l, format, args);
    }

    @Override
    public PrintWriter append(CharSequence csq) {
        runFilterIfFirstInvocation();
        return delegate.append(csq);
    }

    @Override
    public PrintWriter append(CharSequence csq, int start, int end) {
        runFilterIfFirstInvocation();
        return delegate.append(csq, start, end);
    }

    @Override
    public PrintWriter append(char c) {
        runFilterIfFirstInvocation();
        return delegate.append(c);
    }
}
