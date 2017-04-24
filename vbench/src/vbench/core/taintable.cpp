// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "taintable.h"

namespace vbench {

namespace {

struct Untaintable : Taintable {
    Taint taint;
    virtual const Taint &tainted() const override { return taint; }
    virtual ~Untaintable() {}
};

Untaintable untaintable;

} // namespace vbench::<unnamed>

const Taintable &
Taintable::nil()
{
    return untaintable;
}

} // namespace vbench
