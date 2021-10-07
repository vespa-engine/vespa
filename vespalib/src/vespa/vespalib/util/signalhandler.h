// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <csignal>
#include <vector>

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
    volatile sig_atomic_t _gotSignal;

    /**
     * Common signal handler for all caught signals. This method will
     * dispatch the signal to the appropriate static signal handler
     * instance.
     *
     * @param signal the caught signal
     **/
    static void handleSignal(int signal);

    /**
     * This method is invoked by the common signal handling method to
     * dispatch the signal to the right static signal handler
     * instance.
     **/
    void gotSignal();

    /**
     * Create a signal handler for the given signal. This method is
     * private as all signal handlers should be created during
     * initialization.
     **/
    SignalHandler(int signal);

    SignalHandler(const SignalHandler &) = delete;
    SignalHandler &operator=(const SignalHandler &) = delete;

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

    static void shutdown();
};

} // vespalib

