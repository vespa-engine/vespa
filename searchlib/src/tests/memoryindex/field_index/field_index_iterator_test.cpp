// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/memoryindex/field_index.h>
#include <vespa/searchlib/memoryindex/posting_iterator.h>
#include <vespa/searchlib/test/memoryindex/wrap_inserter.h>
#include <vespa/searchlib/test/searchiteratorverifier.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("field_index_iterator_test");

using namespace search::fef;
using namespace search::index;
using namespace search::memoryindex::test;
using namespace search::memoryindex;

using search::index::schema::DataType;
using search::test::SearchIteratorVerifier;

class Verifier : public SearchIteratorVerifier {
private:
    mutable TermFieldMatchData _tfmd;
    FieldIndex _field_index;

public:
    Verifier(const Schema& schema)
        : _tfmd(),
          _field_index(schema, 0)
    {
        WrapInserter inserter(_field_index);
        inserter.word("a");
        for (uint32_t docId : getExpectedDocIds()) {
            inserter.add(docId);
        }
        inserter.flush();
    }
    ~Verifier() {}

    SearchIterator::UP create(bool strict) const override {
        (void) strict;
        TermFieldMatchDataArray match_data;
        match_data.add(&_tfmd);
        return std::make_unique<PostingIterator>(_field_index.find("a"),
                                                 _field_index.getFeatureStore(), 0, match_data);
    }
};

Schema
get_schema()
{
    Schema result;
    result.addIndexField(Schema::IndexField("f0", DataType::STRING));
    return result;
}

struct Fixture {
    Schema schema;
    Verifier verifier;
    Fixture()
        : schema(get_schema()),
          verifier(schema)
    {
    }
};

TEST_F("require that posting iterator conforms", Fixture)
{
    f.verifier.verify();
}

TEST_MAIN() { TEST_RUN_ALL(); }

