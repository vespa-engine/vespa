// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Simon Thoresen Hult
 */
public class Ascii {

    public final static char ESCAPE_CHAR = '\\';

    public static String encode(String str, Charset charset, int... requiresEscape) {
        return newEncoder(charset, requiresEscape).encode(str);
    }

    public static String decode(String str, Charset charset) {
        return newDecoder(charset).decode(str);
    }

    public static Encoder newEncoder(Charset charset, int... requiresEscape) {
        switch (requiresEscape.length) {
        case 0:
            return new Encoder(charset, new EmptyPredicate());
        case 1:
            return new Encoder(charset, new SingletonPredicate(requiresEscape[0]));
        default:
            return new Encoder(charset, new ArrayPredicate(requiresEscape));
        }
    }

    public static Decoder newDecoder(Charset charset) {
        return new Decoder(charset);
    }

    public static class Encoder {

        private final Charset charset;
        private final EncodePredicate predicate;

        private Encoder(Charset charset, EncodePredicate predicate) {
            this.charset = charset;
            this.predicate = predicate;
        }

        public String encode(String str) {
            StringBuilder out = new StringBuilder();
            for (int c : new CodePointSequence(str)) {
                if (c < 0x20 || c >= 0x7F || c == ESCAPE_CHAR || predicate.requiresEscape(c)) {
                    escape(c, out);
                } else {
                    out.appendCodePoint(c);
                }
            }
            return out.toString();
        }

        private void escape(int c, StringBuilder out) {
            switch (c) {
            case ESCAPE_CHAR:
                out.append(ESCAPE_CHAR).append(ESCAPE_CHAR);
                break;
            case '\f':
                out.append(ESCAPE_CHAR).append("f");
                break;
            case '\n':
                out.append(ESCAPE_CHAR).append("n");
                break;
            case '\r':
                out.append(ESCAPE_CHAR).append("r");
                break;
            case '\t':
                out.append(ESCAPE_CHAR).append("t");
                break;
            default:
                ByteBuffer buf = charset.encode(CharBuffer.wrap(Character.toChars(c)));
                while (buf.hasRemaining()) {
                    out.append(ESCAPE_CHAR).append(String.format("x%02X", buf.get()));
                }
                break;
            }
        }
    }

    public static class Decoder {

        private final Charset charset;

        private Decoder(Charset charset) {
            this.charset = charset;
        }

        public String decode(String str) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (Iterator<Integer> it = new CodePointIterator(str); it.hasNext(); ) {
                int c = it.next();
                if (c == ESCAPE_CHAR) {
                    unescape(it, out);
                } else {
                    ByteBuffer buf = charset.encode(CharBuffer.wrap(Character.toChars(c)));
                    while (buf.hasRemaining()) {
                        out.write(buf.get());
                    }
                }
            }
            return new String(out.toByteArray(), charset);
        }

        private void unescape(Iterator<Integer> it, ByteArrayOutputStream out) {
            int c = it.next();
            switch (c) {
            case 'f':
                out.write('\f');
                break;
            case 'n':
                out.write('\n');
                break;
            case 'r':
                out.write('\r');
                break;
            case 't':
                out.write('\t');
                break;
            case 'x':
                int x1 = it.next();
                int x2 = it.next();
                out.write((Character.digit(x1, 16) << 4) +
                          (Character.digit(x2, 16)));
                break;
            default:
                out.write(c);
                break;
            }
        }
    }

    private static interface EncodePredicate {

        boolean requiresEscape(int codePoint);
    }

    private static class EmptyPredicate implements EncodePredicate {

        @Override
        public boolean requiresEscape(int codePoint) {
            return false;
        }
    }

    private static class SingletonPredicate implements EncodePredicate {

        final int requiresEscape;

        private SingletonPredicate(int requiresEscape) {
            this.requiresEscape = requiresEscape;
        }

        @Override
        public boolean requiresEscape(int codePoint) {
            return codePoint == requiresEscape;
        }
    }

    private static class ArrayPredicate implements EncodePredicate {

        final Set<Integer> requiresEscape = new TreeSet<>();

        private ArrayPredicate(int[] requiresEscape) {
            for (int codePoint : requiresEscape) {
                this.requiresEscape.add(codePoint);
            }
        }

        @Override
        public boolean requiresEscape(int codePoint) {
            return requiresEscape.contains(codePoint);
        }
    }

    private static class CodePointSequence implements Iterable<Integer> {

        final String str;

        CodePointSequence(String str) {
            this.str = str;
        }

        @Override
        public Iterator<Integer> iterator() {
            return new CodePointIterator(str);
        }
    }

    private static class CodePointIterator implements Iterator<Integer> {

        final String str;
        int idx = 0;

        CodePointIterator(String str) {
            this.str = str;
        }

        @Override
        public boolean hasNext() {
            return idx < str.length();
        }

        @Override
        public Integer next() {
            int c = str.codePointAt(idx);
            idx += Character.charCount(c);
            return c;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
