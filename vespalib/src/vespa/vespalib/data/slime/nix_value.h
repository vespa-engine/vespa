// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value.h"

namespace vespalib::slime {

/**
 * Class representing a value containing absolutely nothing.
 **/
class NixValue : public Value
{
private:
    static NixValue _invalid;
    static NixValue _instance;

    NixValue() {}
public:
    static Value *invalid() { return &_invalid; }
    static Value *instance() { return &_instance; }
};

} // namespace vespalib::slime
