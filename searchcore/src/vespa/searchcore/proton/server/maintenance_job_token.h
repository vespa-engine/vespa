// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace proton {

class MaintenanceJobTokenSource;

/*
 * A token used for blockable maintenance jobs that competes for shared resources.
 */
class MaintenanceJobToken {
    std::weak_ptr<MaintenanceJobTokenSource> _source;
public:
    MaintenanceJobToken(std::weak_ptr<MaintenanceJobTokenSource> source);
    ~MaintenanceJobToken();
};

}
