package com.yahoo.vespa.hosted.controller.application.pkg;

import com.yahoo.security.X509CertificateUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.yahoo.security.X509CertificateUtils.certificateListFromPem;
import static java.io.OutputStream.nullOutputStream;
import static java.lang.Math.min;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Wraps a zipped application package stream.
 * This allows replacing content as the input stream is read.
 * This also retains a truncated {@link ApplicationPackage}, containing only the specified set of files,
 * which can be accessed when this stream is fully exhausted.
 *
 * @author jonmv
 */
public class ApplicationPackageStream {

    private final Supplier<Replacer> replacer;
    private final Supplier<Predicate<String>> filter;
    private final Supplier<InputStream> in;
    private final AtomicReference<ApplicationPackage> truncatedPackage = new AtomicReference<>();

    public static Supplier<Replacer> addingCertificate(Optional<X509Certificate> certificate) {
        return certificate.map(cert -> Replacer.of(Map.of(ApplicationPackage.trustedCertificatesFile,
                                                          trustBytes -> append(trustBytes, cert))))
                          .orElse(Replacer.of(Map.of()));
    }

    static InputStream append(InputStream trustIn, X509Certificate cert) {
        try {
            List<X509Certificate> trusted = trustIn == null ? new ArrayList<>()
                                                            : new ArrayList<>(certificateListFromPem(new String(trustIn.readAllBytes(), UTF_8)));
            trusted.add(cert);
            return new ByteArrayInputStream(X509CertificateUtils.toPem(trusted).getBytes(UTF_8));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Stream that effectively copies the input stream to its {@link #truncatedPackage()} when exhausted. */
    public ApplicationPackageStream(Supplier<InputStream> in) {
        this(in, () -> __ -> true, Map.of());
    }

    /** Stream that replaces the indicated entries, and copies all metadata files to its {@link #truncatedPackage()} when exhausted. */
    public ApplicationPackageStream(Supplier<InputStream> in, Supplier<Replacer> replacer) {
        this(in, () -> name -> ApplicationPackage.prePopulated.contains(name) || name.endsWith(".xml"), replacer);
    }

    /** Stream that replaces the indicated entries, and copies the filtered entries to its {@link #truncatedPackage()} when exhausted. */
    public ApplicationPackageStream(Supplier<InputStream> in, Supplier<Predicate<String>> truncation, Map<String, UnaryOperator<InputStream>> replacements) {
        this(in, truncation, Replacer.of(replacements));
    }

    /** Stream that uses the given replacer to modify content, and copies the filtered entries to its {@link #truncatedPackage()} when exhausted. */
    public ApplicationPackageStream(Supplier<InputStream> in, Supplier<Predicate<String>> truncation, Supplier<Replacer> replacer) {
        this.in = in;
        this.filter = truncation;
        this.replacer = replacer;
    }

    /**
     * Returns a new stream continaing the zipped application package this wraps. Separate streams may exist concurrently,
     * and the first to be exhausted will populate the truncated application package.
     */
    public InputStream zipStream() {
        return new Stream(in.get(), replacer.get(), filter.get(), truncatedPackage);
    }

    /**
     * Returns the application package backed by only the files indicated by the truncation filter.
     * Throws if no instances of {@link #zipStream()} have been exhausted yet.
     */
    public ApplicationPackage truncatedPackage() {
        ApplicationPackage truncated = truncatedPackage.get();
        if (truncated == null) throw new IllegalStateException("must completely exhaust input before reading package");
        return truncated;
    }

    private static class Stream extends InputStream {

        private final byte[] inBuffer = new byte[1 << 16];
        private final ByteArrayOutputStream teeOut = new ByteArrayOutputStream(1 << 16);
        private final ZipOutputStream teeZip = new ZipOutputStream(teeOut);
        private final ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
        private final ZipOutputStream outZip = new ZipOutputStream(out);
        private final AtomicReference<ApplicationPackage> truncatedPackage;
        private final InputStream in;
        private final ZipInputStream inZip;
        private final Replacer replacer;
        private final Predicate<String> filter;
        private byte[] currentOut = new byte[0];
        private InputStream currentIn = InputStream.nullInputStream();
        private boolean includeCurrent = false;
        private int pos = 0;
        private boolean closed = false;
        private boolean done = false;

        private Stream(InputStream in, Replacer replacer, Predicate<String> filter, AtomicReference<ApplicationPackage> truncatedPackage) {
            this.in = in;
            this.inZip = new ZipInputStream(in);
            this.replacer = replacer;
            this.filter = filter;
            this.truncatedPackage = truncatedPackage;
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
                // We may still have replacements to fill in, but if we don't, we're done filling, forever!
                name = replacer.next();
                if (name == null) {
                    outZip.close(); // This typically makes new output available, so must check for that after this.
                    teeZip.close();
                    currentIn = nullInputStream();
                    truncatedPackage.compareAndSet(null, new ApplicationPackage(teeOut.toByteArray()));
                    done = true;
                    return;
                }
            }
            else {
                name = next.getName();
                content = new FilterInputStream(inZip) { @Override public void close() { } }; // Protect inZip from replacements closing it.
            }

            includeCurrent = truncatedPackage.get() == null && filter.test(name);
            currentIn = replacer.modify(name, content);
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
            return 0xff & currentOut[pos++];
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
        public void close() {
            if ( ! closed) try {
                transferTo(nullOutputStream());    // Finish reading the zip, to populate the truncated package in case of errors.
                in.transferTo(nullOutputStream()); // For some inane reason, ZipInputStream doesn't exhaust its wrapped input.
                inZip.close();
                closed = true;
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

    }

    /** Replaces entries in a zip stream as they are encountered, then appends remaining entries at the end. */
    public interface Replacer {

        /** Called when the entries of the original zip stream are exhausted. Return remaining names, or {@code null} when none left. */
        String next();

        /** Modify content for a given name; return {@code null} for removal; in is {@code null} for entries not present in the input. */
        InputStream modify(String name, InputStream in);

        /**
         * Wraps a map of fixed replacements, and:
         * <ul>
         * <li>Removes entries whose value is {@code null}.</li>
         * <li>Modifies entries present in both input and the map.</li>
         * <li>Appends entries present exclusively in the map.</li>
         * <li>Writes all other entries as they are.</li>
         * </ul>
         */
        static Supplier<Replacer> of(Map<String, UnaryOperator<InputStream>> replacements) {
            return () -> new Replacer() {
                final Map<String, UnaryOperator<InputStream>> remaining = new HashMap<>(replacements);
                @Override public String next() {
                    return remaining.isEmpty() ? null : remaining.keySet().iterator().next();
                }
                @Override public InputStream modify(String name, InputStream in) {
                    UnaryOperator<InputStream> mapper = remaining.remove(name);
                    return mapper == null ? in : mapper.apply(in);
                }
            };
        }

    }

}
