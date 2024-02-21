// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/vespalib/testkit/test_path.h>
#include <gtest/gtest.h>
#include <fstream>
#include <filesystem>
#include <iostream>
#include <map>
#include <sstream>
#include <string>

using NamedErrorCodes = std::map<std::string, uint32_t>;

namespace {

// ERROR_CODE_KV(FOO) -> {"FOO", DocumentProtocol::FOO}
#define ERROR_CODE_KV(code_name) \
    {#code_name, DocumentProtocol::code_name}

NamedErrorCodes all_document_protocol_error_codes() {
    using documentapi::DocumentProtocol;
    return {
        ERROR_CODE_KV(ERROR_MESSAGE_IGNORED),
        ERROR_CODE_KV(ERROR_POLICY_FAILURE),
        ERROR_CODE_KV(ERROR_DOCUMENT_NOT_FOUND),
        // Error code not consistently named between languages!
        // Java: ERROR_DOCUMENT_EXISTS, C++: ERROR_EXISTS
        // Names must be consistent in test or checking will fail.
        {"ERROR_DOCUMENT_EXISTS", DocumentProtocol::ERROR_EXISTS},
        ERROR_CODE_KV(ERROR_REJECTED),
        ERROR_CODE_KV(ERROR_NOT_IMPLEMENTED),
        ERROR_CODE_KV(ERROR_ILLEGAL_PARAMETERS),
        ERROR_CODE_KV(ERROR_UNKNOWN_COMMAND),
        ERROR_CODE_KV(ERROR_NO_SPACE),
        ERROR_CODE_KV(ERROR_IGNORED),
        ERROR_CODE_KV(ERROR_INTERNAL_FAILURE),
        ERROR_CODE_KV(ERROR_TEST_AND_SET_CONDITION_FAILED),
        ERROR_CODE_KV(ERROR_PROCESSING_FAILURE),
        ERROR_CODE_KV(ERROR_TIMESTAMP_EXIST),
        ERROR_CODE_KV(ERROR_NODE_NOT_READY),
        ERROR_CODE_KV(ERROR_WRONG_DISTRIBUTION),
        ERROR_CODE_KV(ERROR_ABORTED),
        ERROR_CODE_KV(ERROR_BUSY),
        ERROR_CODE_KV(ERROR_NOT_CONNECTED),
        ERROR_CODE_KV(ERROR_DISK_FAILURE),
        ERROR_CODE_KV(ERROR_IO_FAILURE),
        ERROR_CODE_KV(ERROR_BUCKET_NOT_FOUND),
        ERROR_CODE_KV(ERROR_BUCKET_DELETED),
        ERROR_CODE_KV(ERROR_STALE_TIMESTAMP),
        ERROR_CODE_KV(ERROR_SUSPENDED)
    };
}

#undef ERROR_CODE_KV

std::string read_file(const std::filesystem::path& file_name) {
    std::ifstream ifs(file_name);
    if (!ifs.is_open()) {
        throw std::runtime_error("file '" + file_name.native() + "' does not exist");
    }
    std::ostringstream oss;
    oss << ifs.rdbuf();
    return oss.str();
}

void write_file(const std::filesystem::path& file_name,
                const std::string& content)
{
    std::ofstream ofs(file_name, std::ios_base::trunc);
    ofs << content;
}

std::string to_sorted_key_value_string(const NamedErrorCodes& codes) {
    std::ostringstream os;
    bool emit_newline = false;
    for (auto& kv : codes) {
        if (emit_newline) {
            os << '\n';
        }
        os << kv.first << ' ' << kv.second;
        emit_newline = true;
    }
    return os.str();
}

std::filesystem::path path_prefixed(const std::string& file_name) {
    return {TEST_PATH("../../../test/crosslanguagefiles/" + file_name)};
}

} // anon ns

namespace documentapi {

TEST(ErrorCodesTest, error_codes_match_java_definitions) {
    NamedErrorCodes codes(all_document_protocol_error_codes());
    auto cpp_golden_file = path_prefixed("HEAD-cpp-golden-error-codes.txt");
    auto cpp_golden_data = to_sorted_key_value_string(codes);
    write_file(cpp_golden_file, cpp_golden_data);

    auto java_golden_file = path_prefixed("HEAD-java-golden-error-codes.txt");
    auto java_golden_data = read_file(java_golden_file);
    EXPECT_EQ(cpp_golden_data, java_golden_data);
}

TEST(ErrorCodesTest, stringification_is_defined_for_all_error_codes) {
    using documentapi::DocumentProtocol;
    NamedErrorCodes codes(all_document_protocol_error_codes());
    for (const auto& kv : codes) {
        // Ugh, special casing due to divergence between Java and C++ naming.
        // Can we fix this without breaking anything in exciting ways?
        if (kv.second != DocumentProtocol::ERROR_EXISTS) {
            EXPECT_EQ(kv.first, "ERROR_" + DocumentProtocol::getErrorName(kv.second));
        } else {
            EXPECT_EQ("EXISTS", DocumentProtocol::getErrorName(kv.second));
        }
    }
}

} // documentapi
