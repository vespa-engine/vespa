// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "diversity.hpp"
#include "singlenumericattribute.h"
#include <vespa/vespalib/stllike/hash_map.h>

using std::make_unique;
namespace search::attribute::diversity {

template <typename T>
struct FetchNumberFast {
    const T * const attr;
    typedef typename T::LoadedValueType ValueType;
    FetchNumberFast(const IAttributeVector &attr_in) : attr(dynamic_cast<const T *>(&attr_in)) {}
    ValueType get(uint32_t docid) const { return attr->getFast(docid); }
    bool valid() const { return (attr != nullptr); }
};

struct FetchEnumFast {
    IAttributeVector::EnumRefs enumRefs;
    typedef uint32_t ValueType;
    FetchEnumFast(const IAttributeVector &attr) : enumRefs(attr.make_enum_read_view()) {}
    ValueType get(uint32_t docid) const { return enumRefs[docid].load_relaxed().ref(); }
    bool valid() const { return ! enumRefs.empty(); }
};

struct FetchEnum {
    const IAttributeVector *attr;
    typedef uint32_t ValueType;
    FetchEnum(const IAttributeVector & attr_in) : attr(&attr_in) {}
    ValueType get(uint32_t docid) const { return attr->getEnum(docid); }
};

struct FetchInteger {
    const IAttributeVector * attr;
    typedef int64_t ValueType;
    FetchInteger(const IAttributeVector & attr_in) : attr(&attr_in) {}
    ValueType get(uint32_t docid) const { return attr->getInt(docid); }
};

struct FetchFloat {
    const IAttributeVector * attr;
    typedef double ValueType;
    FetchFloat(const IAttributeVector & attr_in) : attr(&attr_in) {}
    ValueType get(uint32_t docid) const { return attr->getFloat(docid); }
};

template <typename Fetcher>
class DiversityFilterT final : public DiversityFilter {
private:
    size_t  _total_count;
    Fetcher _diversity;
    size_t  _max_per_group;
    size_t  _cutoff_max_groups;
    bool    _cutoff_strict;

    typedef vespalib::hash_map<typename Fetcher::ValueType, uint32_t> Diversity;
    Diversity _seen;
public:
    DiversityFilterT(Fetcher diversity, size_t max_per_group, size_t cutoff_max_groups,
                     bool cutoff_strict, size_t max_total)
        : DiversityFilter(max_total), _total_count(0), _diversity(diversity), _max_per_group(max_per_group),
          _cutoff_max_groups(cutoff_max_groups), _cutoff_strict(cutoff_strict),
          _seen(std::min(cutoff_max_groups, 10000ul)*3)
    { }

    bool accepted(uint32_t docId) override;
private:
    bool add() {
        ++_total_count;
        return true;
    }
    bool conditional_add(uint32_t & group_count) {
        if (group_count  < _max_per_group) {
            ++group_count;
            add();
            return true;
        }
        return false;
    }
};

template <typename Fetcher>
bool
DiversityFilterT<Fetcher>::accepted(uint32_t docId) {
    if (_total_count < _max_total) {
        if ((_seen.size() < _cutoff_max_groups) || _cutoff_strict) {
            typename Fetcher::ValueType group = _diversity.get(docId);
            if (_seen.size() < _cutoff_max_groups) {
                return conditional_add(_seen[group]);
            } else {
                auto found = _seen.find(group);
                return (found == _seen.end()) ? add() : conditional_add(found->second);
            }
        } else if ( !_cutoff_strict) {
            return add();
        }
    }
    return false;
}

std::unique_ptr<DiversityFilter>
DiversityFilter::create(const IAttributeVector &diversity_attr, size_t wanted_hits,
                        size_t max_per_group,size_t cutoff_max_groups, bool cutoff_strict)
{
    if (diversity_attr.hasEnum()) { // must handle enum first
        FetchEnumFast fastEnum(diversity_attr);
        if (fastEnum.valid()) {
            return make_unique<DiversityFilterT<FetchEnumFast>> (fastEnum, max_per_group, cutoff_max_groups, cutoff_strict, wanted_hits);
        } else {
            return make_unique<DiversityFilterT<FetchEnum>>(FetchEnum(diversity_attr), max_per_group, cutoff_max_groups, cutoff_strict, wanted_hits);
        }
    } else if (diversity_attr.isIntegerType()) {
        using FetchInt32Fast = FetchNumberFast<SingleValueNumericAttribute<IntegerAttributeTemplate<int32_t> > >;
        using FetchInt64Fast = FetchNumberFast<SingleValueNumericAttribute<IntegerAttributeTemplate<int64_t> > >;

        FetchInt32Fast fastInt32(diversity_attr);
        FetchInt64Fast fastInt64(diversity_attr);
        if (fastInt32.valid()) {
            return make_unique<DiversityFilterT<FetchInt32Fast>>(fastInt32, max_per_group, cutoff_max_groups, cutoff_strict, wanted_hits);
        } else if (fastInt64.valid()) {
            return make_unique<DiversityFilterT<FetchInt64Fast>>(fastInt64, max_per_group, cutoff_max_groups, cutoff_strict, wanted_hits);
        } else {
            return make_unique<DiversityFilterT<FetchInteger>>(FetchInteger(diversity_attr), max_per_group, cutoff_max_groups, cutoff_strict, wanted_hits);
        }
    } else if (diversity_attr.isFloatingPointType()) {
        using FetchFloatFast = FetchNumberFast<SingleValueNumericAttribute<FloatingPointAttributeTemplate<float> > >;
        using FetchDoubleFast = FetchNumberFast<SingleValueNumericAttribute<FloatingPointAttributeTemplate<double> > >;
        FetchFloatFast fastFloat(diversity_attr);
        FetchDoubleFast fastDouble(diversity_attr);
        if (fastFloat.valid()) {
            return make_unique<DiversityFilterT<FetchFloatFast>>(fastFloat, max_per_group, cutoff_max_groups, cutoff_strict, wanted_hits);
        } else if (fastDouble.valid()) {
            return make_unique<DiversityFilterT<FetchDoubleFast>>(fastDouble, max_per_group, cutoff_max_groups, cutoff_strict, wanted_hits);
        } else {
            return make_unique<DiversityFilterT<FetchFloat>>(FetchFloat(diversity_attr), max_per_group, cutoff_max_groups, cutoff_strict, wanted_hits);
        }
    }
    return std::unique_ptr<DiversityFilter>();
}

}
