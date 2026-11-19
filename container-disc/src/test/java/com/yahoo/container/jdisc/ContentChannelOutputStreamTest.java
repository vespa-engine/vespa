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
        out.write(new byte[0]);
        out.flush();
        assertEquals(List.of(8101), channel.sizes(), "an empty flush sends nothing");
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

    @Test
    void a_filled_buffer_followed_by_a_copied_whole_write_leads_to_64_KiB_buffers() throws Exception {
        out.write(bytes(8192, 1)); // fills the first buffer exactly
        out.write(bytes(65536, 2));
        out.write(3);
        out.flush();
        assertEquals(List.of(8192, 65536, 65536), channel.arraySizes());
        assertEquals(List.of(8192, 65536, 1), channel.sizes());
        assertArrayEquals(concat(bytes(8192, 1), bytes(65536, 2), new byte[] { 3 }), channel.content());
    }

    @Test
    void a_partly_filled_buffer_held_before_a_whole_write_does_not_lead_to_64_KiB_buffers() throws Exception {
        out.write(bytes(10, 1));
        out.write(bytes(70_000, 2));
        out.write(bytes(3, 3));
        out.flush();
        assertEquals(List.of(8192, 70_000, 8192), channel.arraySizes());
    }

    // Writes of 64 KiB or more: a buffer of their own

    @Test
    void a_copied_write_of_64_KiB_or_more_is_sent_whole_and_a_smaller_one_is_split_into_buffers() throws Exception {
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
        assertEquals(List.of(8192, 57343, 65536, 65537), channel.sizes());
        var expected = new ByteArrayOutputStream();
        expected.writeBytes(bytes(65535, 15));
        expected.writeBytes(bytes(65536, 16));
        expected.writeBytes(Arrays.copyOfRange(bytes(70_000, 17), 100, 100 + 65537));
        assertArrayEquals(expected.toByteArray(), channel.content(), "changes to the caller's arrays are not sent");
    }

    @Test
    void a_transferring_write_is_copied_like_any_other_write() throws Exception {
        byte[] small = bytes(100, 11);
        byte[] large = bytes(70_000, 12);
        out.nonCopyingWrite(small);
        out.nonCopyingWrite(large, 500, 65536);
        small[0]++; // the caller changes its arrays afterwards
        large[500]++;
        out.flush();
        assertEquals(List.of(100, 65536), channel.sizes());
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

    // Holding: data is sent at flush, or once the held arrays would take more than 256 KiB

    @Test
    void data_is_held_until_flush_or_until_the_held_arrays_take_more_than_256_KiB() throws Exception {
        for (int i = 0; i < 4; i++) out.nonCopyingWrite(bytes(65536, i));
        assertEquals(List.of(), channel.sizes(), "exactly 256 KiB is held");
        out.nonCopyingWrite(bytes(65536, 4));
        assertEquals(repeat(65536, 5), channel.sizes(), "the held data is sent once it takes more than 256 KiB");
        out.flush();
        assertEquals(5 * 65536, channel.content().length);
    }

    @Test
    void a_new_buffer_counts_toward_the_256_KiB() throws Exception {
        for (int i = 0; i < 4; i++) out.nonCopyingWrite(bytes(65536, i)); // exactly 256 KiB held
        out.write(1); // needs a new 8 KiB buffer
        assertEquals(repeat(65536, 4), channel.sizes(), "the held data is sent before the new buffer is made");
        out.flush();
        assertEquals(List.of(65536, 65536, 65536, 65536, 1), channel.sizes());
    }

    @Test
    void a_new_64_KiB_buffer_counts_toward_the_256_KiB() throws Exception {
        out.write(bytes(8192, 1)); // fills the first buffer, so the next one is 64 KiB
        for (int i = 0; i < 3; i++) out.nonCopyingWrite(bytes(65536, i)); // 8 KiB + 192 KiB held
        out.write(1);
        assertEquals(List.of(8192, 65536, 65536, 65536), channel.sizes(), "the held data is sent before the new buffer is made");
        out.flush();
        assertEquals(List.of(8192, 65536, 65536, 65536, 65536), channel.arraySizes());
    }

    @Test
    void a_new_buffer_that_brings_the_held_arrays_to_exactly_256_KiB_does_not_send() throws Exception {
        out.write(bytes(256 * 1024 - 8192, 1)); // a 248 KiB buffer of its own
        out.write(1); // + an 8 KiB buffer = exactly 256 KiB
        assertEquals(List.of(), channel.sizes());

        var overChannel = new RecordingChannel();
        var over = new ContentChannelOutputStream(overChannel);
        over.write(bytes(256 * 1024 - 8192 + 1, 1));
        over.write(1); // one byte over 256 KiB
        assertEquals(List.of(256 * 1024 - 8192 + 1), overChannel.sizes());
    }

    @Test
    void the_held_limit_counts_the_whole_array_not_only_the_data_in_it() throws Exception {
        out.write(bytes(8192, 1)); // fills the first buffer, so the next ones are 64 KiB
        out.write(1); // a 64 KiB buffer with one byte in it
        out.write(bytes(65536, 2)); // a buffer of its own
        out.write(1);
        assertEquals(List.of(), channel.sizes(), "8 KiB and two 64 KiB arrays are held, and a third is being filled");
        out.write(bytes(65536, 3));
        assertEquals(List.of(8192, 1, 65536, 1, 65536), channel.sizes(),
                     "the arrays take 264 KiB, so they are sent, though they hold only about 136 KiB of data");
    }

    @Test
    void every_buffer_is_sent_through_send_so_a_subclass_can_limit_pending_data() throws Exception {
        var sent = new ArrayList<ByteBuffer>();
        var subclassed = new ContentChannelOutputStream(channel) {
            @Override public void send(ByteBuffer src) throws IOException {
                sent.add(src);
                super.send(src);
            }
        };
        for (int i = 0; i < 5; i++) subclassed.nonCopyingWrite(bytes(65536, i)); // sent by the write, over 256 KiB
        subclassed.write(bytes(10, 5));
        subclassed.flush();
        assertEquals(6, sent.size());
        assertEquals(channel.writes, sent);
    }

    @Test
    void a_transferring_write_goes_through_write_so_a_subclass_sees_it() throws Exception {
        var written = new ArrayList<Integer>();
        var subclassed = new ContentChannelOutputStream(channel) {
            @Override public void write(byte[] b, int off, int len) throws IOException {
                written.add(len);
                super.write(b, off, len);
            }
        };
        subclassed.nonCopyingWrite(bytes(100, 1));
        subclassed.nonCopyingWrite(bytes(65536, 2));
        assertEquals(List.of(100, 65536), written);
    }

    // A send that throws: the rest of the held data is dropped, never sent twice

    @Test
    void when_a_send_at_flush_throws_the_rest_of_the_held_data_is_never_sent() throws Exception {
        byte[] first = bytes(70_000, 7);
        byte[] second = bytes(70_000, 8);
        byte[] third = bytes(70_000, 9);
        out.nonCopyingWrite(first);
        out.nonCopyingWrite(second);
        out.nonCopyingWrite(third);
        channel.failAtWrite = 2; // the channel takes the second buffer, then throws
        assertThrows(IOException.class, out::flush);
        assertEquals(2, channel.writes.size(), "the third buffer is not tried");
        out.flush();
        assertEquals(2, channel.writes.size(), "a later flush sends none of the held data again");
        out.write(bytes(5, 10));
        out.flush();
        assertEquals(3, channel.writes.size());
        assertArrayEquals(concat(first, second, bytes(5, 10)), channel.content(), "new data is sent after a failure");
    }

    @Test
    void when_a_send_started_by_a_write_throws_the_rest_of_the_held_data_is_never_sent() throws Exception {
        var arrays = new ArrayList<byte[]>();
        for (int i = 0; i < 5; i++) arrays.add(bytes(65536, i));
        for (int i = 0; i < 4; i++) out.nonCopyingWrite(arrays.get(i));
        channel.failAtWrite = 2; // the channel takes the second buffer, then throws
        assertThrows(IOException.class, () -> out.nonCopyingWrite(arrays.get(4)));
        out.flush(); // a retry, as ThreadedHttpRequestHandler does after an IOException
        assertEquals(2, channel.writes.size(), "the other three buffers are never sent");
        out.write(bytes(5, 1));
        out.flush();
        assertArrayEquals(concat(arrays.get(0), arrays.get(1), bytes(5, 1)), channel.content(), "new data is sent after a failure");
    }

    @Test
    void when_a_send_started_by_a_new_buffer_throws_the_unsent_held_data_is_dropped_and_the_byte_is_not_written() throws Exception {
        for (int i = 0; i < 4; i++) out.nonCopyingWrite(bytes(65536, i)); // exactly 256 KiB held
        channel.failAtWrite = 2; // the channel takes the second buffer, then throws
        assertThrows(IOException.class, () -> out.write(1));
        out.nonCopyingWrite(bytes(65536, 9));
        assertEquals(2, channel.writes.size(), "after a failure, data is held again from zero");
        out.flush();
        assertEquals(List.of(65536, 65536, 65536), channel.sizes(), "the other two buffers and the byte are dropped");
        assertArrayEquals(concat(bytes(65536, 0), bytes(65536, 1), bytes(65536, 9)), channel.content());
    }

    @Test
    void after_a_flush_throws_the_next_buffer_is_8_KiB() throws Exception {
        out.write(bytes(8192, 1)); // fills the first buffer
        out.write(bytes(10, 2)); // goes into a 64 KiB buffer
        channel.failAtWrite = 1;
        assertThrows(IOException.class, out::flush);
        out.write(bytes(10, 3));
        out.flush();
        assertEquals(8192, channel.arraySizes().get(channel.writes.size() - 1));
    }

    // Invalid input

    @Test
    void an_invalid_range_in_a_copying_write_is_an_index_out_of_bounds_exception() {
        assertThrows(IndexOutOfBoundsException.class, () -> out.write(new byte[2000], 1500, 1000));
        assertThrows(IndexOutOfBoundsException.class, () -> out.write(new byte[100_000], 50_000, 70_000));
        assertThrows(IndexOutOfBoundsException.class, () -> out.write(new byte[10], 0, -1));
    }

    @Test
    void an_invalid_range_or_a_null_array_in_a_transferring_write_is_an_io_exception() {
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
