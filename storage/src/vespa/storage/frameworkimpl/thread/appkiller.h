// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::AppKiller
 * @ingroup thread
 * @brief A class for killing a storage process
 *
 * The app killer is a utility used by the deadlock detector to kill the
 * process. This is separated into this utility such that the deadlock
 * detector itself can use a fake killer to test the functionality.
 */

#pragma once

#include <memory>

namespace storage {

struct AppKiller {
    typedef std::unique_ptr<AppKiller> UP;
    virtual ~AppKiller() {}
    virtual void kill() = 0;
};

struct RealAppKiller : public AppKiller {
    void kill() override;
};

} // storage

