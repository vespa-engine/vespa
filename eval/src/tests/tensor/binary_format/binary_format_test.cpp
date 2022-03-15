// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/test/test_io.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/cell_type.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/streamed/streamed_value_builder_factory.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/io/mapped_file_input.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::slime::convenience;

using vespalib::make_string_short::fmt;

vespalib::string get_source_dir() {
    const char *dir = getenv("SOURCE_DIRECTORY");
    return (dir ? dir : ".");
}
vespalib::string source_dir = get_source_dir();
vespalib::string module_src_path = source_dir + "/../../../../";
vespalib::string module_build_path = "../../../../";

const ValueBuilderFactory &simple = SimpleValueBuilderFactory::get();
const ValueBuilderFactory &streamed = StreamedValueBuilderFactory::get();
const ValueBuilderFactory &fast = FastValueBuilderFactory::get();

TEST(TensorBinaryFormatTest, tensor_binary_format_test_spec_can_be_generated) {
    vespalib::string spec = module_src_path + "src/apps/make_tensor_binary_format_test_spec/test_spec.json";
    vespalib::string binary = module_build_path + "src/apps/make_tensor_binary_format_test_spec/eval_make_tensor_binary_format_test_spec_app";
    EXPECT_EQ(system(fmt("%s > binary_test_spec.json", binary.c_str()).c_str()), 0);
    EXPECT_EQ(system(fmt("diff -u %s binary_test_spec.json", spec.c_str()).c_str()), 0);
}

void verify_encode_decode(const TensorSpec &spec,
                          const ValueBuilderFactory &encode_factory,
                          const ValueBuilderFactory &decode_factory)
{
    nbostream data;
    auto value = value_from_spec(spec, encode_factory);
    encode_value(*value, data);
    auto value2 = decode_value(data, decode_factory);
    TensorSpec spec2 = spec_from_value(*value2);
    EXPECT_EQ(spec2, spec);
}

void verify_encode_decode(const GenSpec &spec) {
    for (CellType ct : CellTypeUtils::list_types()) {
        auto my_spec = spec.cpy().cells(ct);
        if (my_spec.bad_scalar()) continue;
        auto my_tspec = my_spec.gen();
        verify_encode_decode(my_tspec, simple, fast);
        verify_encode_decode(my_tspec, fast, simple);
        verify_encode_decode(my_tspec, simple, streamed);
        verify_encode_decode(my_tspec, streamed, simple);
    }
}

TEST(TensorBinaryFormatTest, encode_decode) {
    verify_encode_decode(GenSpec(42));
    verify_encode_decode(GenSpec().idx("x", 3));
    verify_encode_decode(GenSpec().idx("x", 3).idx("y", 5));
    verify_encode_decode(GenSpec().idx("x", 3).idx("y", 5).idx("z", 7));
    verify_encode_decode(GenSpec().map("x", 3));
    verify_encode_decode(GenSpec().map("x", 3).map("y", 2));
    verify_encode_decode(GenSpec().map("x", 3).map("y", 2).map("z", 4));
    verify_encode_decode(GenSpec().idx("x", 3).map("y", 2).idx("z", 7));
    verify_encode_decode(GenSpec().map("x", 3).idx("y", 5).map("z", 4));
}

uint8_t unhex(char c) {
    if (c >= '0' && c <= '9') {
        return (c - '0');
    }
    if (c >= 'A' && c <= 'F') {
        return ((c - 'A') + 10);
    }
    EXPECT_TRUE(false) << "bad hex char";
    return 0;
}

nbostream extract_data(const Memory &hex_dump) {
    nbostream data;
    if ((hex_dump.size > 2) && (hex_dump.data[0] == '0') && (hex_dump.data[1] == 'x')) {
        for (size_t i = 2; i < (hex_dump.size - 1); i += 2) {
            data << uint8_t((unhex(hex_dump.data[i]) << 4) | unhex(hex_dump.data[i + 1]));
        }
    }
    return data;
}

bool is_same(const nbostream &a, const nbostream &b) {
    return (Memory(a.peek(), a.size()) == Memory(b.peek(), b.size()));
}

void test_binary_format_spec(const Inspector &test, const ValueBuilderFactory &factory) {
    Stash stash;
    TensorSpec spec = TensorSpec::from_slime(test["tensor"]);
    const Inspector &binary = test["binary"];
    EXPECT_GT(binary.entries(), 0u);
    nbostream encoded;
    encode_value(*value_from_spec(spec, factory), encoded);
    bool matched_encode = false;
    for (size_t i = 0; i < binary.entries(); ++i) {
        nbostream data = extract_data(binary[i].asString());
        matched_encode = (matched_encode || is_same(encoded, data));
        EXPECT_EQ(spec_from_value(*decode_value(data, factory)), spec);
        EXPECT_EQ(data.size(), 0u);
    }
    EXPECT_TRUE(matched_encode);
}

void test_binary_format_spec(Cursor &test) {
    test_binary_format_spec(test, simple);
    test_binary_format_spec(test, streamed);
    test_binary_format_spec(test, fast);
}

TEST(TensorBinaryFormatTest, tensor_binary_format_test_spec) {
    vespalib::string path = module_src_path;
    path.append("src/apps/make_tensor_binary_format_test_spec/test_spec.json");
    MappedFileInput file(path);
    EXPECT_TRUE(file.valid());
    auto handle_test = [](Slime &slime)
                       {
                           test_binary_format_spec(slime.get());
                       };
    auto handle_summary = [](Slime &slime)
                          {
                              EXPECT_GT(slime["num_tests"].asLong(), 0);
                          };
    for_each_test(file, handle_test, handle_summary);
}

GTEST_MAIN_RUN_ALL_TESTS()
