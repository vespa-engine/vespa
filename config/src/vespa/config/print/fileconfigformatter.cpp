// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fileconfigformatter.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <cmath>
#include <vector>

#include <vespa/log/log.h>
LOG_SETUP(".config.print.fileconfigformatter");

using namespace vespalib::slime::convenience;

using vespalib::slime::ArrayTraverser;
using vespalib::slime::ObjectTraverser;
using vespalib::OutputWriter;
using vespalib::SimpleBuffer;
using vespalib::Output;

namespace config {
    void doEncode(ConfigDataBuffer & buffer, Output & output);
}

namespace {

struct ConfigEncoder : public ArrayTraverser,
                       public ObjectTraverser
{
    OutputWriter &out;
    int level;
    bool head;
    std::vector<std::string> prefixList;

    ConfigEncoder(OutputWriter &out_in)
        : out(out_in), level(0), head(true) {}

    void printPrefix() {
        for (size_t i = 0; i < prefixList.size(); i++) {
            out.printf("%s", prefixList[i].c_str());
        }
    }

    void encodeBOOL(bool value) {
        if (value) {
            out.printf("true");
        } else {
            out.printf("false");
        }
    }
    void encodeLONG(int64_t value) {
        out.printf("%ld", value);
    }
    void encodeDOUBLE(double value) {
        out.printf("%g", value);
    }
    void encodeSTRINGNOQUOTE(const Memory &memory) {
        const char *hex = "0123456789ABCDEF";
        char *p = out.reserve(memory.size * 6);
        size_t len = 0;
        const char *pos = memory.data;
        const char *end = memory.data + memory.size;
        for (; pos < end; ++pos) {
            uint8_t c = *pos;
            switch(c) {
            case '"':  *p++ = '\\'; *p++ = '"';  len += 2; break;
            case '\\': *p++ = '\\'; *p++ = '\\'; len += 2; break;
            case '\b': *p++ = '\\'; *p++ = 'b';  len += 2; break;
            case '\f': *p++ = '\\'; *p++ = 'f';  len += 2; break;
            case '\n': *p++ = '\\'; *p++ = 'n';  len += 2; break;
            case '\r': *p++ = '\\'; *p++ = 'r';  len += 2; break;
            case '\t': *p++ = '\\'; *p++ = 't';  len += 2; break;
            default:
                if (c > 0x1f) {
                    *p++ = c; ++len;
                } else { // requires escaping according to RFC 4627
                    *p++ = '\\'; *p++ = 'u'; *p++ = '0'; *p++ = '0';
                    *p++ = hex[(c >> 4) & 0xf]; *p++ = hex[c & 0xf];
                    len += 6;
                }
            }
        }
        out.commit(len);
    }
    void encodeSTRING(const Memory &memory) {
        out.write('\"');
        encodeSTRINGNOQUOTE(memory);
        out.write('\"');
    }
    void encodeARRAY(const Inspector &inspector) {
        ArrayTraverser &array_traverser = *this;
        inspector.traverse(array_traverser);
    }
    void encodeMAP(const Inspector & inspector) {
        for (size_t i = 0; i < inspector.children(); i++) {
            const Inspector & child(inspector[i]);
            vespalib::asciistream ss;
            ss << "{\"" << child["key"].asString().make_string() << "\"}";
            prefixList.push_back(ss.str());
            encodeMAPEntry(child);
            prefixList.pop_back();
        }
    }
    void encodeMAPEntry(const Inspector & inspector) {
        if (inspector["type"].valid()) {
            std::string type(inspector["type"].asString().make_string());
            if (type.compare("struct") == 0) {
                prefixList.push_back(".");
                encodeOBJECT(inspector["value"]);
                prefixList.pop_back();
            } else {
                printPrefix();
                out.write(' ');
                if (type.compare("enum") == 0) encodeSTRINGNOQUOTE(inspector["value"].asString());
                else encodeValue(inspector["value"]);
                out.write('\n');
            }
        }
    }
    void encodeOBJECT(const Inspector &inspector) {
        ObjectTraverser &object_traverser = *this;
        inspector.traverse(object_traverser);
    }
    void encodeValue(const Inspector &inspector) {
        switch (inspector.type().getId()) {
        case vespalib::slime::BOOL::ID:   return encodeBOOL(inspector.asBool());
        case vespalib::slime::LONG::ID:   return encodeLONG(inspector.asLong());
        case vespalib::slime::DOUBLE::ID: return encodeDOUBLE(inspector.asDouble());
        case vespalib::slime::STRING::ID: return encodeSTRING(inspector.asString());
        case vespalib::slime::ARRAY::ID:  return encodeARRAY(inspector);
        case vespalib::slime::OBJECT::ID: return encodeOBJECT(inspector);
        case vespalib::slime::NIX::ID: return;
        }
        LOG_ABORT("should not be reached"); // should not be reached
    }
    void entry(size_t idx, const Inspector &inspector) override;
    void field(const Memory &symbol_name, const Inspector &inspector) override;

    static void encode(Inspector & root, OutputWriter &out) {
        ConfigEncoder encoder(out);
        encoder.encodeValue(root);
    }
};

void
ConfigEncoder::entry(size_t index, const Inspector &inspector)
{
    if (inspector["type"].valid()) {
        std::string type(inspector["type"].asString().make_string());
        if (type.compare("array") == 0) {
            vespalib::asciistream ss;
            ss << "[" << index << "]";
            prefixList.push_back(ss.str());
            encodeARRAY(inspector["value"]);
            prefixList.pop_back();
        } else if (type.compare("struct") == 0) {
            vespalib::asciistream ss;
            ss << "[" << index << "].";
            prefixList.push_back(ss.str());
            encodeOBJECT(inspector["value"]);
            prefixList.pop_back();
        } else {
            printPrefix();
            out.write('[');
            encodeLONG(index);
            out.write(']');
            out.write(' ');

            if (type.compare("enum") == 0) encodeSTRINGNOQUOTE(inspector["value"].asString());
            else encodeValue(inspector["value"]);
            out.write('\n');
        }
    }
}

void
ConfigEncoder::field(const Memory &symbol_name, const Inspector &inspector)
{
    if (inspector["type"].valid()) {
        std::string type(inspector["type"].asString().make_string());
        if (type.compare("array") == 0) {
            size_t len = inspector["value"].children();
            if (len > 0) {
                prefixList.push_back(symbol_name.make_string());
                encodeARRAY(inspector["value"]);
                prefixList.pop_back();
            }
        } else if (type.compare("map") == 0) {
            size_t len = inspector["value"].children();
            if (len > 0) {
                prefixList.push_back(symbol_name.make_string());
                encodeMAP(inspector["value"]);
                prefixList.pop_back();
            }
        } else if (type.compare("struct") == 0) {
            prefixList.push_back(symbol_name.make_string() + ".");
            encodeOBJECT(inspector["value"]);
            prefixList.pop_back();
        } else {
            printPrefix();
            encodeSTRINGNOQUOTE(symbol_name);
            out.write(' ');

            if (type.compare("enum") == 0) encodeSTRINGNOQUOTE(inspector["value"].asString());
            else encodeValue(inspector["value"]);
            out.write('\n');
        }
    }
}

}

namespace config {

void
doEncode(ConfigDataBuffer & buffer, Output & output)
{
    OutputWriter out(output, 8000);
    ConfigEncoder::encode(buffer.slimeObject().get()["configPayload"], out);
}

void
FileConfigFormatter::encode(ConfigDataBuffer & buffer) const
{
    SimpleBuffer buf;
    doEncode(buffer, buf);
    buffer.setEncodedString(buf.get().make_string());
}

size_t
FileConfigFormatter::decode(ConfigDataBuffer & buffer) const
{
    (void) buffer;
    throw vespalib::IllegalArgumentException("Reading cfg format is not supported");
    return 0;
}

} // namespace config
