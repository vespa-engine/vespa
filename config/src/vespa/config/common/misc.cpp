// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "misc.h"
#include <iostream>
#include <sstream>
#include <xxhash.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/data/slime/slime.h>
#include "exceptions.h"

using vespalib::Memory;

namespace config {

vespalib::string
calculateContentXxhash64(const StringVector & fileContents)
{
    vespalib::string normalizedLines;
    XXH64_hash_t xxhash64;
    vespalib::asciistream s;
    std::stringstream ss;

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
    xxhash64 = XXH64((const unsigned char*)normalizedLines.c_str(), normalizedLines.size(), 0);

    ss << std::hex << xxhash64;
    ss << std::endl;
    return ss.str();
}

StringVector
getlines(vespalib::asciistream & is, char delim)
{
    StringVector lines;
    while (!is.eof()) {
        lines.push_back(is.getline(delim));
    }
    return lines;
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
