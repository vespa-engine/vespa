// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("deprecation") // tests the deprecated nonCopyingWrite methods
class ContentChannelOutputStreamTest {

    private final RecordingChannel channel = new RecordingChannel();
    private final ContentChannelOutputStream out = new ContentChannelOutputStream(channel);

    /** Keeps every buffer written to it, as the buffer was handed over, and completes each write at once. */
    private static class RecordingChannel implements ContentChannel {
        final List<ByteBuffer> writes = new ArrayList<>();
        int failAtWrite = -1; // the write number, counting from 1, that throws after keeping its buffer
        @Override public void write(ByteBuffer buffer, CompletionHandler handler) {
            writes.add(buffer);
            if (writes.size() == failAtWrite) throw new IllegalStateException("connection lost");
            handler.completed();
        }
        int closes = 0;
        @Override public void close(CompletionHandler handler) { closes++; handler.completed(); }

        List<Integer> sizes() { return writes.stream().map(ByteBuffer::remaining).toList(); }

        List<Integer> arraySizes() { return writes.stream().map(write -> write.array().length).toList(); }

        byte[] content() {
            var all = new ByteArrayOutputStream();
            for (ByteBuffer write : writes) all.writeBytes(copyOf(write));
            return all.toByteArray();
        }
    }

    private static byte[] bytes(int length, int seed) {
        byte[] bytes = new byte[length];
        new Random(seed).nextBytes(bytes); // no repeating pattern, so data sent from a wrong offset is noticed
        return bytes;
    }

    private static List<Integer> repeat(int size, int times) { return Collections.nCopies(times, size); }

    private static byte[] concat(byte[]... arrays) {
        var all = new ByteArrayOutputStream();
        for (byte[] array : arrays) all.writeBytes(array);
        return all.toByteArray();
    }

    // Short responses: one 8 KiB buffer, sent at flush

    @Test
    void a_short_response_is_sent_in_one_8_KiB_buffer_at_flush() throws Exception {
        out.write(bytes(100, 1));
        out.write('x');
        out.write(bytes(8000, 2));
        assertEquals(List.of(), channel.sizes(), "nothing is sent before flush");
        out.flush();
        assertEquals(List.of(8101), channel.sizes());
        assertEquals(List.of(8192), channel.arraySizes());
        assertArrayEquals(concat(bytes(100, 1), new byte[] { 'x' }, bytes(8000, 2)), channel.content());
    }

    @Test
    void empty_writes_and_a_flush_with_nothing_buffered_send_nothing() throws Exception {
        out.flush();
        out.write(new byte[0]);
        out.write(new byte[10], 10, 0);
        out.nonCopyingWrite(new byte[0]);
        out.flush();
        assertEquals(List.of(), channel.sizes());
    }

    @Test
    void close_sends_the_buffered_data_and_leaves_the_channel_open() throws Exception {
        out.write(bytes(100, 1));
        out.close();
        assertArrayEquals(bytes(100, 1), channel.content());
        out.close();
        assertEquals(List.of(100), channel.sizes(), "closing again with nothing buffered sends nothing");
        out.write(bytes(5, 2));
        out.flush();
        assertArrayEquals(concat(bytes(100, 1), bytes(5, 2)), channel.content(), "the stream can still be written after close");
        assertEquals(0, channel.closes, "closing the stream does not close the channel");
    }

    @Test
    void a_response_that_is_flushed_often_keeps_8_KiB_buffers() throws Exception {
        var expected = new ByteArrayOutputStream();
        for (int i = 0; i < 100; i++) { // like a stream of events, each flushed at once
            byte[] event = bytes(100, i);
            out.write(event);
            expected.writeBytes(event);
            out.flush();
        }
        assertEquals(repeat(8192, 100), channel.arraySizes());
        assertArrayEquals(expected.toByteArray(), channel.content(), "no buffer is written to after it is sent");
    }

    @Test
    void a_flush_starts_again_with_an_8_KiB_buffer() throws Exception {
        out.write(bytes(8192, 1)); // fills the first buffer exactly
        out.write(bytes(10, 2));
        out.flush();
        assertEquals(List.of(8192, 65536), channel.arraySizes(), "the write after a filled buffer goes into a 64 KiB buffer");
        out.write(bytes(10, 3));
        out.flush();
        assertEquals(List.of(8192, 65536, 8192), channel.arraySizes());
    }

    @Test
    void a_flush_right_when_a_buffer_is_full_starts_again_with_an_8_KiB_buffer() throws Exception {
        out.write(bytes(8192, 1)); // the first buffer is full when flush holds it
        out.flush();
        out.write(bytes(10, 2));
        out.flush();
        assertEquals(List.of(8192, 8192), channel.arraySizes());
    }

