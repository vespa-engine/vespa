// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/time.h>

namespace searchcorespi::index {

/**
 * Keeps all config for controlling warmup.
 **/
class WarmupConfig {
public:
    WarmupConfig() : _duration(vespalib::duration::zero()), _unpack(false) { }
    WarmupConfig(vespalib::duration duration, bool unpack) : _duration(duration), _unpack(unpack) { }
    vespalib::duration getDuration() const { return _duration; }
    bool getUnpack() const { return _unpack; }
private:
    const vespalib::duration _duration;
    const bool               _unpack;
};

}
