// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

#include "componentregisterimpl.h"
#include <vespa/storageframework/defaultimplementation/thread/threadpoolimpl.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>

namespace storage::framework::defaultimplementation {

class TestComponentRegister {
    ComponentRegisterImpl::UP _compReg;
    FakeClock                 _clock;
    ThreadPoolImpl            _threadPool;

public:
    explicit TestComponentRegister(ComponentRegisterImpl::UP compReg);
    virtual ~TestComponentRegister();

    virtual ComponentRegisterImpl& getComponentRegister() { return *_compReg; }
    FakeClock& getClock() { return _clock; }
    ThreadPoolImpl& getThreadPoolImpl() { return _threadPool; }
};

}
