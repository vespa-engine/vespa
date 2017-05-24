// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <atomic>

namespace search
{

/*
 * Class used to store application state in a mostly safe manner.
 *
 * It maintaines two files, one file with zero-padding at end to store
 * last state, and another file with history of states.
 *
 * State files can not be shared between processes, file locking is not
 * async signal safe.
 *
 * Standalone implementation (doesn't use fastos or vespalib) to
 * ensure that we don't trigger callback hooks in fastos.
 *
 */
class StateFile
{
    char *_name;
    char *_historyName;
    std::atomic<int>   _gen;

    /*
     * Zero pad file, to ensure that a later write won't run out of space.
     */
    void
    zeroPad();

    /*
     * Read state file to buffer in raw form, including padding.
     */
    void
    readRawState(std::vector<char> &buf);

    /*
     * Trim padding and everything after state (i.e. stop at first newline).
     */
    static void
    trimState(std::vector<char> &buf);

    /*
     * Trim partial state from end of history.
     */
    static void
    trimHistory(std::vector<char> &history, const char *historyName, int hfd,
                std::vector<char> &lastHistoryState);

    /*
     * Fixup history: trim partial state from end and append current state
     * in state file to history if different from last state in history.
     * If main state file doesn't have a state but history has a state then
     * restore main state from history.
     */
    void
    fixupHistory();

    /*
     * Check that state doesn't contain nul bytes or early newline and
     * that it is terminated by a newline at end.
     */
    void
    checkState(const char *buf, size_t bufLen) noexcept;

    void
    internalAddSignalState(const char *buf, size_t bufLen,
                           const char *name,
                           int appendFlag,
                           const char *openerr,
                           const char *writeerr,
                           const char *fsyncerr,
                           const char *closeerr) noexcept;

    void
    addSignalState(const char *buf, size_t bufLen) noexcept;
public:
    StateFile(const std::string &name);

    ~StateFile();

    void
    addState(const char *buf, size_t bufLen, bool signal);

    static void
    erase(const std::string &name);

    /*
     * Read state file to buffer and trim it down to a state.
     */
    void
    readState(std::vector<char> &buf);

    /*
     * Get current state generation (bumped whenever new state is written).
     */
    int
    getGen() const;
};

}