    // Long responses: 64 KiB buffers once a buffer has filled

    @Test
    void after_the_first_buffer_is_filled_a_long_response_is_sent_in_64_KiB_writes() throws Exception {
        var expected = new ByteArrayOutputStream();
        for (int i = 0; i < 40; i++) { // 40 pieces of 8,000 bytes, the size a JSON generator typically writes at a time
            byte[] piece = bytes(8000, i);
            out.write(piece);
            expected.writeBytes(piece);
            if (i == 24) assertEquals(List.of(), channel.sizes(), "8 KiB and two 64 KiB buffers are held while a third is filled");
            if (i == 25) assertEquals(List.of(8192, 65536, 65536, 65536), channel.sizes(), "sent before a fourth 64 KiB buffer is made");
        }
        out.flush();
        assertEquals(List.of(8192, 65536, 65536, 65536, 65536, 49664), channel.sizes());
        assertArrayEquals(expected.toByteArray(), channel.content());
    }

    @Test
    void single_bytes_that_fill_the_first_buffer_lead_to_a_64_KiB_buffer() throws Exception {
        var expected = new ByteArrayOutputStream();
        for (int i = 0; i < 8193; i++) {
            out.write(i);
            expected.write(i);
        }
        out.flush();
        assertEquals(List.of(8192, 65536), channel.arraySizes());
        assertEquals(List.of(8192, 1), channel.sizes());
        assertArrayEquals(expected.toByteArray(), channel.content());
        out.write(7);
        out.flush();
        expected.write(7);
        assertArrayEquals(expected.toByteArray(), channel.content(), "sent arrays are not written to again");
    }

    // Writes of 64 KiB or more: copied into the buffers like any other write

    @Test
    void a_write_of_64_KiB_or_more_is_copied_into_the_buffers() throws Exception {
        byte[] under = bytes(65535, 15);
        byte[] exact = bytes(65536, 16);
        byte[] over = bytes(70_000, 17);
        out.write(under);
        out.write(exact);
        out.write(over, 100, 65537);
        under[0]++; // the caller reuses its arrays
        exact[0]++;
        over[100]++;
        out.flush();
        assertEquals(List.of(8192, 65536, 65536, 65536), channel.arraySizes());
        assertEquals(List.of(8192, 65536, 65536, 57344), channel.sizes());
        var expected = new ByteArrayOutputStream();
        expected.writeBytes(bytes(65535, 15));
        expected.writeBytes(bytes(65536, 16));
        expected.writeBytes(Arrays.copyOfRange(bytes(70_000, 17), 100, 100 + 65537));
        assertArrayEquals(expected.toByteArray(), channel.content(), "changes to the caller's arrays are not sent");
    }

    @Test
    void small_writes_between_large_writes_share_the_64_KiB_buffers() throws Exception {
        var expected = new ByteArrayOutputStream();
        byte[] start = bytes(10_000, 1); // fills the first buffer, so the next ones are 64 KiB
        out.write(start);
        expected.writeBytes(start);
        for (int i = 0; i < 3; i++) {
            byte[] large = bytes(100 * 1024, 10 + i);
            byte[] small = bytes(200, 20 + i);
            out.write(large);
            out.write(small);
            expected.writeBytes(large);
            expected.writeBytes(small);
        }
        out.flush();
        assertEquals(List.of(8192, 65536, 65536, 65536, 65536, 47464), channel.sizes(),
                     "every buffer but the last is full: none is sent holding only a small write");
        assertArrayEquals(expected.toByteArray(), channel.content());
    }

    @Test
    void nonCopyingWrite_copies_the_data_like_write() throws Exception {
        byte[] small = bytes(100, 11);
        byte[] large = bytes(70_000, 12);
        out.nonCopyingWrite(small);
        out.nonCopyingWrite(large, 500, 65536);
        small[0]++; // the caller changes its arrays afterwards
        large[500]++;
        out.flush();
        assertEquals(List.of(8192, 57444), channel.sizes());
        assertArrayEquals(concat(bytes(100, 11), Arrays.copyOfRange(bytes(70_000, 12), 500, 500 + 65536)), channel.content());
    }

    @Test
    void a_small_write_is_copied_so_the_caller_may_reuse_its_array() throws Exception {
        byte[] reused = bytes(10, 4);
        out.write(reused);
        reused[0]++;
        out.write(bytes(10, 6), 2, 3);
        out.flush();
        assertEquals(List.of(13), channel.sizes());
        assertArrayEquals(concat(bytes(10, 4), Arrays.copyOfRange(bytes(10, 6), 2, 5)), channel.content(),
                          "the change to the reused array is not sent");
    }

    // Holding: data is sent at flush, or before a new buffer would take the held arrays over 256 KiB

