// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::TestComponentRegister
 * \ingroup component
 *
 * \brief Simple instance to use for testing.
 *
 * For testing we just want to set up a simple component register with the basic
 * services that tests need, and that all tests need the same instance of.
 *
 * This instance should be the same for all using it. So don't add set functions
 * that can possibly alter it while running.
 */
#pragma once

#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <vespa/storageframework/defaultimplementation/component/componentregisterimpl.h>
#include <vespa/storageframework/defaultimplementation/thread/threadpoolimpl.h>
#include <vespa/storageframework/defaultimplementation/memory/nomemorymanager.h>

namespace storage {
namespace framework {
namespace defaultimplementation {

class TestComponentRegister {
    ComponentRegisterImpl::UP _compReg;
    FakeClock _clock;
    ThreadPoolImpl _threadPool;
    NoMemoryManager _memoryManager;

public:
    TestComponentRegister(ComponentRegisterImpl::UP compReg)
        : _compReg(std::move(compReg)),
          _clock(),
          _threadPool(_clock),
          _memoryManager()
    {
        assert(_compReg.get() != 0);
            // Set a memory manager, so users can register memory types and
            // ask for memory.
        _compReg->setMemoryManager(_memoryManager);
            // Set a fake clock, giving test control of clock
        _compReg->setClock(_clock);
            // Set a thread pool so components can make threads in tests.
        _compReg->setThreadPool(_threadPool);
            // Metric manager should not be needed. Tests of metric system can
            // be done without using this class. Components can still register
            // metrics without a manager.

            // Status page server should not be needed. Tests of status parts
            // can be done without using this class. Components can still
            // register status pages without a server
    }

    ComponentRegisterImpl& getComponentRegister() { return *_compReg; }
    FakeClock& getClock() { return _clock; }
    ThreadPoolImpl& getThreadPoolImpl() { return _threadPool; }
    FastOS_ThreadPool& getThreadPool() { return _threadPool.getThreadPool(); }
    NoMemoryManager& getMemoryManager() { return _memoryManager; }
};

} // defaultimplementation
} // framework
} // storage

