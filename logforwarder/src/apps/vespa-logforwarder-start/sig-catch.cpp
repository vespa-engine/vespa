// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sig-catch.h"
#include <csignal>
#include <unistd.h>
#include <string.h>

static int sigPermanent(int sig, void(*handler)(int));
static void setStopFlag(int sig);
sig_atomic_t stop = 0;

SigCatch::SigCatch()
{
    sigPermanent(SIGTERM, setStopFlag);
    sigPermanent(SIGINT, setStopFlag);
}

bool
SigCatch::receivedStopSignal() {
    return stop != 0;
}

static void
setStopFlag(int sig)
{
    (void)sig;
    stop = 1;
}

static int
sigPermanent(int sig, void(*handler)(int))
{
    struct sigaction sa;

    memset(&sa, 0, sizeof(sa));
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = 0; // no SA_RESTART!
    sa.sa_handler = handler;
    return sigaction(sig, &sa, nullptr);
}
