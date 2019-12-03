// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <memory>
#include <utility>

namespace document { class Document; }

namespace search::docsummary {

/**
 * Simple wrapper class containing the location and size of a docsum
 * blob located in memory. The memory containing the docsum blob is
 * owned by the object that emitted the docsum store value object.
 * Always start with an uint32_t representing the result class ID.
 **/
class DocsumStoreValue
{
private:
    std::pair<const char *, uint32_t> _value;
    // The document instance that was used to generate the docsum blob.
    // Note: This is temporary until the docsummary framework is simplified,
    //       and the docsum blob concept is removed.
    std::unique_ptr<document::Document> _document;

public:
    DocsumStoreValue(const DocsumStoreValue&) = delete;
    DocsumStoreValue& operator=(const DocsumStoreValue&) = delete;

    /**
     * Construct object representing an empty docsum blob.
     **/
    DocsumStoreValue();

    /**
     * Construct object encapsulating the given location and size.
     *
     * @param pt_ docsum location
     * @param len_ docsum size
     **/
    DocsumStoreValue(const char *pt_, uint32_t len_);

    /**
     * Construct object encapsulating the given location and size.
     *
     * @param pt_ docsum location
     * @param len_ docsum size
     * @param document_ document instance used to generate the docsum blob
     **/
    DocsumStoreValue(const char *pt_, uint32_t len_, std::unique_ptr<document::Document> document_);

    ~DocsumStoreValue();

    /**
     * @return docsum blob location
     **/
    const char *pt() const { return _value.first; }

    /**
     * @return docsum blob size
     **/
    uint32_t len() const { return _value.second; }

    /**
     * @return pointer to start of serialized docsum fields
     **/
    const char *fieldsPt() const { return _value.first + sizeof(uint32_t); }

    /**
     * @return size of serialized docsum fields
     **/
    uint32_t fieldsSz() const { return _value.second - sizeof(uint32_t); }

    /**
     * @return true if this has a valid blob
     **/
    bool valid() const { return (_value.first != 0) && (_value.second >= sizeof(uint32_t)); }

    const document::Document* get_document() const { return _document.get(); }
};

}
