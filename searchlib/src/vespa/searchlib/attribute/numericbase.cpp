// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "numericbase.h"
#include <vespa/fastlib/io/bufferedfile.h>
#include "enumstorebase.h"
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/log/log.h>

LOG_SETUP(".searchlib.attribute.numericbase");

namespace search
{

IMPLEMENT_IDENTIFIABLE_ABSTRACT(NumericAttribute, AttributeVector);

using attribute::LoadedEnumAttributeVector;

void
NumericAttribute::fillEnum0(const void *src,
                           size_t srcLen,
                           EnumIndexVector &eidxs)
{
    (void) src;
    (void) srcLen;
    (void) eidxs;
    fprintf(stderr, "NumericAttribute::fillEnum0\n");
}


void
NumericAttribute::fillEnumIdx(ReaderBase &attrReader,
                             const EnumIndexVector &eidxs,
                             LoadedEnumAttributeVector &loaded)
{
    (void) attrReader;
    (void) eidxs;
    (void) loaded;
    fprintf(stderr, "NumericAttribute::fillEnumIdx (loaded)\n");
}


void
NumericAttribute::fillEnumIdx(ReaderBase &attrReader,
                             const EnumIndexVector &eidxs,
                             EnumVector &enumHist)
{
    (void) attrReader;
    (void) eidxs;
    (void) enumHist;
    fprintf(stderr, "NumericAttribute::fillEnumIdx (enumHist)\n");
}


void
NumericAttribute::fillPostingsFixupEnum(const LoadedEnumAttributeVector &
                                        loaded)
{
    (void) loaded;
    fprintf(stderr, "NumericAttribute::fillPostingsFixupEnum\n");
}

void
NumericAttribute::fixupEnumRefCounts(const EnumVector &enumHist)
{
    (void) enumHist;
    fprintf(stderr, "NumericAttribute::fixupEnumRefCounts\n");
}

}
