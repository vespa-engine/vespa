// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

namespace proton {

class DocumentSubDBReconfig;

/**
 * Class representing the result of the prepare step of a DocumentDB reconfig.
 *
 * The reconfig is performed in two steps:
 * Prepare:
 * Based on the config that is changed, new components are instantiated in each subdb.
 * This can be costly and is handled by helper threads from the shared executor pool.
 *
 * Apply:
 * The new components are swapped with the old ones in the DocumentDB master write thread.
 */
class DocumentDBReconfig {
private:
    std::unique_ptr<const DocumentSubDBReconfig> _ready_reconfig;
    std::unique_ptr<const DocumentSubDBReconfig> _not_ready_reconfig;

public:
    DocumentDBReconfig(std::unique_ptr<const DocumentSubDBReconfig> ready_reconfig_in,
                       std::unique_ptr<const DocumentSubDBReconfig> not_ready_reconfig_in);
    ~DocumentDBReconfig();

    const DocumentSubDBReconfig& ready_reconfig() const { return *_ready_reconfig; }
    const DocumentSubDBReconfig& not_ready_reconfig() const { return *_not_ready_reconfig; }
};

}

