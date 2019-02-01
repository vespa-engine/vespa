// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "misc.h"
#include <vespa/vespalib/util/md5.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/data/slime/slime.h>
#include "exceptions.h"

using vespalib::Memory;

namespace config {

vespalib::string
calculateContentMd5(const std::vector<vespalib::string> & fileContents)
{
    vespalib::string normalizedLines;
    int compact_md5size = 16;
    unsigned char md5sum[compact_md5size];
    vespalib::asciistream s;

    // remove comments, trailing spaces and empty lines
    // TODO: Remove multiple spaces and space before comma, like in Java
    for (int i = 0; i < (int)fileContents.size(); i++) {
        std::string line = fileContents[i];
        line = line.erase(line.find_last_not_of("#") + 1);
        line = line.erase(line.find_last_not_of(" ") + 1);
        if (line.size() > 0) {
            line += "\n";
            normalizedLines += line;
        }
    }
    fastc_md5sum((const unsigned char*)normalizedLines.c_str(), normalizedLines.size(), md5sum);

    // convert to 32 character hex string
    for (int i = 0; i < compact_md5size; i++) {
        if (md5sum[i] < 16) {
            s << "0";
        }
        s << vespalib::hex << (int)md5sum[i];
    }
    return s.str();
}

bool
isGenerationNewer(int64_t newGen, int64_t oldGen)
{
    return (newGen > oldGen) || (newGen == 0);
}

void
throwInvalid(const char *format, ...)
{
    char buf[4000];
    va_list args;

    va_start(args, format);
    vsnprintf(buf, sizeof buf, format, args);
    va_end(args);

    throw InvalidConfigException(buf);
}

using namespace vespalib::slime;

void copySlimeArray(const Inspector & src, Cursor & dest);

class CopyObjectTraverser : public ObjectTraverser
{
private:
    Cursor & _dest;
public:
    CopyObjectTraverser(Cursor & dest) : _dest(dest) {}
    void field(const Memory & symbol, const Inspector & inspector) override {
        switch(inspector.type().getId()) {
            case NIX::ID:
                _dest.addNix();
                break;
            case BOOL::ID:
                _dest.setBool(symbol, inspector.asBool());
                break;
            case LONG::ID:
                _dest.setLong(symbol, inspector.asLong());
                break;
            case DOUBLE::ID:
                _dest.setDouble(symbol, inspector.asDouble());
                break;
            case STRING::ID:
                _dest.setString(symbol, inspector.asString());
                break;
            case DATA::ID:
                _dest.setData(symbol, inspector.asData());
                break;
            case ARRAY::ID:
                copySlimeArray(inspector, _dest.setArray(symbol));
                break;
            case OBJECT::ID:
                copySlimeObject(inspector, _dest.setObject(symbol));
                break;
        }
    }
};

class CopyArrayTraverser : public ArrayTraverser
{
private:
    Cursor & _dest;
public:
    CopyArrayTraverser(Cursor & dest) : _dest(dest) {}
    void entry(size_t idx, const Inspector & inspector) override {
        (void) idx;
        switch(inspector.type().getId()) {
            case NIX::ID:
                _dest.addNix();
                break;
            case BOOL::ID:
                _dest.addBool(inspector.asBool());
                break;
            case LONG::ID:
                _dest.addLong(inspector.asLong());
                break;
            case DOUBLE::ID:
                _dest.addDouble(inspector.asDouble());
                break;
            case STRING::ID:
                _dest.addString(inspector.asString());
                break;
            case DATA::ID:
                _dest.addData(inspector.asData());
                break;
            case ARRAY::ID:
                copySlimeArray(inspector, _dest.addArray());
                break;
            case OBJECT::ID:
                copySlimeObject(inspector, _dest.addObject());
                break;
        }
    }
};

void copySlimeArray(const Inspector & src, Cursor & dest)
{
    if (src.type().getId() != ARRAY::ID) {
        throw vespalib::IllegalArgumentException("Source inspector is not of type array");
    }
    CopyArrayTraverser traverser(dest);
    src.traverse(traverser);
}


void copySlimeObject(const Inspector & src, Cursor & dest)
{
    if (src.type().getId() != OBJECT::ID) {
        throw vespalib::IllegalArgumentException("Source inspector is not of type object");
    }
    CopyObjectTraverser traverser(dest);
    src.traverse(traverser);
}

}
