// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/fieldvalue/predicatefieldvalue.h>
#include <vespa/document/predicate/predicate_slime_builder.h>
#include <vespa/fastos/file.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/predicate_attribute.h>
#include <vespa/searchlib/common/fileheadertags.h>
#include <vespa/searchlib/util/file_settings.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/exceptions.h>
#include <filesystem>
#include <memory>
#include <sstream>

using document::FieldValue;
using document::PredicateSlimeBuilder;
using document::PredicateFieldValue;
using search::AttributeFactory;
using search::AttributeVector;
using search::FileSettings;
using search::PredicateAttribute;
using search::attribute::Config;
using search::attribute::BasicType;
using search::tags::FILE_BIT_SIZE;
using vespalib::IllegalStateException;
using Tag = vespalib::GenericHeader::Tag;

namespace {

std::filesystem::path tmp_dir("tmp");
std::string attr_name("test");

void remove_tmp_dir() {
    std::filesystem::remove_all(tmp_dir);
}

void make_tmp_dir() {
    std::filesystem::create_directories(tmp_dir);
}

const Config predicate(BasicType::Type::PREDICATE);

Config get_predicate_with_arity(uint32_t arity)
{
    Config ret(predicate);
    search::attribute::PredicateParams predicateParams;
    predicateParams.setArity(arity);
    ret.setPredicateParams(predicateParams);
    return ret;
}

std::shared_ptr<AttributeVector>
make_attribute(vespalib::stringref name, const Config& cfg, bool setup)
{
    auto attribute = AttributeFactory::createAttribute(name, cfg);
    if (attribute && setup) {
        attribute->addReservedDoc();
    }
    return attribute;
}

std::string
fv_as_string(const FieldValue& val)
{
    std::ostringstream os;
    val.print(os, false, "");
    return os.str();
}

std::shared_ptr<AttributeVector>
make_sample_predicate_attribute()
{
    auto cfg = get_predicate_with_arity(2);
    auto attr = make_attribute(attr_name, cfg, true);
    auto& pattr = dynamic_cast<PredicateAttribute&>(*attr);
    PredicateSlimeBuilder builder;
    builder.neg().feature("foo").value("bar").value("baz");
    PredicateFieldValue val(builder.build());
    EXPECT_EQ("'foo' not in ['bar','baz']\n", fv_as_string(val));
    attr->addDocs(10);
    pattr.updateValue(1,  val);
    attr->commit();
    EXPECT_TRUE(attr->isLoaded());
    return attr;
}

void
corrupt_file_header(const vespalib::string &name)
{
    vespalib::FileHeader h(FileSettings::DIRECTIO_ALIGNMENT);
    FastOS_File f;
    auto file_bit_size = 0;
    f.OpenReadWrite(name.c_str());
    h.readFile(f);
    if (h.hasTag(FILE_BIT_SIZE)) {
        file_bit_size = h.getTag(FILE_BIT_SIZE).asInteger() - 8;
    }
    h.putTag(Tag(FILE_BIT_SIZE, file_bit_size));
    h.rewriteFile(f);
    bool sync_ok = f.Sync();
    assert(sync_ok);
}

}

class PredicateAttributeTest : public ::testing::Test
{
protected:
    PredicateAttributeTest();
    ~PredicateAttributeTest() override;
    void SetUp() override;
    void TearDown() override;
};

PredicateAttributeTest::PredicateAttributeTest() = default;

PredicateAttributeTest::~PredicateAttributeTest() = default;

void
PredicateAttributeTest::SetUp()
{
    make_tmp_dir();
}

void
PredicateAttributeTest::TearDown()
{
    remove_tmp_dir();
}

TEST_F(PredicateAttributeTest, save_and_load_predicate_attribute)
{
    auto attr = make_sample_predicate_attribute();
    std::filesystem::path file_name(tmp_dir);
    file_name.append(attr_name);
    attr->save(file_name.native());
    auto attr2 = make_attribute(file_name.native(), attr->getConfig(), false);
    EXPECT_FALSE(attr2->isLoaded());
    EXPECT_TRUE(attr2->load());
    EXPECT_TRUE(attr2->isLoaded());
    EXPECT_EQ(11, attr2->getCommittedDocIdLimit());
}

TEST_F(PredicateAttributeTest, buffer_size_mismatch_is_fatal_during_load)
{
    auto attr = make_sample_predicate_attribute();
    std::filesystem::path file_name(tmp_dir);
    file_name.append(attr_name);
    attr->save(file_name.native());
    corrupt_file_header(file_name.native() + ".dat");
    auto attr2 = make_attribute(file_name.native(), attr->getConfig(), false);
    EXPECT_FALSE(attr2->isLoaded());
    try {
        attr2->load();
        FAIL() << "Expected exception not thrown when loading corrupt attribute";
    } catch (IllegalStateException& e) {
        EXPECT_EQ("Deserialize error when loading predicate attribute 'test', -1 bytes remaining in buffer", e.getMessage());
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
