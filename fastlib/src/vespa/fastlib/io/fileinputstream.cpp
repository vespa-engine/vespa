// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
*******************************************************************************
*
* @author Stein Hardy Danielsen
* @date            Creation date: 2000-10-07
*
* FileInputStream class implementation
*
* Copyright (c)  : 1997-2000 Fast Search & Transfer ASA
*                  ALL RIGHTS RESERVED
*
******************************************************************************/

#include "fileinputstream.h"

Fast_FileInputStream::Fast_FileInputStream(const char *fileName)
    : _theFile(new FastOS_File(fileName)),
      _fileOpenedOk(false)
{
    _fileOpenedOk = _theFile->OpenReadOnly();
}

Fast_FileInputStream::~Fast_FileInputStream()
{
    Close();
    delete _theFile;
}

ssize_t
Fast_FileInputStream::Read(void *targetBuffer, size_t bufferSize)
{
    ssize_t retVal = -1;
    if (_fileOpenedOk) {
        retVal = _theFile->Read(targetBuffer, bufferSize);
    }
    return retVal;
};

bool
Fast_FileInputStream::Close() {
    return _theFile->Close();
}

ssize_t
Fast_FileInputStream::Available() {
    return 0;
}

ssize_t
Fast_FileInputStream::Skip(size_t) {
    return 0;
}
