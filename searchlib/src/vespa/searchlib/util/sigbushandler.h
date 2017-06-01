// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <csetjmp>
#include <csignal>

namespace search {

class StateFile;

/*
 * Class used to handle SIGBUS signals, which are generated on IO errors
 * on backing file for a memory map.
 */
class SigBusHandler
{
    static SigBusHandler *_instance;
    StateFile *_stateFile;
    sigjmp_buf *_unwind;
    bool _trapped;
    bool _fired;
    char _buf[2048];

    void trap();
    void untrap();
    static void forward(int sig, siginfo_t *si, void *ucv);
    void handle(int sig, siginfo_t *si, void *ucv);
public:
    SigBusHandler(StateFile *stateFile);
    ~SigBusHandler();

    bool fired() const { return _fired; }

    /*
     * Setup siglongjmp based unwinding, used by unit tests.
     */
    void setUnwind(sigjmp_buf *unwind) { _unwind = unwind; }
};

}
