// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "testcomponentregister.h"
#include <cassert>

namespace storage::framework::defaultimplementation {

TestComponentRegister::TestComponentRegister(ComponentRegisterImpl::UP compReg)
    : _compReg(std::move(compReg)),
      _clock(),
      _threadPool(_clock)
{
    assert(_compReg.get() != 0);
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

TestComponentRegister::~TestComponentRegister() = default;

}
