// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace storage {

struct ClusterContext {
protected:
    virtual ~ClusterContext() = default;
public:
    // returns a pointer to the cluster name
    // must be a valid pointer to a constant string for the
    // lifetime of all the components that may ask for it
    virtual const vespalib::string * cluster_name_ptr() const = 0;

    // convenience method
    const vespalib::string &cluster_name() const {
        return *cluster_name_ptr();
    }
};

struct SimpleClusterContext : ClusterContext {
    vespalib::string my_cluster_name;
    const vespalib::string * cluster_name_ptr() const override {
        return &my_cluster_name;
    }
    SimpleClusterContext() : my_cluster_name("") {}
    explicit SimpleClusterContext(const vespalib::string& value)
      : my_cluster_name(value)
    {}
    ~SimpleClusterContext() override = default;
};

} // namespace
