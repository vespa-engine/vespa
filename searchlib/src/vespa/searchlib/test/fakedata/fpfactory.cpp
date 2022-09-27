// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fpfactory.h"
#include "fakeegcompr64filterocc.h"
#include "fakefilterocc.h"
#include "fakezcbfilterocc.h"
#include "fakezcfilterocc.h"
#include "fakememtreeocc.h"
#include "fakewordset.h"

namespace search::fakedata {

using index::Schema;

FPFactory::~FPFactory() = default;

void
FPFactory::setup(const FakeWordSet &fws)
{
    std::vector<const FakeWord *> v;

    for (const auto& words : fws.words()) {
        for (const auto& word : words) {
            v.push_back(word.get());
        }
    }
    setup(v);
}


void
FPFactory::setup(const std::vector<const FakeWord *> &fws)
{
    (void) fws;
}


typedef std::map<const std::string, FPFactoryMaker *const>
FPFactoryMap;

static FPFactoryMap *fpFactoryMap = nullptr;

/*
 * Posting list factory glue.
 */

FPFactory *
getFPFactory(const std::string &name, const Schema &schema)
{
    if (fpFactoryMap == nullptr)
        return nullptr;

    FPFactoryMap::const_iterator i(fpFactoryMap->find(name));

    if (i != fpFactoryMap->end())
        return i->second(schema);
    else
        return nullptr;
}


std::vector<std::string>
getPostingTypes()
{
    std::vector<std::string> res;

    if (fpFactoryMap != nullptr)
        for (FPFactoryMap::const_iterator i(fpFactoryMap->begin());
             i != fpFactoryMap->end();
             ++i)
            res.push_back(i->first);
    return res;
}


FPFactoryInit::FPFactoryInit(const FPFactoryMapEntry &fpFactoryMapEntry)
    : _key(fpFactoryMapEntry.first)
{
    if (fpFactoryMap == nullptr)
        fpFactoryMap = new FPFactoryMap;
    fpFactoryMap->insert(fpFactoryMapEntry);
}

FPFactoryInit::~FPFactoryInit()
{
    assert(fpFactoryMap != nullptr);
    size_t eraseRes = fpFactoryMap->erase(_key);
    assert(eraseRes == 1);
    (void) eraseRes;
    if (fpFactoryMap->empty()) {
        delete fpFactoryMap;
        fpFactoryMap = nullptr;
    }
}

void
FPFactoryInit::forceLink()
{
    FakeEGCompr64FilterOcc::forceLink();
    FakeFilterOcc::forceLink();
    FakeZcbFilterOcc::forceLink();
    FakeZcFilterOcc::forceLink();
    FakeMemTreeOcc::forceLink();
}

}
