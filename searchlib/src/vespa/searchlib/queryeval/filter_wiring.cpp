// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "filter_wiring.h"


namespace search::queryeval {

FilterWiring::Info::~Info() = default;

FilterWiring::FilterWiring()
   : targets(),
     untargeted_info(std::make_shared<FilterInfoNop>())
{}

FilterWiring::~FilterWiring() = default;

}
