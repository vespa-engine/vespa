package com.yahoo.concurrent;// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bjorncs
 */
class CompletableFuturesTest {

    @Test
    public void firstof_completes_when_first_futures_has_completed() {
        CompletableFuture<String> f1 = new CompletableFuture<>();
        CompletableFuture<String> f2 = new CompletableFuture<>();
        CompletableFuture<String> f3 = new CompletableFuture<>();
        CompletableFuture<String> result = CompletableFutures.firstOf(List.of(f1, f2, f3));
        f1.complete("success");
        assertTrue(result.isDone());
        assertFalse(result.isCompletedExceptionally());
        assertEquals("success", result.join());
    }

    @Test
    public void firstof_completes_if_any_futures_completes() {
        CompletableFuture<String> f1 = new CompletableFuture<>();
        CompletableFuture<String> f2 = new CompletableFuture<>();
        CompletableFuture<String> f3 = new CompletableFuture<>();
        CompletableFuture<String> result = CompletableFutures.firstOf(List.of(f1, f2, f3));
        f1.completeExceptionally(new Throwable("t1"));
        f2.completeExceptionally(new Throwable("t2"));
        f3.complete("success");
        assertTrue(result.isDone());
        assertFalse(result.isCompletedExceptionally());
        assertEquals("success", result.join());
    }

    @Test
    public void firstof_completes_exceptionally_when_all_futures_have_complete_exceptionally() {
        CompletableFuture<String> f1 = new CompletableFuture<>();
        CompletableFuture<String> f2 = new CompletableFuture<>();
        CompletableFuture<String> f3 = new CompletableFuture<>();
        CompletableFuture<String> result = CompletableFutures.firstOf(List.of(f1, f2, f3));
        f1.completeExceptionally(new Throwable("t1"));
        f2.completeExceptionally(new Throwable("t2"));
        f3.completeExceptionally(new Throwable("t3"));
        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
        try {
            result.join();
            fail("Exception expected");
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            assertEquals("t1", cause.getMessage());
            assertEquals(2, cause.getSuppressed().length);
        }
    }

}