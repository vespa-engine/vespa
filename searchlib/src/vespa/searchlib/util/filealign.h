// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>

class FastOS_FileInterface;

namespace vespalib { class nbostream; }

namespace search {

class FileAlign
{
private:
    size_t _directIOFileAlign;
    size_t _preferredFileAlign;
    size_t _minDirectIOSize;
    size_t _minAlignedSize;
    size_t _elemSize;
    size_t _directIOMemAlign;
    bool   _directio;
    bool   _checkPointResumed;


public:
    FileAlign();
    ~FileAlign();

    /**
     * Adjust number of bytes for IO (read or write), reducing
     * number of bytes if it helps making end of IO matching
     * an alignment boundary.
     *
     * @param offset position of start of IO, measured in bytes
     * @param size   number of bytes for IO
     *
     * @return adjusted number of bytes for IO
     */
    size_t adjustSize(int64_t offset, size_t size);

    /**
     * Adjust number of elements for IO (read or write), reducing
     * number of elements if it helps making end of IO matching
     * an alignment boundary.
     *
     * @param eoffset position of start of IO, measured in elements
     * @param esize   number of elements for IO
     *
     * @return adjusted number of elements for IO
     */
    size_t adjustElements(int64_t eoffset, size_t esize);

    /**
     * Setup alignment
     *
     * @param elements	suggested number of elements in buffer
     * @param elemSize  size of each elements
     * @param file      File interface for IO
     * @param preferredFileAlignment prefered alignment for IO
     *
     * @return adjusted number of elements in buffer
     */
    size_t setupAlign(size_t elements, size_t elemSize, FastOS_FileInterface *file, size_t preferredFileAlignment);
    bool getDirectIO() const { return _directio; }
    bool getCheckPointResumed() const { return _checkPointResumed; }
    size_t getDirectIOFileAlign() const { return _directIOFileAlign; }
    size_t getDirectIOMemAlign() const { return _directIOMemAlign; }
    size_t getMinDirectIOSize() const { return _minDirectIOSize; }
    size_t getMinAlignedSize() const { return _minAlignedSize; }
    size_t getPreferredFileAlign() const { return _preferredFileAlign; }
    size_t getElemSize() const { return _elemSize; }

    /**
     * Checkpoint write.  Used at semi-regular intervals during indexing
     * to allow for continued indexing after an interrupt.
     */
    void checkPointWrite(vespalib::nbostream &out);

    /**
     * Checkpoint read.  Used when resuming indexing after an interrupt.
     */
    void checkPointRead(vespalib::nbostream &in);
};

}
