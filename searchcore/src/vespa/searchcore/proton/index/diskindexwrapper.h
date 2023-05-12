// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/index/idiskindex.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchlib/diskindex/diskindex.h>
#include <vespa/searchlib/queryeval/blueprint.h>

namespace proton {

class DiskIndexWrapper : public searchcorespi::index::IDiskIndex {
private:
    search::diskindex::DiskIndex _index;
    search::SerialNum _serialNum;

public:
    DiskIndexWrapper(const vespalib::string &indexDir,
                     const search::TuneFileSearch &tuneFileSearch,
                     size_t cacheSize);

    DiskIndexWrapper(const DiskIndexWrapper &oldIndex,
                     const search::TuneFileSearch &tuneFileSearch,
                     size_t cacheSize);

    std::unique_ptr<search::queryeval::Blueprint>
    createBlueprint(const IRequestContext & requestContext, const FieldSpec &field, const Node &term) override {
        return _index.createBlueprint(requestContext, field, term);
    }
    std::unique_ptr<search::queryeval::Blueprint>
    createBlueprint(const IRequestContext & requestContext, const FieldSpecList &fields, const Node &term) override {
        return _index.createBlueprint(requestContext, fields, term);
    }
    search::SearchableStats getSearchableStats() const override {
        return search::SearchableStats().sizeOnDisk(_index.getSize());
    }

    search::SerialNum getSerialNum() const override;

    void accept(searchcorespi::IndexSearchableVisitor &visitor) const override;
    search::index::FieldLengthInfo get_field_length_info(const vespalib::string& field_name) const override;
    const vespalib::string &getIndexDir() const override { return _index.getIndexDir(); }
    const search::index::Schema &getSchema() const override { return _index.getSchema(); }
};

}  // namespace proton