    @Test
    void data_is_held_until_flush_or_until_a_new_buffer_would_take_the_held_arrays_over_256_KiB() throws Exception {
        byte[] data = bytes(8192 + 3 * 65536, 1);
        out.write(data);
        assertEquals(List.of(), channel.sizes(), "an 8 KiB and three 64 KiB buffers, 200 KiB, are held");
        out.write(7);
        assertEquals(List.of(8192, 65536, 65536, 65536), channel.sizes(),
                     "a fourth 64 KiB buffer would take the held arrays to 264 KiB, so they are sent first");
        out.flush();
        assertEquals(List.of(8192, 65536, 65536, 65536, 1), channel.sizes());
        assertArrayEquals(concat(data, new byte[] { 7 }), channel.content());
    }

    @Test
    void after_the_first_send_four_64_KiB_buffers_are_held_and_then_sent_together() throws Exception {
        out.write(bytes(8192 + 7 * 65536, 1)); // the 8 KiB buffer and seven full 64 KiB buffers
        assertEquals(List.of(8192, 65536, 65536, 65536), channel.sizes(), "the 8 KiB and the first three 64 KiB buffers were sent");
        out.write(7);
        assertEquals(repeat(65536, 4), channel.sizes().subList(4, 8),
                     "four 64 KiB buffers, exactly 256 KiB, were held until a fifth was needed");
        byte[] large = bytes(1024 * 1024, 2);
        out.write(large); // fills the rest of the buffer holding the 7, and 15 more, plus one byte in a 17th
        assertEquals(repeat(65536, 16), channel.sizes().subList(8, 24), "sent four at a time; the last buffer, with one byte, is held");
        out.flush();
        assertEquals(List.of(1), channel.sizes().subList(24, 25));
        assertArrayEquals(concat(bytes(8192 + 7 * 65536, 1), new byte[] { 7 }, large), channel.content());
    }

    @Test
    void every_buffer_is_sent_through_send_which_a_subclass_can_override() throws Exception {
        var sent = new ArrayList<ByteBuffer>();
        var subclassed = new ContentChannelOutputStream(channel) {
            @Override public void send(ByteBuffer src) throws IOException {
                sent.add(src);
                super.send(src);
            }
        };
        subclassed.write(bytes(5 * 65536, 1)); // more than 256 KiB, so some is sent by the write itself
        subclassed.write(bytes(10, 2));
        subclassed.flush();
        assertEquals(6, sent.size());
        assertEquals(channel.writes, sent);
    }

    @Test
    void a_subclass_whose_write_calls_nonCopyingWrite_does_not_loop() throws Exception {
        var subclassed = new ContentChannelOutputStream(channel) {
            @Override public void write(byte[] b, int off, int len) throws IOException {
                nonCopyingWrite(b.clone(), off, len); // worked on master, where write called nonCopyingWrite
            }
        };
        subclassed.write(bytes(100, 1), 0, 100);
        subclassed.nonCopyingWrite(bytes(10, 2));
        subclassed.flush();
        assertArrayEquals(concat(bytes(100, 1), bytes(10, 2)), channel.content());
    }

    @Test
    void a_subclass_whose_nonCopyingWrite_calls_write_does_not_loop() throws Exception {
        var subclassed = new ContentChannelOutputStream(channel) {
            @Override public void nonCopyingWrite(byte[] b, int off, int len) throws IOException {
                write(b, off, len);
            }
        };
        subclassed.nonCopyingWrite(bytes(100, 1), 0, 100);
        subclassed.write(bytes(10, 2));
        subclassed.nonCopyingWrite(bytes(5, 3));
        subclassed.flush();
        assertArrayEquals(concat(bytes(100, 1), bytes(10, 2), bytes(5, 3)), channel.content());
    }

    @Test
    void a_subclass_whose_range_methods_call_the_whole_array_methods_does_not_loop() throws Exception {
        var subclassed = new ContentChannelOutputStream(channel) {
            // worked on master, where the whole array methods did not call the range methods
            @Override public void write(byte[] b, int off, int len) throws IOException {
                super.write(Arrays.copyOfRange(b, off, off + len));
            }
            @Override public void nonCopyingWrite(byte[] b, int off, int len) throws IOException {
                super.nonCopyingWrite(Arrays.copyOfRange(b, off, off + len));
            }
        };
        subclassed.write(bytes(100, 1), 0, 100);
        subclassed.nonCopyingWrite(bytes(10, 2), 0, 10);
        subclassed.write(bytes(5, 3));
        subclassed.nonCopyingWrite(bytes(4, 4));
        subclassed.flush();
        assertArrayEquals(concat(bytes(100, 1), bytes(10, 2), bytes(5, 3), bytes(4, 4)), channel.content());
    }

