// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("attribute_populator_test");
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchcore/proton/attribute/attributemanager.h>
#include <vespa/searchcore/proton/attribute/attribute_populator.h>
#include <vespa/searchcore/proton/test/test.h>
#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchlib/common/foregroundtaskexecutor.h>

using namespace document;
using namespace proton;
using namespace search;
using namespace search::index;

typedef search::attribute::Config AVConfig;
typedef search::attribute::BasicType AVBasicType;

const vespalib::string TEST_DIR = "testdir";
const uint64_t CREATE_SERIAL_NUM = 8u;

Schema
createSchema()
{
    Schema schema;
    schema.addAttributeField(Schema::AttributeField("a1", Schema::DataType::INT32));
    return schema;
}

struct DocContext
{
    Schema _schema;
    DocBuilder _builder;
    DocContext()
        : _schema(createSchema()),
          _builder(_schema)
    {
    }
    Document::UP create(uint32_t id, int64_t fieldValue) {
        vespalib::string docId =
                vespalib::make_string("id:searchdocument:searchdocument::%u", id);
        return _builder.startDocument(docId).
                startAttributeField("a1").addInt(fieldValue).endField().
                endDocument();
    }
};

struct Fixture
{
    test::DirectoryHandler _testDir;
    DummyFileHeaderContext _fileHeader;
    ForegroundTaskExecutor _attributeFieldWriter;
    HwInfo                 _hwInfo;
    AttributeManager::SP _mgr;
    std::unique_ptr<AttributePopulator> _pop;
    DocContext _ctx;
    Fixture()
        : _testDir(TEST_DIR),
          _fileHeader(),
          _attributeFieldWriter(),
          _hwInfo(),
          _mgr(new AttributeManager(TEST_DIR, "test.subdb",
                  TuneFileAttributes(),
                                    _fileHeader, _attributeFieldWriter, _hwInfo)),
          _pop(),
          _ctx()
    {
        _mgr->addAttribute({ "a1", AVConfig(AVBasicType::INT32)},
                CREATE_SERIAL_NUM);
        _pop = std::make_unique<AttributePopulator>(_mgr, 1, "test", CREATE_SERIAL_NUM);
    }
    AttributeGuard::UP getAttr() {
        return _mgr->getAttribute("a1");
    }
};

TEST_F("require that reprocess with document populates attribute", Fixture)
{
    AttributeGuard::UP attr = f.getAttr();
    EXPECT_EQUAL(1u, attr->get()->getNumDocs());

    f._pop->handleExisting(5, *f._ctx.create(0, 33));
    EXPECT_EQUAL(6u, attr->get()->getNumDocs());
    EXPECT_EQUAL(33, attr->get()->getInt(5));
    EXPECT_EQUAL(1u, attr->get()->getStatus().getLastSyncToken());

    f._pop->handleExisting(6, *f._ctx.create(1, 44));
    EXPECT_EQUAL(7u, attr->get()->getNumDocs());
    EXPECT_EQUAL(44, attr->get()->getInt(6));
    EXPECT_EQUAL(2u, attr->get()->getStatus().getLastSyncToken());
    f._pop->done();
    EXPECT_EQUAL(CREATE_SERIAL_NUM, attr->get()->getStatus().getLastSyncToken());
}

TEST_MAIN()
{
    vespalib::rmdir(TEST_DIR, true);
    TEST_RUN_ALL();
}
