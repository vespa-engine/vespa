// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "visitormessagesession.h"
#include <vespa/documentapi/messagebus/priority.h>

namespace storage {

class Visitor;
class VisitorThread;

struct VisitorMessageSessionFactory {
    typedef std::unique_ptr<VisitorMessageSessionFactory> UP;

    virtual ~VisitorMessageSessionFactory() = default;

    virtual VisitorMessageSession::UP createSession(Visitor&, VisitorThread&) = 0;

    virtual documentapi::Priority::Value toDocumentPriority(uint8_t storagePriority) const = 0;
};

} // storage

