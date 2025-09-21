// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>

namespace vespalib {

/**
 * Gets a textual stack trace from the current frame of execution.
 * Potentially very expensive call, so only use in exceptional circumstances.
 *
 * @param ignoreTop number of frames to skip from the top of the stack
 * @return Stacktrace complete with resolved (although still mangled) symbols
 */
std::string getStackTrace(int ignoreTop);

/**
 * Gets a textual stack trace from an existing buffer of stack frames.
 * Potentially very expensive call, so only use in exceptional circumstances.
 *
 * @param ignoreTop number of frames to skip from the top of the stack
 * @param stack buffer of stack frame addresses
 * @param size number of valid frame addresses in the buffer
 * @return Stacktrace complete with resolved (although still mangled) symbols
 */
std::string getStackTrace(int ignoreTop, void* const* stack, int size);

/**
 * Get the stack frame addresses from the current frame of execution.
 * Lightweight call, as it does not involve any symbol resolving.
 *
 * @param framesOut buffer receiving up to maxFrames stack frame addresses
 * @param maxFrames maximum number of addresses to return (must be <=
 *     size of buffer)
 * @return number of frame addresses actually stored in framesOut
 */
int getStackTraceFrames(void** framesOut, int maxFrames);

/**
 * Returns true iff `signal_safe_collect_stack_frames()` is supported on
 * this platform.
 */
[[nodiscard]] bool has_signal_safe_collect_stack_frames() noexcept;

/**
 * Collects up to and including `frames_max` stack frames into `frames_out` in
 * an async signal safe way. The number of collected frames is returned.
 *
 * If `has_signal_safe_collect_stack_frames() == false`, the  function will
 * return 0 and `frames_out` is not modified.
 */
[[nodiscard]] size_t signal_safe_collect_stack_frames(void** frames_out, size_t frames_max);


}

