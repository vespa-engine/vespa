package com.yahoo.vespa.hosted.controller.application.pkg;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static java.lang.Math.min;
import static java.util.function.UnaryOperator.identity;

/**
 * Wraps a zipped application package stream.
 * This allows replacing content as the input stream is read.
 * This also retains a truncated {@link ApplicationPackage}, containing only the specified set of files,
 * which can be accessed when this stream is fully exhausted.
 *
 * @author jonmv
 */
public class ApplicationPackageStream extends InputStream {

    private final byte[] inBuffer = new byte[1 << 16];
    private final ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
    private final ZipOutputStream outZip = new ZipOutputStream(out);
    private final ByteArrayOutputStream teeOut = new ByteArrayOutputStream(1 << 16);
    private final ZipOutputStream teeZip = new ZipOutputStream(teeOut);
    private final Map<String, UnaryOperator<InputStream>> replacements;
    private final Predicate<String> filter;
    private final ZipInputStream inZip;

    private byte[] currentOut = new byte[0];
    private InputStream currentIn = InputStream.nullInputStream();
    private boolean includeCurrent = false;
    private ApplicationPackage ap = null;
    private int pos = 0;
    private boolean done = false;
    private boolean closed = false;

    public ApplicationPackageStream(InputStream in) {
        this(in, __ -> true, Map.of());
    }

    public ApplicationPackageStream(InputStream in, Predicate<String> truncation, Map<String, UnaryOperator<InputStream>> replacements) {
        this.inZip = new ZipInputStream(in);
        this.filter = truncation;
        this.replacements = new HashMap<>(replacements);
    }

    public ApplicationPackage truncatedPackage() {
        if (ap == null) throw new IllegalStateException("must completely exhaust input before reading package");
        return ap;
    }

    private void fill() throws IOException {
        if (done) return;
        while (out.size() == 0) {
            // Exhaust current entry first.
            int i, n = out.size();
            while (out.size() == 0 && (i = currentIn.read(inBuffer)) != -1) {
                if (includeCurrent) teeZip.write(inBuffer, 0, i);
                outZip.write(inBuffer, 0, i);
                n += i;
            }

            // Current entry exhausted, look for next.
            if (n == 0) {
                next();
                if (done) break;
            }
        }

        currentOut = out.toByteArray();
        out.reset();
        pos = 0;
    }

    private void next() throws IOException {
        if (includeCurrent) teeZip.closeEntry();
        outZip.closeEntry();

        ZipEntry next = inZip.getNextEntry();
        String name;
        InputStream content = null;
        if (next == null) {
            // We may still have replacements to fill in, but if we don't, we're done filling, for ever!
            if (replacements.isEmpty()) {
                outZip.close(); // This typically makes new output available, so must check for that after this.
                teeZip.close();
                currentIn = nullInputStream();
                ap = new ApplicationPackage(teeOut.toByteArray());
                done = true;
                return;
            }
            name = replacements.keySet().iterator().next();
        }
        else {
            name = next.getName();
            content = new FilterInputStream(inZip) { @Override public void close() { } }; // Protect inZip from replacements closing it.
        }

        includeCurrent = filter.test(name);

        UnaryOperator<InputStream> mapper = replacements.remove(name);
        currentIn = mapper == null ? content : mapper.apply(content);

        if (currentIn == null) {
            currentIn = InputStream.nullInputStream();
        }
        else {
            if (includeCurrent) teeZip.putNextEntry(new ZipEntry(name));
            outZip.putNextEntry(new ZipEntry(name));
        }
    }

    @Override
    public int read() throws IOException {
        if (closed) throw new IOException("stream closed");
        if (pos == currentOut.length) {
            fill();
            if (pos == currentOut.length) return -1;
        }
        return 0xff & inBuffer[pos++];
    }

    @Override
    public int read(byte[] out, int off, int len) throws IOException {
        if (closed) throw new IOException("stream closed");
        if ((off | len | (off + len) | (out.length - (off + len))) < 0) throw new IndexOutOfBoundsException();
        if (pos == currentOut.length) {
            fill();
            if (pos == currentOut.length) return -1;
        }
        int n = min(currentOut.length - pos, len);
        System.arraycopy(currentOut, pos, out, off, n);
        pos += n;
        return n;
    }

    @Override
    public int available() throws IOException {
        return pos == currentOut.length && done ? 0 : 1;
    }

    @Override
    public void close() throws IOException {
        if (closed != (closed = true)) inZip.close();
    }

    public static class LazyInputStream extends InputStream {

        private Supplier<InputStream> source;
        private InputStream delegate;

        public LazyInputStream(Supplier<InputStream> source) {
            this.source = source;
        }

        private InputStream in() {
            if (delegate == null) {
                delegate = source.get();
                source = null;
            }
            return delegate;
        }

        @Override public int read() throws IOException { return in().read(); }
        @Override public int read(byte[] b, int off, int len) throws IOException { return in().read(b, off, len); }
        @Override public long skip(long n) throws IOException { return in().skip(n); }
        @Override public int available() throws IOException { return in().available(); }
        @Override public void close() throws IOException { in().close(); }
        @Override public synchronized void mark(int readlimit) { in().mark(readlimit); }
        @Override public synchronized void reset() throws IOException { in().reset(); }
        @Override public boolean markSupported() { return in().markSupported(); }

    }

}
