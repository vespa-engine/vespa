// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executeinfo.h"

using vespalib::Doom;
namespace search::queryeval {

const ExecuteInfo ExecuteInfo::FULL(1.0, Doom::never(), vespalib::ThreadBundle::trivial());

ExecuteInfo::ExecuteInfo() noexcept
    : ExecuteInfo(1.0, Doom::never(), vespalib::ThreadBundle::trivial())
{ }

ExecuteInfo
ExecuteInfo::createForTest(double hitRate) noexcept {
    return createForTest(hitRate, Doom::never());
}

}
