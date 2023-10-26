// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "gid_to_lid_change_registrator.h"
#include "i_gid_to_lid_change_listener.h"
#include <cassert>

namespace proton {

GidToLidChangeRegistrator::GidToLidChangeRegistrator(std::shared_ptr<IGidToLidChangeHandler> handler,
                                                     const vespalib::string &docTypeName)
    : _handler(std::move(handler)),
      _docTypeName(docTypeName),
      _keepNames()
{
}

GidToLidChangeRegistrator::~GidToLidChangeRegistrator()
{
    _handler->removeListeners(_docTypeName, _keepNames);
}

void
GidToLidChangeRegistrator::addListener(std::unique_ptr<IGidToLidChangeListener> listener)
{
    assert(listener->getDocTypeName() == _docTypeName);
    _keepNames.insert(listener->getName());
    _handler->addListener(std::move(listener));
}

} // namespace proton
