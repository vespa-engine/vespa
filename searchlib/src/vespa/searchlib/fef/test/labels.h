// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/stllike/asciistream.h>

#include <vector>

namespace search::fef::test {

struct Labels {
    virtual void inject(Properties& p) const = 0;
    virtual ~Labels() = default;
};
struct NoLabel : public Labels {
    void inject(Properties&) const override {}
    ~NoLabel() override;
};
struct SingleLabel : public Labels {
    std::string label;
    uint32_t    uid;
    SingleLabel(const std::string& l, uint32_t x) : label(l), uid(x) {}
    void inject(Properties& p) const override {
        vespalib::asciistream key;
        key << "vespa.label." << label << ".id";
        vespalib::asciistream value;
        value << uid;
        p.add(key.view(), value.view());
    }
};
struct MultiLabel : public Labels {
    std::string           label;
    std::vector<uint32_t> uids;
    MultiLabel(const std::string& l, std::vector<uint32_t> x) : label(l), uids(std::move(x)) {}
    ~MultiLabel() override;
    void inject(Properties& p) const override {
        vespalib::asciistream key;
        key << "vespa.label." << label << ".id";
        for (uint32_t uid : uids) {
            vespalib::asciistream value;
            value << uid;
            p.add(key.view(), value.view());
        }
    }
};

} // namespace search::fef::test
