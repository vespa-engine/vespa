// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
LOG_SETUP("iteratespeed");

#include <vespa/searchlib/btree/btreeroot.h>
#include <vespa/searchlib/btree/btreebuilder.h>
#include <vespa/searchlib/btree/btreenodeallocator.h>
#include <vespa/searchlib/btree/btree.h>
#include <vespa/searchlib/btree/btreestore.h>
#include <vespa/searchlib/util/rand48.h>
#include <vespa/searchlib/btree/btreenodeallocator.hpp>
#include <vespa/searchlib/btree/btreenode.hpp>
#include <vespa/searchlib/btree/btreenodestore.hpp>
#include <vespa/searchlib/btree/btreeiterator.hpp>
#include <vespa/searchlib/btree/btreeroot.hpp>
#include <vespa/searchlib/btree/btreebuilder.hpp>
#include <vespa/searchlib/btree/btree.hpp>
#include <vespa/searchlib/btree/btreestore.hpp>

#include <vespa/fastos/app.h>
#include <vespa/fastos/timestamp.h>

namespace search {
namespace btree {

enum class IterateMethod
{
    FORWARD,
    BACKWARDS,
    LAMBDA
};

class IterateSpeed : public FastOS_Application
{
    template <typename Traits, IterateMethod iterateMethod>
    void
    workLoop(int loops, bool enableForward, bool enableBackwards,
             bool enableLambda, int leafSlots);
    void usage();
    int Main() override;
};


namespace {

const char *iterateMethodName(IterateMethod iterateMethod)
{
    switch (iterateMethod) {
    case IterateMethod::FORWARD:
        return "forward";
    case IterateMethod::BACKWARDS:
        return "backwards";
    default:
        return "lambda";
    }
}

}

template <typename Traits, IterateMethod iterateMethod>
void
IterateSpeed::workLoop(int loops, bool enableForward, bool enableBackwards,
                       bool enableLambda, int leafSlots)
{
    if ((iterateMethod == IterateMethod::FORWARD && !enableForward) ||
        (iterateMethod == IterateMethod::BACKWARDS && !enableBackwards) ||
        (iterateMethod == IterateMethod::LAMBDA && !enableLambda) ||
        (leafSlots != 0 &&
         leafSlots != static_cast<int>(Traits::LEAF_SLOTS)))
        return;
    vespalib::GenerationHandler g;
    using Tree = BTree<int, int, btree::NoAggregated, std::less<int>, Traits>;
    using Builder = typename Tree::Builder;
    using ConstIterator = typename Tree::ConstIterator;
    Tree tree;
    Builder builder(tree.getAllocator());
    size_t numEntries = 1000000;
    size_t numInnerLoops = 1000;
    for (size_t i = 0; i < numEntries; ++i) {
        builder.insert(i, 0);
    }
    tree.assign(builder);
    assert(numEntries == tree.size());
    assert(tree.isValid());
    for (int l = 0; l < loops; ++l) {
        fastos::TimeStamp before = fastos::ClockSystem::now();
        uint64_t sum = 0;
        for (size_t innerl = 0; innerl < numInnerLoops; ++innerl) {
            if (iterateMethod == IterateMethod::FORWARD) {
                ConstIterator itr(BTreeNode::Ref(), tree.getAllocator());
                itr.begin(tree.getRoot());
                while (itr.valid()) {
                    sum += itr.getKey();
                    ++itr;
                }
            } else if (iterateMethod == IterateMethod::BACKWARDS) {
                ConstIterator itr(BTreeNode::Ref(), tree.getAllocator());
                itr.end(tree.getRoot());
                --itr;
                while (itr.valid()) {
                    sum += itr.getKey();
                    --itr;
                }
            } else {
                tree.getAllocator().foreach_key(tree.getRoot(),
                                                [&](int key) { sum += key; } );
            }
        }
        fastos::TimeStamp after = fastos::ClockSystem::now();
        double used = after.sec() - before.sec();
        printf("Elapsed time for iterating %ld steps is %8.5f, "
               "direction=%s, fanout=%u,%u, sum=%" PRIu64 "\n",
               numEntries * numInnerLoops,
               used,
               iterateMethodName(iterateMethod),
               static_cast<int>(Traits::LEAF_SLOTS),
               static_cast<int>(Traits::INTERNAL_SLOTS),
               sum);
        fflush(stdout);
    }
}


void
IterateSpeed::usage()
{
    printf("iteratspeed "
           "[-F <leafSlots>] "
           "[-b] "
           "[-c <numLoops>] "
           "[-f] "
           "[-l]\n");
}

int
IterateSpeed::Main()
{
    int argi;
    char c;
    const char *optArg;
    argi = 1;
    int loops = 1;
    bool backwards = false;
    bool forwards = false;
    bool lambda = false;
    int leafSlots = 0;
    while ((c = GetOpt("F:bc:fl", optArg, argi)) != -1) {
        switch (c) {
        case 'F':
            leafSlots = atoi(optArg);
            break;
        case 'b':
            backwards = true;
            break;
        case 'c':
            loops = atoi(optArg);
            break;
        case 'f':
            forwards = true;
            break;
        case 'l':
            lambda = true;
            break;
        default:
            usage();
            return 1;
        }
    }
    if (!backwards && !forwards && !lambda) {
        backwards = true;
        forwards = true;
        lambda = true;
    }

    using SmallTraits = BTreeTraits<4, 4, 31, false>;
    using DefTraits = BTreeDefaultTraits;
    using LargeTraits = BTreeTraits<32, 16, 10, true>;
    using HugeTraits = BTreeTraits<64, 16, 10, true>;
    workLoop<SmallTraits, IterateMethod::FORWARD>(loops, forwards, backwards,
                                                  lambda, leafSlots);
    workLoop<DefTraits, IterateMethod::FORWARD>(loops, forwards, backwards,
                                                lambda, leafSlots);
    workLoop<LargeTraits, IterateMethod::FORWARD>(loops, forwards, backwards,
                                                  lambda, leafSlots);
    workLoop<HugeTraits, IterateMethod::FORWARD>(loops, forwards, backwards,
                                                 lambda, leafSlots);
    workLoop<SmallTraits, IterateMethod::BACKWARDS>(loops, forwards, backwards,
                                                    lambda, leafSlots);
    workLoop<DefTraits, IterateMethod::BACKWARDS>(loops, forwards, backwards,
                                                  lambda, leafSlots);
    workLoop<LargeTraits, IterateMethod::BACKWARDS>(loops, forwards, backwards,
                                                    lambda, leafSlots);
    workLoop<HugeTraits, IterateMethod::BACKWARDS>(loops, forwards, backwards,
                                                   lambda, leafSlots);
    workLoop<SmallTraits, IterateMethod::LAMBDA>(loops, forwards, backwards,
                                                 lambda, leafSlots);
    workLoop<DefTraits, IterateMethod::LAMBDA>(loops, forwards, backwards,
                                               lambda, leafSlots);
    workLoop<LargeTraits, IterateMethod::LAMBDA>(loops, forwards, backwards,
                                                 lambda, leafSlots);
    workLoop<HugeTraits, IterateMethod::LAMBDA>(loops, forwards, backwards,
                                                lambda, leafSlots);
    return 0;
}

}
}

FASTOS_MAIN(search::btree::IterateSpeed);


