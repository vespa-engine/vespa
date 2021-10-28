// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/memoryindex/field_index.h>
#include <vespa/searchlib/test/memoryindex/wrap_inserter.h>
#include <vespa/searchlib/test/searchiteratorverifier.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("field_index_iterator_test");

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
    FieldIndexType _field_index;

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
        return _field_index.make_search_iterator("a", 0, match_data);
    }
};

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

TEST_F("require that normal posting iterator conforms", Fixture<FieldIndex<false>>)
{
    f.verifier.verify();
}

TEST_F("require that interleaved posting iterator conforms", Fixture<FieldIndex<true>>)
{
    f.verifier.verify();
}

TEST_MAIN() { TEST_RUN_ALL(); }

