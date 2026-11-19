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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentChannelOutputStreamTest {

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

    @Test
    void a_short_response_is_sent_in_one_8_KiB_buffer_at_flush() throws Exception {
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
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
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
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
    void after_the_first_buffer_is_filled_a_long_response_is_sent_in_64_KiB_writes() throws Exception {
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
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
    void data_is_held_until_flush_or_until_the_held_arrays_take_more_than_256_KiB() throws Exception {
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
        for (int i = 0; i < 4; i++) out.nonCopyingWrite(bytes(65536, i));
        assertEquals(List.of(), channel.sizes(), "exactly 256 KiB is held");
        out.nonCopyingWrite(bytes(65536, 4));
        assertEquals(repeat(65536, 5), channel.sizes(), "the held data is sent once it takes more than 256 KiB");
        out.flush();
        assertEquals(5 * 65536, channel.content().length);
    }

    @Test
    void the_held_limit_counts_the_whole_array_not_only_the_data_in_it() throws Exception {
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
        out.nonCopyingWrite(new byte[256 * 1024 + 1], 0, 65536);
        assertEquals(List.of(65536), channel.sizes(), "sent at once, as its array is larger than 256 KiB");
    }

    @Test
    void a_new_buffer_counts_toward_the_256_KiB() throws Exception {
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
        for (int i = 0; i < 4; i++) out.nonCopyingWrite(bytes(65536, i)); // exactly 256 KiB held
        out.write(1); // needs a new 8 KiB buffer
        assertEquals(repeat(65536, 4), channel.sizes(), "the held data is sent before the new buffer is made");
        out.flush();
        assertEquals(List.of(65536, 65536, 65536, 65536, 1), channel.sizes());
    }

    @Test
    void a_new_buffer_that_brings_the_held_arrays_to_exactly_256_KiB_does_not_send() throws Exception {
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
        out.nonCopyingWrite(new byte[256 * 1024 - 8192], 0, 65536); // 248 KiB array
        out.write(1); // + an 8 KiB buffer = exactly 256 KiB
        assertEquals(List.of(), channel.sizes());

        var overChannel = new RecordingChannel();
        var over = new ContentChannelOutputStream(overChannel);
        over.nonCopyingWrite(new byte[256 * 1024 - 8192 + 1], 0, 65536);
        over.write(1); // one byte over 256 KiB
        assertEquals(List.of(65536), overChannel.sizes());
    }

    @Test
    void a_new_64_KiB_buffer_counts_toward_the_256_KiB() throws Exception {
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
        out.write(bytes(8192, 1)); // fills the first buffer, so the next one is 64 KiB
        for (int i = 0; i < 3; i++) out.nonCopyingWrite(bytes(65536, i)); // 8 KiB + 192 KiB held
        out.write(1);
        assertEquals(List.of(8192, 65536, 65536, 65536), channel.sizes(), "the held data is sent before the new buffer is made");
        out.flush();
        assertEquals(List.of(8192, 65536, 65536, 65536, 65536), channel.arraySizes());
    }

    @Test
    void when_a_send_started_by_a_new_buffer_throws_the_unsent_held_data_is_dropped_and_the_byte_is_not_written() throws Exception {
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
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
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
        out.write(bytes(8192, 1)); // fills the first buffer
        out.write(bytes(10, 2)); // goes into a 64 KiB buffer
        channel.failAtWrite = 1;
        assertThrows(IOException.class, out::flush);
        out.write(bytes(10, 3));
        out.flush();
        assertEquals(8192, channel.arraySizes().get(channel.writes.size() - 1));
    }

    @Test
    void when_a_send_started_by_a_write_throws_the_rest_of_the_held_data_is_never_sent() throws Exception {
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
        var arrays = new ArrayList<byte[]>();
        for (int i = 0; i < 5; i++) arrays.add(bytes(65536, i));
        for (int i = 0; i < 4; i++) out.nonCopyingWrite(arrays.get(i));
        channel.failAtWrite = 2; // the channel takes the second buffer, then throws
        assertThrows(IOException.class, () -> out.nonCopyingWrite(arrays.get(4)));
        out.flush(); // a retry, as ThreadedHttpRequestHandler does after an IOException
        assertEquals(2, channel.writes.size(), "the other three buffers are never sent");
        out.write(bytes(5, 1));
        out.flush();
        assertSame(arrays.get(0), channel.writes.get(0).array());
        assertSame(arrays.get(1), channel.writes.get(1).array());
        assertArrayEquals(bytes(5, 1), copyOf(channel.writes.get(2)), "new data is sent after a failure");
    }

    @Test
    void a_copied_array_may_be_reused_and_a_transferred_one_is_sent_as_it_is() throws Exception {
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
        byte[] reused = bytes(10, 4);
        out.write(reused);
        reused[0]++;
        byte[] transferred = bytes(70_000, 5);
        out.nonCopyingWrite(transferred);
        out.write(bytes(10, 6), 2, 3);
        out.flush();
        assertEquals(List.of(10, 70_000, 3), channel.sizes());
        assertArrayEquals(bytes(10, 4), copyOf(channel.writes.get(0)), "the reused array's change is not sent");
        assertSame(transferred, channel.writes.get(1).array(), "a large transferred array is not copied");
        assertArrayEquals(bytes(70_000, 5), copyOf(channel.writes.get(1)));
        assertArrayEquals(Arrays.copyOfRange(bytes(10, 6), 2, 5), copyOf(channel.writes.get(2)));
    }

    @Test
    void a_partly_filled_buffer_held_before_a_whole_write_does_not_lead_to_64_KiB_buffers() throws Exception {
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
        out.write(bytes(10, 1));
        out.nonCopyingWrite(bytes(70_000, 2));
        out.write(bytes(3, 3));
        out.flush();
        assertEquals(List.of(8192, 70_000, 8192), channel.arraySizes());
    }

    @Test
    void a_response_that_is_flushed_often_keeps_8_KiB_buffers() throws Exception {
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
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
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
        out.write(bytes(8192, 1)); // fills the first buffer exactly
        out.write(bytes(10, 2));
        out.flush();
        assertEquals(List.of(8192, 65536), channel.arraySizes(), "the write after a filled buffer goes into a 64 KiB buffer");
        out.write(bytes(10, 3));
        out.flush();
        assertEquals(List.of(8192, 65536, 8192), channel.arraySizes());
    }

    @Test
    void single_bytes_that_fill_the_first_buffer_lead_to_a_64_KiB_buffer() throws Exception {
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
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
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
        out.write(bytes(8192, 1)); // fills the first buffer exactly
        out.write(bytes(65536, 2));
        out.write(3);
        out.flush();
        assertEquals(List.of(8192, 65536, 65536), channel.arraySizes());
        assertEquals(List.of(8192, 65536, 1), channel.sizes());
        assertArrayEquals(concat(bytes(8192, 1), bytes(65536, 2), new byte[] { 3 }), channel.content());
    }

    @Test
    void a_filled_buffer_followed_by_a_transferred_whole_write_leads_to_64_KiB_buffers() throws Exception {
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
        out.write(bytes(8192, 1)); // fills the first buffer exactly
        out.nonCopyingWrite(bytes(65536, 2));
        out.write(3);
        out.flush();
        assertEquals(List.of(8192, 65536, 65536), channel.arraySizes());
        assertEquals(List.of(8192, 65536, 1), channel.sizes());
        assertArrayEquals(concat(bytes(8192, 1), bytes(65536, 2), new byte[] { 3 }), channel.content());
    }

    @Test
    void every_buffer_is_sent_through_send_so_a_subclass_can_limit_pending_data() throws Exception {
        var channel = new RecordingChannel();
        var sent = new ArrayList<ByteBuffer>();
        var out = new ContentChannelOutputStream(channel) {
            @Override public void send(ByteBuffer src) throws IOException {
                sent.add(src);
                super.send(src);
            }
        };
        for (int i = 0; i < 5; i++) out.nonCopyingWrite(bytes(65536, i)); // sent by the write, over 256 KiB
        out.write(bytes(10, 5));
        out.flush();
        assertEquals(6, sent.size());
        assertEquals(channel.writes, sent);
    }

    @Test
    void when_a_send_at_flush_throws_the_rest_of_the_held_data_is_never_sent() throws Exception {
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
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
        assertSame(first, channel.writes.get(0).array());
        assertSame(second, channel.writes.get(1).array());
        assertArrayEquals(bytes(5, 10), copyOf(channel.writes.get(2)), "new data is sent after a failure");
    }

    @Test
    void a_transferred_array_range_is_sent_without_the_bytes_outside_it() throws Exception {
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
        byte[] transferred = bytes(80_000, 9);
        out.nonCopyingWrite(transferred, 500, 70_000);
        out.flush();
        assertSame(transferred, channel.writes.get(0).array());
        assertArrayEquals(Arrays.copyOfRange(transferred, 500, 70_500), channel.content());
    }

    @Test
    void a_copied_write_of_64_KiB_or_more_is_sent_whole_and_a_smaller_one_is_split_into_buffers() throws Exception {
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
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
    void a_transferred_array_is_copied_when_under_64_KiB_and_sent_as_it_is_from_64_KiB() throws Exception {
        var channel = new RecordingChannel();
        var out = new ContentChannelOutputStream(channel);
        byte[] under = bytes(65535, 11);
        byte[] whole = bytes(65536, 12);
        out.nonCopyingWrite(under);
        out.nonCopyingWrite(whole);
        out.flush();
        assertEquals(List.of(8192, 57343, 65536), channel.sizes(), "under 64 KiB: copied into an 8 KiB and a 64 KiB buffer");
        assertNotSame(under, channel.writes.get(0).array());
        assertNotSame(under, channel.writes.get(1).array());
        assertSame(whole, channel.writes.get(2).array(), "64 KiB: sent as it is");
        var expected = new ByteArrayOutputStream();
        expected.writeBytes(under);
        expected.writeBytes(whole);
        assertArrayEquals(expected.toByteArray(), channel.content());
    }

    @Test
    void an_invalid_range_in_a_copying_write_is_an_index_out_of_bounds_exception() {
        var out = new ContentChannelOutputStream(new RecordingChannel());
        assertThrows(IndexOutOfBoundsException.class, () -> out.write(new byte[2000], 1500, 1000));
        assertThrows(IndexOutOfBoundsException.class, () -> out.write(new byte[100_000], 50_000, 70_000));
        assertThrows(IndexOutOfBoundsException.class, () -> out.write(new byte[10], 0, -1));
    }

    @Test
    void an_invalid_range_or_a_null_array_in_a_transferring_write_is_an_io_exception() {
        var out = new ContentChannelOutputStream(new RecordingChannel());
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
