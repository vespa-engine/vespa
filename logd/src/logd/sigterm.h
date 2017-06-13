// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once
// setup for catching signals
extern void hook_signals();
extern bool gotSignaled();
extern int gotSignalNumber();
