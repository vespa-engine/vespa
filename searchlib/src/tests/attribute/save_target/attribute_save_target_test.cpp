// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/attributefilesavetarget.h>
#include <vespa/searchlib/attribute/attributememorysavetarget.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/searchlib/util/fileutil.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_save_target_test");

using namespace search;
using namespace search::attribute;

using search::index::DummyFileHeaderContext;
using search::test::DirectoryHandler;

const vespalib::string test_dir = "test_data/";

class SaveTargetTest : public ::testing::Test {
public:
    DirectoryHandler dir_handler;
    TuneFileAttributes tune_file;
    DummyFileHeaderContext file_header_ctx;
    IAttributeSaveTarget& target;
    vespalib::string base_file_name;

    SaveTargetTest(IAttributeSaveTarget& target_in)
        : dir_handler(test_dir),
          tune_file(),
          file_header_ctx(),
          target(target_in),
          base_file_name(test_dir + "test_file")
    {
    }
    ~SaveTargetTest() {}
    void set_header(const vespalib::string& file_name) {
        target.setHeader(AttributeHeader(file_name));
    }
    IAttributeFileWriter& setup_writer(const vespalib::string& file_suffix,
                                       const vespalib::string& desc) {
        bool res = target.setup_writer(file_suffix, desc);
        assert(res);
        return target.get_writer(file_suffix);
    }
    void setup_writer_and_fill(const vespalib::string& file_suffix,
                               const vespalib::string& desc,
                               int value) {
        auto& writer = setup_writer(file_suffix, desc);
        auto buf = writer.allocBufferWriter();
        buf->write(&value, sizeof(int));
        buf->flush();
    }
    void validate_loaded_file(const vespalib::string& file_suffix,
                              const vespalib::string& exp_desc,
                              int exp_value)
    {
        vespalib::string file_name = base_file_name + "." + file_suffix;
        EXPECT_TRUE(std::filesystem::exists(std::filesystem::path(file_name)));
        auto loaded = FileUtil::loadFile(file_name);
        EXPECT_FALSE(loaded->empty());

        const auto& header = loaded->getHeader();
        EXPECT_EQ(file_name, header.getTag("fileName").asString());
        EXPECT_EQ(exp_desc, header.getTag("desc").asString());

        EXPECT_EQ(sizeof(int), loaded->size());
        int act_value = (reinterpret_cast<const int*>(loaded->buffer()))[0];
        EXPECT_EQ(exp_value, act_value);
    }
};

class FileSaveTargetTest : public SaveTargetTest {
public:
    AttributeFileSaveTarget file_target;

    FileSaveTargetTest()
        : SaveTargetTest(file_target),
          file_target(tune_file, file_header_ctx)
    {
        set_header(base_file_name);
    }
};

TEST_F(FileSaveTargetTest, can_setup_and_return_writers)
{
    setup_writer_and_fill("my1", "desc 1", 123);
    setup_writer_and_fill("my2", "desc 2", 456);
    target.close();

    validate_loaded_file("my1", "desc 1", 123);
    validate_loaded_file("my2", "desc 2", 456);
}

TEST_F(FileSaveTargetTest, setup_fails_if_writer_already_exists)
{
    setup_writer("my", "my desc");
    EXPECT_FALSE(target.setup_writer("my", "my desc"));
}

TEST_F(FileSaveTargetTest, get_throws_if_writer_does_not_exists)
{
    EXPECT_THROW(target.get_writer("na"), vespalib::IllegalArgumentException);
}

class MemorySaveTargetTest : public SaveTargetTest {
public:
    AttributeMemorySaveTarget memory_target;

    MemorySaveTargetTest()
            : SaveTargetTest(memory_target),
              memory_target()
    {
        set_header(base_file_name);
    }
    void write_to_file() {
        bool res = memory_target.writeToFile(tune_file, file_header_ctx);
        ASSERT_TRUE(res);
    }
};

TEST_F(MemorySaveTargetTest, can_setup_and_return_writers)
{
    setup_writer_and_fill("my1", "desc 1", 123);
    setup_writer_and_fill("my2", "desc 2", 456);
    write_to_file();

    validate_loaded_file("my1", "desc 1", 123);
    validate_loaded_file("my2", "desc 2", 456);
}

TEST_F(MemorySaveTargetTest, setup_fails_if_writer_already_exists)
{
    setup_writer("my", "my desc");
    EXPECT_FALSE(target.setup_writer("my", "my desc"));
}

TEST_F(MemorySaveTargetTest, get_throws_if_writer_does_not_exists)
{
    EXPECT_THROW(target.get_writer("na"), vespalib::IllegalArgumentException);
}

GTEST_MAIN_RUN_ALL_TESTS()

