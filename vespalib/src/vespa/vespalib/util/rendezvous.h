// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <type_traits>
#include <condition_variable>
#include <vector>
#include <atomic>

namespace vespalib {

/**
 * A place where threads meet up and exchange information. Each
 * participating thread calls the rendezvous function with an input
 * value. Execution will be blocked until enough threads are present,
 * at which point mingle will be called with input and output values
 * for all threads available at the same time. When mingle completes,
 * each thread resumes and returns the output value assigned to
 * it. This class implements all needed thread synchronization. The
 * subclass needs to implement the mingle function to supply the
 * application logic.
 **/
template <typename IN, typename OUT, bool external_id = false>
class Rendezvous
{
private:
    std::mutex              _lock;
    std::condition_variable _cond;
    size_t                  _size;
    size_t                  _next;
    size_t                  _gen;
    std::vector<IN *>       _in;
    std::vector<OUT *>      _out;
    std::atomic<bool>       _destroyed;

    /**
     * Function called to perform the actual inter-thread state
     * processing.
     **/
    virtual void mingle() = 0;

    /**
     * lock-free version for when there is only one thread meeting
     * itself.
     **/
    void meet_self(IN &input, OUT &output);

    /**
     * general version for when there are multiple threads meeting.
     **/
    void meet_others(IN &input, OUT &output, size_t my_id, std::unique_lock<std::mutex> guard);

protected:
    /**
     * Obtain an input parameter. This function is called by mingle.
     *
     * @return reference to the appropriate input
     * @param i the index of the requested input [0 .. size-1]
     **/
    IN &in(size_t i) const { return *_in[i]; }

    /**
     * Obtain the storage location of an output parameter. This
     * function is called by mingle.
     *
     * @return reference to the appropriate output
     * @param i the index of the requested output [0 .. size-1]
     **/
    OUT &out(size_t i) { return *_out[i]; }

public:
    /**
     * Create a Rendezvous with the given size. The size defines the
     * number of threads that need to call the rendezvous function to
     * trigger a mingle operation. The size of a Rendezvous must be at
     * least 1.
     *
     * @param n the size of this Rendezvous
     **/
    Rendezvous(size_t n);
    virtual ~Rendezvous();

    /**
     * Destroy this Rendezvous. This will cause any calls to the
     * rendezvous function to fail with an exception. This function
     * can be used to avoid barrier deadlocks caused by early thread
     * unwinding.
     **/
    void destroy();

    /**
     * @return number of participants
     **/
    size_t size() const { return _size; }

    /**
     * Called by individual threads to synchronize execution and share
     * state with the mingle function.
     *
     * @return output parameter for a single thread
     * @param input input parameter for a single thread
     **/
    OUT rendezvous(IN input) requires (!external_id);

    /**
     * Called by individual threads to synchronize execution and share
     * state with the mingle function where each caller has a
     * pre-defined participation id (enable by setting the external_id
     * template flag).
     *
     * @return output parameter for a single thread
     * @param input input parameter for a single thread
     * @param my_id participant id for this thread (must be in range and
     *              not conflicting with other threads)
     **/
    OUT rendezvous(IN input, size_t my_id) requires (external_id);
};

} // namespace vespalib

#include "rendezvous.hpp"
