// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
*******************************************************************************
*
* @author          Stein Hardy Danielsen
* @date            Creation date: 2000-10-07
* @version         $Id$
*
* @file
*
* FileOutputStream class
*
* Copyright (c)  : 1997-2000 Fast Search & Transfer ASA
*                  ALL RIGHTS RESERVED
*
******************************************************************************/
#pragma once

#include <vespa/fastos/fastos.h>
#include <vespa/fastlib/io/outputstream.h>




/**
********************************************************************************
*
* FileOutputStream class
* @author          Stein Hardy Danielsen
* @date            Creation date: 2000-10-07
* @version         $Id$
*
*
* Copyright (c)  : 1997-2000 Fast Search & Transfer ASA
*                  ALL RIGHTS RESERVED
*
******************************************************************************/
class Fast_FileOutputStream  : public Fast_OutputStream
{
private:
  Fast_FileOutputStream(const Fast_FileOutputStream&);
  Fast_FileOutputStream& operator=(const Fast_FileOutputStream&);

  protected:

    /** Pointer to the physical file object*/
    FastOS_FileInterface  *_theFile;

  public:

    /** Constructor */
    Fast_FileOutputStream(const char *fileName);

    /** Destructor */
    virtual ~Fast_FileOutputStream(void);


    // Implementation of Fast_OutputStream interface

    inline ssize_t Write(const void *sourceBuffer, size_t bufferSize)
    {
      return _theFile->CheckedWrite(sourceBuffer, bufferSize) ?
          static_cast<ssize_t>(bufferSize) :
          static_cast<ssize_t>(-1);
    };

    inline bool Close(void)
    {
      return _theFile->Close();
    };

    inline void Flush(void)
    {
    };

};






