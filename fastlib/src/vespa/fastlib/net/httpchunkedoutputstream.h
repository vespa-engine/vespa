// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
*******************************************************************************
*
* @author          Markus Bjartveit Kr�ger
* @date            Creation date: 2000-11-21
* @version         $Id$
*
* @file
*
* HTTP chunked output stream.
*
* Copyright (c)  : 2001 Fast Search & Transfer ASA
*                  ALL RIGHTS RESERVED
*
******************************************************************************/


#pragma once

#include <vespa/fastos/fastos.h>
#include <vespa/fastlib/io/filteroutputstream.h>





class Fast_HTTPChunkedOutputStream : public Fast_FilterOutputStream
{
  private:
    // Prevent use of:
    Fast_HTTPChunkedOutputStream(const Fast_HTTPChunkedOutputStream &);
    Fast_HTTPChunkedOutputStream & operator=(const Fast_HTTPChunkedOutputStream &);
  protected:
    size_t  _chunkSize;
    char   *_buffer;
    size_t  _bufferUsed;
    bool    _writeHasFailed;

    bool WriteChunk(void);
  public:
    Fast_HTTPChunkedOutputStream(Fast_OutputStream &out, size_t chunkSize = 1024);
    virtual ~Fast_HTTPChunkedOutputStream(void);


    // Methods
    virtual bool    Close(void);
    virtual ssize_t Write(const void *sourceBuffer, size_t length);
    virtual void    Flush(void);
};



