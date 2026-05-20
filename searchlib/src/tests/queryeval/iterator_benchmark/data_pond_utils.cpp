// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "data_pond_utils.h"

#include <vespa/vespalib/data/memory.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/data/slime/binary_format.h>
#include <vespa/vespalib/data/slime/inspector.h>
#include <vespa/vespalib/data/slime/object_traverser.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/slime/type.h>
#include <vespa/vespalib/io/fileutil.h>

using vespalib::File;
using vespalib::Memory;
using vespalib::SimpleBuffer;
using vespalib::Slime;
using vespalib::slime::BinaryFormat;
using vespalib::slime::BOOL;
using vespalib::slime::Cursor;
using vespalib::slime::DOUBLE;
using vespalib::slime::Inspector;
using vespalib::slime::LONG;
using vespalib::slime::ObjectTraverser;
using vespalib::slime::STRING;

namespace search::queryeval::test {

void write_data_pond_to_file(const std::string& file_path, const DataPond& data_pond) {
    Slime   slime;
    Cursor& root = slime.setObject();
    Cursor& records = root.setArray("records", data_pond.records().size());
    for (const auto& rec : data_pond.records()) {
        Cursor& obj = records.addObject();
        for (const auto& [name, field] : rec.data()) {
            if (field.has_type<int64_t>()) {
                obj.setLong(name, field.get<int64_t>());
            } else if (field.has_type<double>()) {
                obj.setDouble(name, field.get<double>());
            } else if (field.has_type<bool>()) {
                obj.setBool(name, field.get<bool>());
            } else if (field.has_type<std::string>()) {
                obj.setString(name, field.get<std::string>());
            }
        }
    }

    SimpleBuffer buffer;
    BinaryFormat::encode(slime, buffer);
    Memory bytes = buffer.get();

    File out(file_path);
    out.open(File::CREATE | File::TRUNC);
    out.write(bytes.data, bytes.size, 0);
}

/**
 * Copies fields from slime to data pond record.
 */
class FieldCopier : public ObjectTraverser {
    Record& _target;

public:
    explicit FieldCopier(Record& target) noexcept : _target(target) {}

    void field(const Memory& name, const Inspector& value) override {
        auto field_name = name.make_string();
        switch (value.type().getId()) {
        case LONG::ID:
            _target.set(field_name, value.asLong());
            break;
        case DOUBLE::ID:
            _target.set(field_name, value.asDouble());
            break;
        case BOOL::ID:
            _target.set(field_name, value.asBool());
            break;
        case STRING::ID: {
            _target.set(field_name, value.asString().make_string());
            break;
        }
        default:
            break;
        }
    }
};

void read_file_into_data_pond(const std::string& file_path, DataPond& data_pond) {
    std::string bytes = File::readAll(file_path);

    Slime slime;
    BinaryFormat::decode(Memory(bytes.data(), bytes.size()), slime);

    const Inspector& records = slime.get()["records"];
    for (size_t i = 0; i < records.entries(); ++i) {
        const Inspector& rec_in = records[i];
        Record&          rec_out = data_pond.new_record();
        FieldCopier      copier(rec_out);
        rec_in.traverse(copier);
    }
}

} // namespace search::queryeval::test
