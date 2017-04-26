// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "bucketvector.h"
#include <limits>
#include <iostream>
#include <algorithm>

namespace BucketVector{

typedef document::BucketId::Type BidType;

std::vector<BidType> v;
uint64_t countBitsMask = 0x03FFFFFFFFFFFFFF;


void reserve(size_t n)
{
    v.reserve(n);
}

void clear()
{
    v.clear();
}

void addBucket(uint64_t bucket)
{
    BidType b = document::BucketId::bucketIdToKey(bucket & countBitsMask);
    v.push_back(b);
}

inline uint32_t getMax(uint32_t a, uint32_t b, uint32_t c){
    if(a > b){
        return (a>c)?a:c;
    }
    return (b>c)?b:c;
}

inline uint64_t getCountBitsMask(uint32_t countBits)
{
    uint64_t shift = 8 * sizeof(BidType) - countBits;
    uint64_t mask = std::numeric_limits<uint64_t>::max() << shift;
    mask = mask >> shift;
    return mask;
}


inline uint32_t getLSBdiff(BidType a, BidType b)
{
    uint64_t mask = 0x0000000000000001;
    uint32_t c = 1;

    while((a&mask) == (b&mask)){
        c++;
        mask <<= 1;
    }
    return c;
}

void getBuckets(uint32_t distributionBits, std::vector<document::BucketId>& buckets){

    if(v.size() == 0){
        return;
    }

    std::sort(v.begin(), v.end());

    uint32_t prevMSB = 1; //number of common MSB bits between current and previous bucket
    uint32_t nextMSB;     //number of common MSB bits between current and next bucket

    size_t i=0;
    for(; i<v.size()-1;i++){
        if(v[i] == v[i+1]){
            std::cerr << "identical buckets...bypassing\n";
            continue;
        }
        nextMSB = getLSBdiff(document::BucketId::keyToBucketId(v[i]),
                             document::BucketId::keyToBucketId(v[i+1]));
        BidType b = document::BucketId::keyToBucketId(v[i]);
        uint32_t countBits = getMax(prevMSB, nextMSB, distributionBits);
        uint64_t mask = getCountBitsMask(countBits);
        buckets.push_back(document::BucketId(countBits, b&mask));
        prevMSB = nextMSB;
    }
    nextMSB = 1;
    uint32_t countBits = getMax(prevMSB, nextMSB, distributionBits);
    uint64_t mask = getCountBitsMask(countBits);
    BidType b = document::BucketId::keyToBucketId(v[i]);
    buckets.push_back(document::BucketId(countBits, b&mask));
}


void printVector()
{
    for (size_t i=0; i < v.size(); i++){
        std::cout << " " << v[i] << " " << document::BucketId::keyToBucketId(v[i]) << "\n";
    }
}



}


