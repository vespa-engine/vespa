// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/time.h>
#include <memory>

namespace proton {

class DocumentSubDBReconfig;

/**
 * Class representing the result of the prepare step of a DocumentDB reconfig.
 *
 * The reconfig is performed in three steps:
 * Prepare:
 * Based on the config that is changed, new components are instantiated in each subdb.
 * This can be costly and is handled by helper threads from the shared executor pool.
 *
 * Complete prepare:
 * Docid limit and serial number are used to complete prepared reconfig.
 *
 * Apply:
 * The new components are swapped with the old ones in the DocumentDB master write thread.
 */
class DocumentDBReconfig {
private:
    vespalib::steady_time _start_time;
    std::unique_ptr<DocumentSubDBReconfig> _ready_reconfig;
    std::unique_ptr<DocumentSubDBReconfig> _not_ready_reconfig;

public:
    DocumentDBReconfig(vespalib::steady_time start_time,
                       std::unique_ptr<DocumentSubDBReconfig> ready_reconfig_in,
                       std::unique_ptr<DocumentSubDBReconfig> not_ready_reconfig_in);
    ~DocumentDBReconfig();

    vespalib::steady_time start_time() const noexcept { return _start_time; }
    const DocumentSubDBReconfig& ready_reconfig() const noexcept { return *_ready_reconfig; }
    DocumentSubDBReconfig& ready_reconfig() noexcept { return *_ready_reconfig; }
    const DocumentSubDBReconfig& not_ready_reconfig() const noexcept { return *_not_ready_reconfig; }
    DocumentSubDBReconfig& not_ready_reconfig() noexcept { return *_not_ready_reconfig; }
};

}

