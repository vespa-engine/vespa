// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/attribute/attribute_populator.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/searchcore/proton/attribute/attributemanager.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/proton/test/test.h>
#include <vespa/searchlib/attribute/interlock.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/foreground_thread_executor.h>
#include <vespa/vespalib/util/foregroundtaskexecutor.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_populator_test");

using document::config_builder::DocumenttypesConfigBuilderHelper;
using document::config_builder::Struct;
using vespalib::ForegroundTaskExecutor;
using vespalib::ForegroundThreadExecutor;
using namespace document;
using namespace proton;
using namespace search;
using namespace search::index;

using search::test::DirectoryHandler;

using AVBasicType = search::attribute::BasicType;
using AVConfig = search::attribute::Config;

const vespalib::string TEST_DIR = "testdir";
const uint64_t CREATE_SERIAL_NUM = 8u;

std::unique_ptr<const DocumentTypeRepo>
makeDocTypeRepo()
{
    DocumenttypesConfigBuilderHelper builder;
    builder.document(-645763131, "searchdocument",
                     Struct("searchdocument.header"),
                     Struct("searchdocument.body").
                     addField("a1", DataType::T_INT));
    return std::make_unique<DocumentTypeRepo>(builder.config());
}

struct DocContext
{
    std::unique_ptr<const DocumentTypeRepo> _repo;
    DocContext()
        : _repo(makeDocTypeRepo())
    {
    }
    std::shared_ptr<Document> create(uint32_t id, int64_t fieldValue) {
        vespalib::string docId =
                vespalib::make_string("id:searchdocument:searchdocument::%u", id);
        auto doc = std::make_shared<Document>(*_repo, *_repo->getDocumentType("searchdocument"), DocumentId(docId));
        doc->setValue("a1", IntFieldValue(fieldValue));
        return doc;
    }
};

struct Fixture
{
    DirectoryHandler _testDir;
    DummyFileHeaderContext _fileHeader;
    ForegroundTaskExecutor _attributeFieldWriter;
    ForegroundThreadExecutor _shared;
    HwInfo                 _hwInfo;
    AttributeManager::SP _mgr;
    std::unique_ptr<AttributePopulator> _pop;
    DocContext _ctx;
    Fixture()
        : _testDir(TEST_DIR),
          _fileHeader(),
          _attributeFieldWriter(),
          _shared(),
          _hwInfo(),
          _mgr(std::make_shared<AttributeManager>(TEST_DIR, "test.subdb", TuneFileAttributes(),
                                                  _fileHeader, std::make_shared<search::attribute::Interlock>(),
                                                  _attributeFieldWriter, _shared, _hwInfo)),
          _pop(),
          _ctx()
    {
        _mgr->addAttribute({ "a1", AVConfig(AVBasicType::INT32)}, CREATE_SERIAL_NUM);
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

    f._pop->handleExisting(5, f._ctx.create(0, 33));
    EXPECT_EQUAL(6u, attr->get()->getNumDocs());
    EXPECT_EQUAL(33, attr->get()->getInt(5));
    EXPECT_EQUAL(0u, attr->get()->getStatus().getLastSyncToken());

    f._pop->handleExisting(6, f._ctx.create(1, 44));
    EXPECT_EQUAL(7u, attr->get()->getNumDocs());
    EXPECT_EQUAL(44, attr->get()->getInt(6));
    EXPECT_EQUAL(0u, attr->get()->getStatus().getLastSyncToken());
    f._pop->done();
    EXPECT_EQUAL(CREATE_SERIAL_NUM, attr->get()->getStatus().getLastSyncToken());
}

TEST_MAIN()
{
    std::filesystem::remove_all(std::filesystem::path(TEST_DIR));
    TEST_RUN_ALL();
}
