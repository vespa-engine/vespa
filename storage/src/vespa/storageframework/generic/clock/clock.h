// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::Clock
 * \ingroup clock
 *
 * \brief Class used to attain current time.
 *
 * This class wraps how the time is retrieved. A common clock is useful in order
 * to let unit tests fake time. It is also useful to have one point for all
 * time calculations, such that one can possibly optimize if time retrieval
 * becomes a bottle neck.
 */

#pragma once

#include <vespa/vespalib/util/time.h>

namespace storage::framework {

struct Clock {
    virtual ~Clock() = default;

    // Time point resolution is intentionally not defined here.
    [[nodiscard]] virtual vespalib::steady_time getMonotonicTime() const = 0;
    [[nodiscard]] virtual vespalib::system_time getSystemTime() const = 0;
};

}
