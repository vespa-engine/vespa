// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/memory.h>
#include <vector>

namespace document { class ByteBuffer; }
namespace vespalib { class GrowableByteBuffer; }
namespace vdslib {

class DocumentSummary {
public:
    DocumentSummary();
    ~DocumentSummary();


    /**
     * Constructs a new message from a byte buffer.
     *
     * @param buf A byte buffer that contains a serialized message.
     */
    DocumentSummary(document::ByteBuffer& buf);

    size_t getSummaryCount() const { return _summary.size(); }
    void   getSummary(size_t hitNo, const char * & docId, const void * & buf, size_t & sz) {
        docId = _summary[hitNo].getDocId(_summaryBuffer->c_str());
        buf = _summary[hitNo].getSummary(_summaryBuffer->c_str(), sz);
    }
    void addSummary(const char * docId, const void * buf, size_t sz);
    void sort();

    void deserialize(document::ByteBuffer& buf);
    void serialize(vespalib::GrowableByteBuffer& buf) const;
    uint32_t getSerializedSize() const;
private:
    class Summary {
    public:
        Summary() noexcept : _docIdOffset(0), _summaryOffset(0), _summaryLen(0) { }
        Summary(uint32_t docIdOffset, uint32_t summaryOffset, uint32_t summaryLen) : _docIdOffset(docIdOffset), _summaryOffset(summaryOffset), _summaryLen(summaryLen) { }
        const char * getDocId(const char * base)                const { return base + _docIdOffset; }
        const void * getSummary(const char * base, size_t & sz) const { sz = _summaryLen; return base + _summaryOffset; }
        size_t getSummarySize()                                 const { return _summaryLen; }
        size_t getTotalSize()                                   const { return _summaryOffset - _docIdOffset + _summaryLen; }
    private:
        uint32_t _docIdOffset;
        uint32_t _summaryOffset;
        uint32_t _summaryLen;
    };
    class Compare {
    private:
        const char * _buffer;
    public:
        Compare(const char * buffer) : _buffer(buffer) { }
        bool operator() (const Summary & x, const Summary & y) const {
            return strcmp(x.getDocId(_buffer), y.getDocId(_buffer)) < 0;
        }
    };
    size_t getSummarySize() const { return _summarySize; }
    typedef std::shared_ptr<vespalib::MallocPtr> DocBuffer;
    DocBuffer            _summaryBuffer;  // Raw zero-terminated documentids in rank order.
    std::vector<Summary> _summary;  // Constructed vector containing offset of document in buffer.
    size_t               _summarySize;
};

}

