// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flushcontext.h"

#include <vespa/log/log.h>
LOG_SETUP(".proton.flushengine.flushcontext");

namespace proton {

FlushContext::FlushContext(
        const IFlushHandler::SP &handler,
        const IFlushTarget::SP &target,
        search::SerialNum lastSerial)
    : _name(createName(*handler, *target)),
      _handler(handler),
      _target(target),
      _task(),
      _lastSerial(lastSerial)
{ }

vespalib::string
FlushContext::createName(const IFlushHandler & handler, const IFlushTarget & target) {
    return (handler.getName() + "." + target.getName());
}

FlushContext::~FlushContext()
{
    if (_task) {
        LOG(warning, "Unexecuted flush task for '%s' destroyed.", _name.c_str());
    }
}

bool
FlushContext::initFlush(std::shared_ptr<search::IFlushToken> flush_token)
{
    LOG(debug, "Attempting to flush '%s'.", _name.c_str());
    _task = _target->initFlush(std::max(_handler->getCurrentSerialNumber(), _lastSerial), std::move(flush_token));
    if ( ! _task ) {
        LOG(debug, "Target refused to init flush.");
        return false;
    }
    return true;
}

}
