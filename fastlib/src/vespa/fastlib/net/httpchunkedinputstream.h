// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
*******************************************************************************
*
* @author          Markus Bjartveit Kr�ger
* @date            Creation date: 2000-11-22
* @version         $Id$
*
* @file
*
* HTTP chunked input stream.
*
* Copyright (c)  : 2001 Fast Search & Transfer ASA
*                  ALL RIGHTS RESERVED
*
******************************************************************************/


#pragma once

#include <vespa/fastlib/io/filterinputstream.h>





class Fast_HTTPChunkedInputStream : public Fast_FilterInputStream
{
  private:
    // Prevent use of:
    Fast_HTTPChunkedInputStream(const Fast_HTTPChunkedInputStream &);
    Fast_HTTPChunkedInputStream & operator=(const Fast_HTTPChunkedInputStream &);
  protected:
    size_t _chunkSize;
    bool   _inChunk;
    bool   _isClosed;

    bool ReadChunkHeader(void);
  public:
    Fast_HTTPChunkedInputStream(Fast_InputStream &in);
    virtual ~Fast_HTTPChunkedInputStream(void);


    // Methods
    virtual ssize_t Available(void);
    virtual bool    Close(void);
    virtual ssize_t Read(void *targetBuffer, size_t length);
    virtual ssize_t Skip(size_t skipNBytes);
};



