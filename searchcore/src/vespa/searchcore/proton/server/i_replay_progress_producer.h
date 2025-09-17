// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

/**
 * An interface for getting the progress of an TLS replay.
 **/
class IReplayProgressProducer {
public:
    virtual ~IReplayProgressProducer() = default;
    virtual float getReplayProgress() const = 0;
};

}