    // A send that throws: the rest of the held data is dropped, never sent twice

    @Test
    void when_a_send_at_flush_throws_the_rest_of_the_held_data_is_never_sent() throws Exception {
        byte[] data = bytes(140_000, 7); // an 8 KiB, two full 64 KiB and one part full 64 KiB buffer
        out.write(data);
        channel.failAtWrite = 2; // the channel takes the second buffer, then throws
        assertThrows(IOException.class, out::flush);
        assertEquals(List.of(8192, 65536), channel.sizes(), "the last two buffers are not tried");
        out.flush();
        assertEquals(2, channel.writes.size(), "a later flush sends none of the held data again");
        out.write(bytes(5, 10));
        out.flush();
        assertArrayEquals(concat(Arrays.copyOf(data, 8192 + 65536), bytes(5, 10)), channel.content(),
                          "new data is sent after a failure");
    }

    @Test
    void when_a_send_started_by_a_write_throws_the_rest_of_the_held_data_is_never_sent() throws Exception {
        byte[] data = bytes(8192 + 3 * 65536 + 1, 1); // the last byte needs a fourth 64 KiB buffer
        channel.failAtWrite = 2; // the channel takes the second buffer, then throws
        assertThrows(IOException.class, () -> out.write(data));
        out.flush(); // a retry, as ThreadedHttpRequestHandler does after an IOException
        assertEquals(List.of(8192, 65536), channel.sizes(), "the other two buffers and the last byte are never sent");
        out.write(bytes(5, 2));
        out.flush();
        assertArrayEquals(concat(Arrays.copyOf(data, 8192 + 65536), bytes(5, 2)), channel.content(),
                          "new data is sent after a failure");
    }

    @Test
    void after_a_send_throws_data_is_held_again_from_zero() throws Exception {
        byte[] lost = bytes(8192 + 3 * 65536, 1); // an 8 KiB and three full 64 KiB buffers
        out.write(lost);
        channel.failAtWrite = 2; // the channel takes the second buffer, then throws
        assertThrows(IOException.class, () -> out.write(1)); // a fourth 64 KiB buffer is needed, so the held data is sent first
        byte[] after = bytes(3 * 65536 + 1, 2);
        out.write(after);
        assertEquals(2, channel.writes.size(), "four 64 KiB buffers, exactly 256 KiB, are held without sending");
        out.flush();
        assertEquals(List.of(8192, 65536, 65536, 65536, 65536, 1), channel.sizes(), "the other two buffers and the byte are dropped");
        assertArrayEquals(concat(Arrays.copyOf(lost, 8192 + 65536), after), channel.content());
    }

    @Test
    void after_a_flush_throws_the_next_buffer_is_8_KiB() throws Exception {
        out.write(bytes(8192, 1)); // fills the first buffer
        out.write(bytes(10, 2)); // goes into a 64 KiB buffer
        channel.failAtWrite = 1;
        assertThrows(IOException.class, out::flush);
        out.write(bytes(10, 3));
        out.flush();
        assertEquals(List.of(8192, 8192), channel.arraySizes(), "the 64 KiB buffer is dropped, and the next buffer is 8 KiB");
    }

    @Test
    void a_runtime_exception_from_a_subclass_send_is_an_io_exception_and_the_held_data_is_dropped() throws Exception {
        var subclassed = new ContentChannelOutputStream(channel) {
            @Override public void send(ByteBuffer src) { throw new IllegalStateException("rejected"); }
        };
        subclassed.write(bytes(100, 1));
        assertThrows(IOException.class, subclassed::flush);
        subclassed.flush(); // would throw again if the held data were kept
    }

    // Invalid input

    @Test
    void an_invalid_range_in_a_copying_write_is_an_index_out_of_bounds_exception() {
        assertThrows(IndexOutOfBoundsException.class, () -> out.write(new byte[2000], 1500, 1000));
        assertThrows(IndexOutOfBoundsException.class, () -> out.write(new byte[100_000], 50_000, 70_000));
        assertThrows(IndexOutOfBoundsException.class, () -> out.write(new byte[10], 0, -1));
    }

    @Test
    void an_invalid_range_or_a_null_array_in_nonCopyingWrite_is_an_io_exception() {
        assertThrows(IOException.class, () -> out.nonCopyingWrite(new byte[2000], 1500, 1000));
        assertThrows(IOException.class, () -> out.nonCopyingWrite(new byte[100_000], 50_000, 70_000));
        assertThrows(IOException.class, () -> out.nonCopyingWrite(null));
        assertThrows(IOException.class, () -> out.nonCopyingWrite(null, 0, 10));
    }

    private static byte[] copyOf(ByteBuffer buffer) {
        ByteBuffer copy = buffer.duplicate();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }

}
