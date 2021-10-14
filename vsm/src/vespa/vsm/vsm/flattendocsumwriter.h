// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/document/fieldvalue/iteratorhandler.h>
#include <vespa/vsm/common/charbuffer.h>

namespace vsm {

/**
 * This class is used to flatten out and write a complex field value.
 * A separator string is inserted between primitive field values.
 **/
class FlattenDocsumWriter : public document::fieldvalue::IteratorHandler {
private:
    CharBuffer       _output;
    vespalib::string _separator;
    bool             _useSeparator;

    void considerSeparator();
    void onPrimitive(uint32_t, const Content & c) override;

public:
    FlattenDocsumWriter(const vespalib::string & separator = " ");
    ~FlattenDocsumWriter();
    void setSeparator(const vespalib::string & separator) { _separator = separator; }
    const CharBuffer & getResult() const { return _output; }
    void clear() {
        _output.reset();
        _separator = " ";
        _useSeparator = false;
    }
};

}

