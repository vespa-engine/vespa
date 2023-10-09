// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search {
namespace fef {
namespace test {

//-----------------------------------------------------------------------------

// "ivalue(5)" calculates non-const 5.0
struct ImpureValueBlueprint : Blueprint {
    double value;
    ImpureValueBlueprint() : Blueprint("ivalue"), value(31212.0) {}
    void visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const override {}
    Blueprint::UP createInstance() const override { return Blueprint::UP(new ImpureValueBlueprint()); }
    bool setup(const IIndexEnvironment &, const std::vector<vespalib::string> &params) override;
    FeatureExecutor &createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const override;
};

//-----------------------------------------------------------------------------

// "docid" calculates local document id
struct DocidBlueprint : Blueprint {
    DocidBlueprint() : Blueprint("docid") {}
    void visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const override {}
    Blueprint::UP createInstance() const override { return Blueprint::UP(new DocidBlueprint()); }
    bool setup(const IIndexEnvironment &, const std::vector<vespalib::string> &) override;
    FeatureExecutor &createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const override;
};

//-----------------------------------------------------------------------------

// "box(ivalue(5))" calculates DoubleValue(5)
struct BoxingBlueprint : Blueprint {
    BoxingBlueprint() : Blueprint("box") {}
    void visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const override {}
    Blueprint::UP createInstance() const override { return Blueprint::UP(new BoxingBlueprint()); }
    bool setup(const IIndexEnvironment &, const std::vector<vespalib::string> &params) override;
    FeatureExecutor &createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const override;
};

//-----------------------------------------------------------------------------

// "track(docid)" calculates docid and counts execution as a side-effect
struct TrackingBlueprint : Blueprint {
    size_t &ext_cnt;
    TrackingBlueprint(size_t &ext_cnt_in) : Blueprint("track"), ext_cnt(ext_cnt_in) {}
    void visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const override {}
    Blueprint::UP createInstance() const override { return Blueprint::UP(new TrackingBlueprint(ext_cnt)); }
    bool setup(const IIndexEnvironment &, const std::vector<vespalib::string> &params) override;
    FeatureExecutor &createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const override;
};

//-----------------------------------------------------------------------------

} // namespace test
} // namespace fef
} // namespace search
