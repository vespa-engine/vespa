// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disk_index_builder.h"
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/diskindex/diskindex.h>
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <filesystem>

using search::diskindex::DiskIndex;
using search::index::DocIdAndPosOccFeatures;
using search::index::Schema;

namespace search::queryeval::test {

constexpr search::queryeval::Source default_source = 0;

DiskIndexBuilder::DiskIndexBuilder(const Schema& schema, vespalib::stringref index_dir, uint32_t docid_limit, uint64_t num_words)
    : _schema(schema),
      _field_length_inspector(),
      _tune_file_indexing(),
      _tune_file_attributes(),
      _tune_file_search(),
      _file_header_ctx(),
      _index_dir(index_dir),
      _selector(default_source, _index_dir + "/selector", docid_limit),
      _builder(_schema, index_dir, docid_limit, num_words, _field_length_inspector, _tune_file_indexing, _file_header_ctx),
      _field_builder(_builder.startField(0))
{
    // Mark all documents as being part of this disk index.
    for (uint32_t docid = 0; docid < docid_limit; ++docid) {
        _selector.setSource(docid, default_source);
    }
}

void
DiskIndexBuilder::add_word(vespalib::stringref word, search::BitVector& docids, uint32_t num_occs)
{
    DocIdAndPosOccFeatures diaf;
    diaf.word_positions().reserve(num_occs);
    for (uint32_t word_pos = 0; word_pos < num_occs; ++word_pos) {
        diaf.addNextOcc(0, word_pos, 1, num_occs * 10);
    }
    diaf.set_field_length(num_occs * 10);
    diaf.set_num_occs(num_occs);
    _field_builder->startWord(word);
    docids.foreach_truebit([&](uint32_t docid) {
        diaf.set_doc_id(docid);
        _field_builder->add_document(diaf);
    });
    _field_builder->endWord();
}

namespace {

class DiskIndexSearchable : public BenchmarkSearchable {
private:
    std::unique_ptr<DiskIndex> _index;

public:
    DiskIndexSearchable(std::unique_ptr<DiskIndex> index) : _index(std::move(index)) {}
    ~DiskIndexSearchable() {
        vespalib::string index_dir = _index->getIndexDir();
        _index.reset();
        std::filesystem::remove_all(std::filesystem::path(index_dir));
    }
    std::unique_ptr<Blueprint> create_blueprint(const FieldSpec& field,
                                                const search::query::Node& term) override {
        FakeRequestContext req_ctx;
        return _index->createBlueprint(req_ctx, field, term);
    }
};

}

std::unique_ptr<BenchmarkSearchable>
DiskIndexBuilder::build()
{
    _field_builder.reset();
    _selector.extractSaveInfo(_index_dir + "/selector")->save(_tune_file_attributes, _file_header_ctx);
    auto index = std::make_unique<DiskIndex>(_index_dir);
    bool setup = index->setup(_tune_file_search);
    assert(setup);
    return std::make_unique<DiskIndexSearchable>(std::move(index));
}

}
