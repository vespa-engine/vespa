// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

class DummyObj {
public:
    DummyObj();
    ~DummyObj();
};

class DummyLock {
public:
    void Lock();
    void Unlock();
};
