// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace storage {

/**
 * Cluster ontext common to all storage components.
 * For now just the cluster name, but we can consider
 * moving other global context into this API.
 **/
struct ClusterContext {
protected:
    virtual ~ClusterContext() = default;
public:
    // Returns a pointer to the cluster name.
    // Must be a valid pointer to a constant string for the
    // lifetime of all the components that may ask for it.
    // This API is for the benefit of StorageMessageAddress
    // which wants to contain the pointer returned here.
    virtual const vespalib::string * cluster_name_ptr() const noexcept = 0;

    // convenience method
    const vespalib::string &cluster_name() const noexcept {
        return *cluster_name_ptr();
    }
};

/**
 * Simple ClusterContext with an exposed string.
 **/
struct SimpleClusterContext : ClusterContext {
    vespalib::string my_cluster_name;
    const vespalib::string * cluster_name_ptr() const noexcept override {
        return &my_cluster_name;
    }
    SimpleClusterContext() : my_cluster_name("") {}
    explicit SimpleClusterContext(const vespalib::string& value)
      : my_cluster_name(value)
    {}
    ~SimpleClusterContext() override = default;
};

} // namespace
