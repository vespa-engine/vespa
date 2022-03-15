// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "searchiterator.h"
#include <vespa/vespalib/objects/objectvisitor.h>
#include <stack>

namespace search::queryeval {

/**
 * Search iterator that monitors an underlying search iterator
 * and at the end provides statastics on the following:
 *  - number of seeks
 *  - number of unpacks
 *  - average docid step size
 *  - average hit skip size
 *  - number of seeks per hit
 */
class MonitoringSearchIterator : public SearchIterator
{
public:
    class Stats
    {
    private:
        uint32_t _numSeeks;
        uint32_t _numUnpacks;
        uint64_t _numDocIdSteps;
        uint64_t _numHitSkips;
        double divide(double dividend, double divisor) const {
            return divisor > 0.0 ? dividend / divisor : 0.0;
        }
    public:
        Stats();
        void seek() { ++_numSeeks; }
        void step(uint32_t docIdDiff) { _numDocIdSteps += docIdDiff; }
        void skip(uint32_t hitDiff) { _numHitSkips += hitDiff; }
        void unpack() { ++_numUnpacks; }
        uint32_t getNumSeeks() const { return _numSeeks; }
        uint32_t getNumUnpacks() const { return _numUnpacks; }
        double getNumSeeksPerUnpack() const { return divide(getNumSeeks(), getNumUnpacks()); }
        uint64_t getNumDocIdSteps() const { return _numDocIdSteps; }
        double getAvgDocIdSteps() const { return divide(getNumDocIdSteps(), getNumSeeks()); }
        uint64_t getNumHitSkips() const { return _numHitSkips; }
        double getAvgHitSkips() const { return divide(getNumHitSkips(), getNumSeeks()); }
    };

    class Dumper : public vespalib::ObjectVisitor
    {
    private:
        enum StructType {
            ITERATOR,
            STATS,
            CHILDREN,
            UNKNOWN
        };

        int                    _indent;
        uint32_t               _textFormatWidth;
        uint32_t               _intFormatWidth;
        uint32_t               _floatFormatWidth;
        uint32_t               _floatFormatPrecision;
        vespalib::string       _str;
        int                    _currIndent;
        std::stack<StructType> _stack;

        void addIndent();
        void addText(const vespalib::string &value);
        void addInt(int64_t value, const vespalib::string &desc);
        void addFloat(double value, const vespalib::string &desc);
        void openScope();
        void closeScope();

    public:
        Dumper(int indent = 4,
               uint32_t textFormatWidth = 1,
               uint32_t intFormatWidth = 1,
               uint32_t floatFormatWidth = 1,
               uint32_t floatFormatPrecision = 2);
        ~Dumper();

        vespalib::string toString() const { return _str; }

        void openStruct(const vespalib::string &name, const vespalib::string &type) override;
        void closeStruct() override;
        void visitBool(const vespalib::string &name, bool value) override;
        void visitInt(const vespalib::string &name, int64_t value) override;
        void visitFloat(const vespalib::string &name, double value) override;
        void visitString(const vespalib::string &name, const vespalib::string &value) override;
        void visitNull(const vespalib::string &name) override;
        void visitNotImplemented() override;
    };

    typedef std::unique_ptr<MonitoringSearchIterator> UP;

private:
    const vespalib::string   _name;
    const SearchIterator::UP _search;
    const bool               _collectHitSkipStats;
    Stats                    _stats;

    uint32_t countHitSkips(uint32_t docId);

public:
    MonitoringSearchIterator(const vespalib::string &name,
                             SearchIterator::UP search,
                             bool collectHitSkipStats);
    ~MonitoringSearchIterator();

    // Overrides SearchIterator
    void doSeek(uint32_t docId) override;
    void doUnpack(uint32_t docId) override;
    void initRange(uint32_t beginid, uint32_t endid) override {
        _search->initRange(beginid, endid);
        SearchIterator::initRange(_search->getDocId()+1, _search->getEndId());
    }
    Trinary is_strict() const override { return _search->is_strict(); }
    const PostingInfo *getPostingInfo() const override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;

    const SearchIterator &getIterator() const { return *_search; }
    const Stats &getStats() const { return _stats; }
};

}
