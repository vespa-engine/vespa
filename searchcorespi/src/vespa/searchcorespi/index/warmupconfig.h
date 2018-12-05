// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace searchcorespi::index {

/**
 * Keeps all config for controlling warmup.
 **/
class WarmupConfig {
public:
    WarmupConfig() : _duration(0.0), _unpack(false) { }
    WarmupConfig(double duration, bool unpack) : _duration(duration), _unpack(unpack) { }
    double getDuration() const { return _duration; }
    bool getUnpack() const { return _unpack; }
private:
    const double _duration;
    const bool   _unpack;
};

}
