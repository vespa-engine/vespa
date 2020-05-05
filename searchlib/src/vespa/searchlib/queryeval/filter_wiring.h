// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <memory>

namespace search::queryeval {

class Blueprint;

struct FilterWiring {
    struct Info {
        virtual double compute_whitelist_ratio() const = 0;
        virtual double compute_blacklist_ratio() const = 0;
        virtual ~Info() {}
    };
    struct TargetInfo {
        Blueprint &target;
        std::shared_ptr<Info> filter_info;
        TargetInfo(Blueprint &target_in, std::shared_ptr<Info> info_in)
          : target(target_in), filter_info(std::move(info_in)) {}
    };
    std::vector<TargetInfo> targets;
    std::shared_ptr<Info> untargeted_info;
    FilterWiring();
    ~FilterWiring() {}
};

struct FilterInfoNop : FilterWiring::Info {
    double compute_whitelist_ratio() const override { return 1.0; }
    double compute_blacklist_ratio() const override { return 0.0; }
};

struct FilterInfoForceFilter : FilterWiring::Info {
    double compute_whitelist_ratio() const override { return 0.0; }
    double compute_blacklist_ratio() const override { return 1.0; }
};

FilterWiring::FilterWiring()
   : targets(),
     untargeted_info(std::make_shared<FilterInfoNop>())
{}

}
