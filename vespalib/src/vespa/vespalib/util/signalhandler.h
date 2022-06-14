// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <csignal>
#include <vector>
#include <atomic>
#include <pthread.h>

namespace vespalib {

/**
 * @brief This class helps you perform simple signal handling.
 *
 * A normal application will typically want to ignore SIGPIPE and
 * perform a (clean) exit on SIGINT/SIGTERM. Caught signals are
 * handled by setting a flag indicating that the signal has been
 * caught. The application itself is responsible for checking which
 * signals have been caught. Different signals are handled by
 * combining a single signal dispatch function with a pattern similar
 * to type-safe enums. All available signal handlers are created
 * during static initialization at program startup.
 **/
class SignalHandler
{
private:
    /**
     * Data structure keeping track of all registered signal handlers.
     **/
    static std::vector<SignalHandler*> _handlers;

    /**
     * The signal handled by this signal handler.
     **/
    int _signal;

    /**
     * State indicating if the signal handled by this signal handler
     * has been caught.
     **/
    std::atomic<int> _gotSignal;

    /**
     * Common signal handler for all caught signals. This method will
     * dispatch the signal to the appropriate static signal handler
     * instance.
     *
     * @param signal the caught signal
     **/
    static void handleSignal(int signal) noexcept;

    /**
     * This method is invoked by the common signal handling method to
     * dispatch the signal to the right static signal handler
     * instance.
     *
     * noinline to ensure consistent number of frames to skip for back-tracing.
     **/
    void gotSignal() noexcept __attribute__((noinline));


    /**
     * Internal async signal-safe function used to dump frame addresses of a signal-interrupted
     * thread to a shared buffer that will be read by the signalling thread.
     *
     * noinline to ensure consistent number of frames to skip for back-tracing.
     */
    static void dump_current_thread_stack_to_shared_state() noexcept __attribute__((noinline));

    /**
     * Create a signal handler for the given signal. This method is
     * private as all signal handlers should be created during
     * initialization.
     **/
    explicit SignalHandler(int signal);

    SignalHandler(const SignalHandler &) = delete;
    SignalHandler &operator=(const SignalHandler &) = delete;

    static SignalHandler USR2;

public:
    static SignalHandler HUP;
    static SignalHandler INT;
    static SignalHandler TERM;
    static SignalHandler CHLD;
    static SignalHandler PIPE;
    static SignalHandler SEGV;
    static SignalHandler ABRT;
    static SignalHandler BUS;
    static SignalHandler ILL;
    static SignalHandler TRAP;
    static SignalHandler FPE;
    static SignalHandler QUIT;
    static SignalHandler USR1;

    /**
     * Start catching this signal
     **/
    void hook();

    /**
     * Ignore this signal
     **/
    void ignore();

    /**
     * Check if this signal has been caught
     *
     * @return true if this signal has been caught
     **/
    bool check() const;

    /**
     * Clear the state indicating if this signal has been caught
     **/
    void clear();

    /**
     * Stop catching this signal
     **/
    void unhook();

    /**
     * Hook in signal handler for cross-thread stack tracing.
     *
     * Must be called at application init time if cross-thread tracing is wanted.
     */
    static void enable_cross_thread_stack_tracing();

    /**
     * Get the stack trace of the current point of execution of the thread referenced
     * by `thread_id`. This may be the same ID as the calling thread, in which case
     * the current call stack is returned. The pthread_t ID must be valid; invoking
     * this function without a valid ID is undefined behavior.
     *
     * Returned format is the same as that of vespalib::getStackTrace().
     *
     * Requires enable_cross_thread_stack_tracing() to have been called prior to creating
     * the thread referenced by `thread_id`.
     *
     * Due to potentially heavy internal synchronization overhead, this is not a function
     * that should be used in any kind of hot code path. Intended for debugging purposes.
     */
    static string get_cross_thread_stack_trace(pthread_t thread_id);

    static void shutdown();
};

} // vespalib

