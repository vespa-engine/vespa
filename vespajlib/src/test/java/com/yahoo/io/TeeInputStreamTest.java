package com.yahoo.io;

import java.io.*;
import java.nio.charset.StandardCharsets;

import org.junit.Test;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * @author arnej
 */
public class TeeInputStreamTest {

    @Test
    public void testSimpleInput() throws IOException {
        byte[] input = "very simple input".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(input);
        ByteArrayOutputStream gotten = new ByteArrayOutputStream();

        TeeInputStream tee = new TeeInputStream(in, gotten);
        int b = tee.read();
        assertThat(b, is((int)'v'));
        assertThat(gotten.toString(), is("very simple input"));
        for (int i = 0; i < 16; i++) {
            b = tee.read();
            // System.out.println("got["+i+"]: "+(char)b);
            assertThat(b, is(greaterThan(0)));
        }
        assertThat(tee.read(), is(-1));
    }

    private class Generator implements Runnable {
        private OutputStream dst;
        public Generator(OutputStream dst) { this.dst = dst; }
        public @Override void run() {
            for (int i = 0; i < 123456789; i++) {
                int b = i & 0x7f;
                if (b < 32) continue;
                if (b > 126) b = '\n';
                try {
                    dst.write(b);
                } catch (IOException e) {
                    return;
                }
            }
        }
    }

    @Test
    public void testPipedInput() throws IOException {
        PipedOutputStream input = new PipedOutputStream();
        PipedInputStream in = new PipedInputStream(input);
        ByteArrayOutputStream gotten = new ByteArrayOutputStream();
        TeeInputStream tee = new TeeInputStream(in, gotten);
        input.write("first input".getBytes(StandardCharsets.UTF_8));
        int b = tee.read();
        assertThat(b, is((int)'f'));
        assertThat(gotten.toString(), is("first input"));
        input.write(" second input".getBytes(StandardCharsets.UTF_8));
        b = tee.read();
        assertThat(b, is((int)'i'));
        assertThat(gotten.toString(), is("first input second input"));
        new Thread(new Generator(input)).start();
        b = tee.read();
        assertThat(b, is((int)'r'));
        byte[] ba = new byte[9];
        for (int i = 0; i < 12345; i++) {
            b = tee.read();
            // System.out.println("got["+i+"]: "+(char)b);
            assertThat(b, is(greaterThan(0)));
            assertThat(tee.read(ba), is(greaterThan(0)));
        }
        tee.close();
        String got = gotten.toString();
        // System.out.println("got length: "+got.length());
        // System.out.println("got: "+got);
        assertThat(got.length(), is(greaterThan(34567)));
    }

}
