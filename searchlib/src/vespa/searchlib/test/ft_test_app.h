// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ft_test_app_base.h"
#include <vespa/vespalib/testkit/testapp.h>

/*
 * Test application used by feature unit tests.
 */
struct FtTestApp : public vespalib::TestApp, public FtTestAppBase {
    ~FtTestApp() override;
};
