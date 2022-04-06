// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/btree/btreeroot.h>
#include <vespa/vespalib/btree/btreebuilder.h>
#include <vespa/vespalib/btree/btreenodeallocator.h>
#include <vespa/vespalib/btree/btree.h>
#include <vespa/vespalib/btree/btreestore.h>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/btree/btreenodestore.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreebuilder.hpp>
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/datastore/buffer_type.hpp>
#include <vespa/vespalib/util/time.h>
#include <vector>

using vespalib::btree::BTree;
using vespalib::btree::BTreeNode;
using vespalib::btree::BTreeTraits;

enum class ScanMethod
{
    ITERATOR,
    FUNCTOR
};

class ScanSpeed
{
    template <typename Traits>
    void work_loop(ScanMethod scan_method);
public:
    int main();
};


namespace {

const char *scan_method_name(ScanMethod scan_method)
{
    switch (scan_method) {
    case ScanMethod::ITERATOR:
        return "iterator";
    default:
        return "functor";
    }
}

class ScanOnce {
public:
    virtual ~ScanOnce() = default;
    virtual void operator()(std::vector<bool>& bv) = 0;
};

template <typename Tree>
class ScanTree : public ScanOnce {
protected:
    const Tree &_tree;
    int _startval;
    int _endval;
public:
    ScanTree(const Tree &tree, int startval, int endval)
        : _tree(tree),
          _startval(startval),
          _endval(endval)
    {
    }
    ~ScanTree() override { }
};

template <typename Tree>
class ScanWithIterator : public ScanTree<Tree> {
public:
    ScanWithIterator(const Tree &tree, int startval, int endval)
        : ScanTree<Tree>(tree, startval, endval)
    {
    }
    ~ScanWithIterator() override = default;
    void operator()(std::vector<bool>& bv) override;
};

template <typename Tree>
void
ScanWithIterator<Tree>::operator()(std::vector<bool>& bv)
{
    using ConstIterator = typename Tree::ConstIterator;
    ConstIterator itr(BTreeNode::Ref(), this->_tree.getAllocator());
    itr.lower_bound(this->_tree.getRoot(), this->_startval);
    while (itr.valid() && itr.getKey() < this->_endval) {
        bv[itr.getKey()] = true;
        ++itr;
    }
}

template <typename Tree>
class ScanWithFunctor : public ScanTree<Tree> {
    
public:
    ScanWithFunctor(const Tree &tree, int startval, int endval)
        : ScanTree<Tree>(tree, startval, endval)
    {
    }
    ~ScanWithFunctor() override = default;
    void operator()(std::vector<bool>& bv) override;
};

template <typename Tree>
void
ScanWithFunctor<Tree>::operator()(std::vector<bool>& bv)
{
    using ConstIterator = typename Tree::ConstIterator;
    ConstIterator start(BTreeNode::Ref(), this->_tree.getAllocator());
    ConstIterator end(BTreeNode::Ref(), this->_tree.getAllocator());
    start.lower_bound(this->_tree.getRoot(), this->_startval);
    end.lower_bound(this->_tree.getRoot(), this->_endval);
    start.foreach_key_range(end, [&](int key) { bv[key] = true; } );
}

}

template <typename Traits>
void
ScanSpeed::work_loop(ScanMethod scan_method)
{
    vespalib::GenerationHandler g;
    using Tree = BTree<int, int, vespalib::btree::NoAggregated, std::less<int>, Traits>;
    using Builder = typename Tree::Builder;
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
    std::unique_ptr<ScanOnce> scan_once;
    if (scan_method == ScanMethod::ITERATOR) {
        scan_once = std::make_unique<ScanWithIterator<Tree>>(tree, 4, numEntries - 4);
    } else {
        scan_once = std::make_unique<ScanWithFunctor<Tree>>(tree, 4, numEntries - 4);
    }
    auto bv = std::make_unique<std::vector<bool>>(numEntries);
    vespalib::Timer timer;
    for (size_t innerl = 0; innerl < numInnerLoops; ++innerl) {
        (*scan_once)(*bv);
    }
    double used = vespalib::to_s(timer.elapsed());
    printf("Elapsed time for scanning %ld entries is %8.5f, "
           "scanmethod=%s, fanout=%u,%u\n",
           numEntries * numInnerLoops,
           used,
           scan_method_name(scan_method),
           static_cast<int>(Traits::LEAF_SLOTS),
           static_cast<int>(Traits::INTERNAL_SLOTS));
    fflush(stdout);
}


int
ScanSpeed::main()
{
    using SmallTraits = BTreeTraits<4, 4, 31, false>;
    using DefTraits = vespalib::btree::BTreeDefaultTraits;
    using LargeTraits = BTreeTraits<32, 16, 10, true>;
    using HugeTraits = BTreeTraits<64, 16, 10, true>;
    work_loop<SmallTraits>(ScanMethod::ITERATOR);
    work_loop<DefTraits>(ScanMethod::ITERATOR);
    work_loop<LargeTraits>(ScanMethod::ITERATOR);
    work_loop<HugeTraits>(ScanMethod::ITERATOR);
    work_loop<SmallTraits>(ScanMethod::FUNCTOR);
    work_loop<DefTraits>(ScanMethod::FUNCTOR);
    work_loop<LargeTraits>(ScanMethod::FUNCTOR);
    work_loop<HugeTraits>(ScanMethod::FUNCTOR);
    return 0;
}

int main(int, char **) {
    ScanSpeed app;
    return app.main();
}
