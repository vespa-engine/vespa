// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fn_table.h"
#include <algorithm>
#include <format>

namespace vespalib::hwaccelerated::dispatch {

FnTable::FnTable() = default;

FnTable::FnTable(const TargetInfo& prefilled_target_info) {
    std::ranges::fill(fn_target_infos, prefilled_target_info);
}

FnTable::~FnTable() = default;

FnTable::FnTable(const FnTable&) = default;
FnTable& FnTable::operator=(const FnTable&) = default;

#define VESPA_HWACCEL_RETURN_FALSE_IF_FN_PTR_NULL(fn_type, fn_field, fn_id) \
    if ((fn_field) == nullptr) return false;

bool FnTable::is_complete() const noexcept {
    VESPA_HWACCEL_VISIT_FN_TABLE(VESPA_HWACCEL_RETURN_FALSE_IF_FN_PTR_NULL);
    return true;
}

#define VESPA_HWACCEL_STRINGIFY_FN_ENTRY(fn_type, fn_field, fn_id) \
    fns += std::format("{} => {}\n", #fn_field, fn_target_info(fn_id).to_string());

std::string FnTable::to_string() const {
    std::string fns;
    VESPA_HWACCEL_VISIT_FN_TABLE(VESPA_HWACCEL_STRINGIFY_FN_ENTRY);
    return fns;
}

} // vespalib::hwaccelerated::dispatch
