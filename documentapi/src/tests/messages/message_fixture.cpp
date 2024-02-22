// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "message_fixture.h"
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/base/testdocrepo.h>
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/testkit/test_path.h>
#include <fcntl.h>
#include <unistd.h>
#include <cassert>
#include <filesystem>

#include <vespa/log/log.h>
LOG_SETUP(".message_fixture");

namespace documentapi {

using document::DocumentTypeRepo;
using document::readDocumenttypesConfig;
using namespace vespalib::make_string_short;

MessageFixture::MessageFixture()
    : _repo(std::make_shared<DocumentTypeRepo>(readDocumenttypesConfig(TEST_PATH("../../../test/cfg/testdoctypes.cfg")))),
      _data_path(TEST_PATH("../../../test/crosslanguagefiles")),
      _protocol(_repo)
{
}

MessageFixture::~MessageFixture() = default;

mbus::Blob
MessageFixture::truncate(mbus::Blob data, size_t bytes)
{
    assert(data.size() > bytes);
    mbus::Blob res(data.size() - bytes);
    memcpy(res.data(), data.data(), res.size());
    return res;
}

mbus::Blob
MessageFixture::pad(mbus::Blob data, size_t bytes)
{
    mbus::Blob res(data.size() + bytes);
    memset(res.data(), 0, res.size());
    memcpy(res.data(), data.data(), data.size());
    return res;
}

bool MessageFixture::file_content_is_unchanged(const std::filesystem::path& filename, const mbus::Blob& data_to_write) {
    if (!std::filesystem::exists(filename)) {
        return false;
    }
    mbus::Blob existing = read_file(filename);
    return ((existing.size() == data_to_write.size())
            && (memcmp(existing.data(), data_to_write.data(), data_to_write.size()) == 0));
}

uint32_t
MessageFixture::serialize(const std::string& filename, const mbus::Routable& routable, Tamper tamper)
{
    const vespalib::Version version = tested_protocol_version();
    const auto path = path_to_file(version.toString() + "-cpp-" + filename + ".dat");
    LOG(info, "Serializing to '%s'...", path.c_str());

    mbus::Blob blob = tamper(_protocol.encode(version, routable));
    if (file_content_is_unchanged(path, blob)) {
        LOG(info, "Serialization for '%s' is unchanged; not overwriting it", path.c_str());
    } else if (!write_file(path, blob)) {
        LOG(error, "Could not open file '%s' for writing.", path.c_str());
        throw vespalib::Exception(fmt("Could not open file '%s' for writing.", path.c_str()), VESPA_STRLOC);
    }
    mbus::Routable::UP obj = _protocol.decode(version, blob);
    if (!obj) {
        LOG(error, "Protocol failed to decode serialized data");
        throw vespalib::Exception("Protocol failed to decode serialized data", VESPA_STRLOC);
    }
    if (routable.getType() != obj->getType()) {
        LOG(error, "Expected class %d, got %d", routable.getType(), obj->getType());
        throw vespalib::Exception(fmt("Expected class %d, got %d", routable.getType(), obj->getType()), VESPA_STRLOC);
    }
    return blob.size();
}

mbus::Routable::UP
MessageFixture::deserialize(const std::string& filename, uint32_t classId, uint32_t lang)
{
    const vespalib::Version version = tested_protocol_version();
    const auto path = path_to_file(version.toString() + (lang == LANG_JAVA ? "-java" : "-cpp") + "-" + filename + ".dat");
    LOG(info, "Deserializing from '%s'...", path.c_str());

    mbus::Blob blob = read_file(path);
    if (blob.size() == 0) {
        LOG(error, "Could not open file '%s' for reading", path.c_str());
        throw vespalib::Exception(fmt("Could not open file '%s' for reading", path.c_str()), VESPA_STRLOC);
    }
    mbus::Routable::UP ret = _protocol.decode(version, blob);

    if (!ret) {
        LOG(error, "Unable to decode class %d", classId);
        throw vespalib::Exception(fmt("Unable to decode class %d", classId), VESPA_STRLOC);
    } else if (classId != ret->getType()) {
        LOG(error, "Expected class %d, got %d", classId, ret->getType());
        throw vespalib::Exception(fmt("Expected class %d, got %d", classId, ret->getType()), VESPA_STRLOC);
    }
    return ret;
}

void
MessageFixture::dump(const mbus::Blob& blob)
{
    fprintf(stderr, "[%ld]: ", blob.size());
    for(size_t i = 0; i < blob.size(); i++) {
        if (blob.data()[i] > 32 && blob.data()[i] < 126) {
            fprintf(stderr, "%c ", blob.data()[i]);
        }
        else {
            fprintf(stderr, "%d ", blob.data()[i]);
        }
    }
    fprintf(stderr, "\n");
}


bool
MessageFixture::write_file(const std::filesystem::path& filename, const mbus::Blob& blob)
{
    int file = open(filename.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (file == -1) {
        return false;
    }
    if (write(file, blob.data(), blob.size()) != (ssize_t)blob.size()) {
        throw vespalib::Exception(fmt("Write of file '%s' failed", filename.c_str()));
    }
    close(file);
    return true;
}

mbus::Blob
MessageFixture::read_file(const std::filesystem::path& filename)
{
    int file = open(filename.c_str(), O_RDONLY);
    int len = (file == -1) ? 0 : lseek(file, 0, SEEK_END);
    mbus::Blob blob(len);
    if (file != -1) {
        lseek(file, 0, SEEK_SET);
        if (read(file, blob.data(), len) != len) {
            throw vespalib::Exception(fmt("Read of file '%s' failed", filename.c_str()));
        }
        close(file);
    }

    return blob;
}

}
