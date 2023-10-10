// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "taintable.h"

namespace vbench {

namespace {

struct Untaintable : Taintable {
    Taint taint;

    const Taint &tainted() const override { return taint; }
    ~Untaintable() {}
};

Untaintable untaintable;

} // namespace vbench::<unnamed>

const Taintable &
Taintable::nil()
{
    return untaintable;
}

} // namespace vbench
