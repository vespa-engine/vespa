// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "diskthread.h"
#include "types.h"

namespace storage {

namespace framework {
    class Component;
    class Thread;
}

class PersistenceHandler;
class FileStorHandler;

class PersistenceThread final : public DiskThread, public Types
{
public:
    PersistenceThread(PersistenceHandler & handler, FileStorHandler & fileStorHandler,
                      uint32_t stripeId, framework::Component & component);
    ~PersistenceThread() override;

    /** Waits for current operation to be finished. */
    void flush() override;
    framework::Thread& getThread() override { return *_thread; }

private:
    PersistenceHandler                 & _persistenceHandler;
    FileStorHandler                    & _fileStorHandler;
    uint32_t                             _stripeId;
    std::unique_ptr<framework::Thread>   _thread;

    void run(framework::ThreadHandle&) override;
};

} // storage
