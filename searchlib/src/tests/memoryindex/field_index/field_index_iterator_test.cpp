// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/memoryindex/field_index.h>
#include <vespa/searchlib/test/memoryindex/wrap_inserter.h>
#include <vespa/searchlib/test/searchiteratorverifier.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::fef;
using namespace search::index;
using namespace search::memoryindex::test;
using namespace search::memoryindex;

using search::index::schema::DataType;
using search::test::SearchIteratorVerifier;

template <typename FieldIndexType>
class Verifier : public SearchIteratorVerifier {
private:
    mutable TermFieldMatchData _tfmd;
    TermFieldMatchDataArray    _tfmda;
    FieldIndexType _field_index;

public:
    Verifier(const Schema& schema)
        : _tfmd(),
          _tfmda(),
          _field_index(schema, 0)
    {
        _tfmda.add(&_tfmd);
        WrapInserter inserter(_field_index);
        inserter.word("a");
        for (uint32_t docId : getExpectedDocIds()) {
            inserter.add(docId);
        }
        inserter.flush();
    }
    ~Verifier() override;

    SearchIterator::UP create(bool strict) const override {
        (void) strict;
        return _field_index.make_search_iterator("a", 0, _tfmda);
    }
    std::unique_ptr<SearchIterator> create(bool, const TermFieldMatchDataArray& tfmda) const override {
        return _field_index.make_search_iterator("a", 0, tfmda);
    }
    void verify_hidden_from_ranking() { SearchIteratorVerifier::verify_hidden_from_ranking(_tfmda); }
};

template <typename FieldIndexType>
Verifier<FieldIndexType>::~Verifier() = default;

Schema
get_schema()
{
    Schema result;
    result.addIndexField(Schema::IndexField("f0", DataType::STRING));
    return result;
}

template <typename FieldIndexType>
struct Fixture {
    Schema schema;
    Verifier<FieldIndexType> verifier;
    Fixture()
        : schema(get_schema()),
          verifier(schema)
    {
    }
};

TEST(FieldIndexIteratorTest, require_that_normal_posting_iterator_conforms)
{
    Fixture<FieldIndex<false>> f;
    f.verifier.verify();
    f.verifier.verify_hidden_from_ranking();
}

TEST(FieldIndexIteratorTest, require_that_interleaved_posting_iterator_conforms)
{
    Fixture<FieldIndex<true>> f;
    f.verifier.verify();
    f.verifier.verify_hidden_from_ranking();
}

GTEST_MAIN_RUN_ALL_TESTS()
