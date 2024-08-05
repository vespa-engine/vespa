// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "benchmark_searchable.h"
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/attribute/fixedsourceselector.h>
#include <vespa/searchlib/diskindex/indexbuilder.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/test/index/mock_field_length_inspector.h>
#include <memory>

namespace search { class BitVector; }
namespace search::index { class DocIdAndFeatures; }
namespace search::diskindex { class DiskIndex; }

namespace search::queryeval::test {

/**
 * Class used to build a disk index, used for benchmarking.
 */
class DiskIndexBuilder {
private:
    search::index::Schema _schema;
    search::index::test::MockFieldLengthInspector _field_length_inspector;
    TuneFileIndexing _tune_file_indexing;
    TuneFileAttributes _tune_file_attributes;
    TuneFileSearch _tune_file_search;
    search::index::DummyFileHeaderContext _file_header_ctx;
    vespalib::string _index_dir;
    search::FixedSourceSelector _selector;
    search::diskindex::IndexBuilder _builder;
    std::unique_ptr<search::index::FieldIndexBuilder> _field_builder;

public:
    DiskIndexBuilder(const search::index::Schema& schema, std::string_view index_dir, uint32_t docid_limit, uint64_t num_words);
    void add_word(std::string_view word, search::BitVector& docids, uint32_t num_occs);
    std::unique_ptr<BenchmarkSearchable> build();
};

}
