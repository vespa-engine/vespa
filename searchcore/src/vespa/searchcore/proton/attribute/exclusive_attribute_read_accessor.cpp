// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "exclusive_attribute_read_accessor.h"
#include <vespa/vespalib/util/gate.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>

namespace proton {

using search::AttributeVector;
using vespalib::ISequencedTaskExecutor;
using vespalib::Executor;
using vespalib::Gate;

using GateSP = std::shared_ptr<Gate>;

ExclusiveAttributeReadAccessor::Guard::Guard(const AttributeVector &attribute,
                                             const GateSP &exitGate)
    : _attribute(attribute),
      _exitGate(exitGate)
{
}

ExclusiveAttributeReadAccessor::Guard::~Guard()
{
    _exitGate->countDown();
}

ExclusiveAttributeReadAccessor::
ExclusiveAttributeReadAccessor(const AttributeVector::SP &attribute,
                               ISequencedTaskExecutor &attributeFieldWriter)
    : _attribute(attribute),
      _attributeFieldWriter(attributeFieldWriter)
{
}

namespace {

void
attributeWriteBlockingTask(AttributeVector::SP attribute, GateSP entranceGate, GateSP exitGate)
{
    attribute->commit(true);
    entranceGate->countDown();
    exitGate->await();
}

}

ExclusiveAttributeReadAccessor::Guard::UP
ExclusiveAttributeReadAccessor::takeGuard()
{
    GateSP entranceGate = std::make_shared<Gate>();
    GateSP exitGate = std::make_shared<Gate>();
    _attributeFieldWriter.execute(_attributeFieldWriter.getExecutorIdFromName(_attribute->getNamePrefix()),
                                  [this, entranceGate, exitGate]() { attributeWriteBlockingTask(_attribute, entranceGate, exitGate); });
    entranceGate->await();
    return std::make_unique<Guard>(*_attribute, exitGate);
}

} // namespace proton
