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
* FileInputStream class
*
* Copyright (c)  : 1997-2000 Fast Search & Transfer ASA
*                  ALL RIGHTS RESERVED
*
******************************************************************************/
#pragma once

#include <vespa/fastlib/io/inputstream.h>




/**
********************************************************************************
*
* FileInputStream class
* @author          Stein Hardy Danielsen
* @date            Creation date: 2000-10-07
* @version         $Id$
*
*
* Copyright (c)  : 1997-2000 Fast Search & Transfer ASA
*                  ALL RIGHTS RESERVED
*
******************************************************************************/
class Fast_FileInputStream  : public Fast_InputStream
{
private:
  Fast_FileInputStream(const Fast_FileInputStream&);
  Fast_FileInputStream& operator=(const Fast_FileInputStream&);

  protected:

    /** Pointer to the physical file object*/
    FastOS_FileInterface  *_theFile;

    /** File opened ok flag */
    bool  _fileOpenedOk;


  public:

    /** Constructor */
    Fast_FileInputStream(const char *fileName);

    /** Destructor */
    virtual ~Fast_FileInputStream(void);

    // Implementation of Fast_InputStream interface

    virtual ssize_t Read(void *targetBuffer, size_t bufferSize);
    virtual bool Close();
    virtual ssize_t Available();
    virtual ssize_t Skip(size_t);
};



