// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "feedmessage.h"

namespace documentapi {

FeedMessage::FeedMessage() :
    DocumentMessage(),
    _name(),
    _generation(0),
    _increment(0)
{
    // empty
}

FeedMessage::FeedMessage(const string& name, int generation, int increment) :
    DocumentMessage(),
    _name(name),
    _generation(generation),
    _increment(increment)
{
    // empty
}

}
