// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace searchcorespi { class IIndexManagerFactory; }

namespace proton {

/**
 * Interface defining the communication needed with the owner of the
 * document sub db.
 */
class IDocumentSubDBOwner
{
public:
    virtual ~IDocumentSubDBOwner() {}
    virtual void syncFeedView() = 0;
    virtual std::shared_ptr<searchcorespi::IIndexManagerFactory>
    getIndexManagerFactory(const vespalib::stringref &name) const = 0;
    virtual vespalib::string getName() const = 0;
    virtual uint32_t getDistributionKey() const = 0;
};

} // namespace proton
