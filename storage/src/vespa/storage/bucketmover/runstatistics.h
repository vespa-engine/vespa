// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::bucketmover::RunStatistics
 * \ingroup bucketmover
 *
 * \brief Statistics gathered from a bucket mover cycle.
 */

#pragma once

#define MATRIX_PRINT(desc, var, rs) \
{ \
    bool anyset = false; \
    for (uint32_t i=0; i<(rs)._diskData.size(); ++i) { \
        for (uint32_t j=0; j<(rs)._diskData.size(); ++j) { \
            anyset |= ((rs)._diskData[i][j].var > 0); \
        } \
    } \
    if (anyset) { \
        out << "<h4>" << desc << "</h4>\n"; \
        HtmlTable matrixTable("Source \\ Target"); \
        using LCUP = std::unique_ptr<LongColumn>; \
        std::vector<LCUP> matrixData((rs)._diskData.size()); \
        for (uint32_t i=0; i<(rs)._diskData.size(); ++i)  { \
            std::ostringstream index; \
            index << "Disk " << i; \
            matrixData[i].reset(new LongColumn(index.str(), "", &matrixTable));\
            matrixTable.addRow(index.str()); \
        } \
        for (uint32_t i=0; i<(rs)._diskData.size(); ++i)  { \
            for (uint32_t j=0; j<(rs)._diskData.size(); ++j)  { \
                (*matrixData[j])[i] = (rs)._diskData[i][j].var; \
            } \
        } \
        matrixTable.print(out); \
    } \
}

#include <vespa/storageframework/storageframework.h>
#include <vespa/vdslib/state/nodestate.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/util/printable.h>
#include <vector>

namespace storage {

class Clock;

namespace bucketmover {

struct RunStatistics : public document::Printable {
    using DiskDistribution = lib::Distribution::DiskDistribution;

    /** Data kept as targets for moves for each disk. */
    struct DiskMatrix {
        uint32_t _bucketsMoved;
        uint32_t _bucketsFailedMoving;
        uint32_t _bucketsLeftOnWrongDisk;
        uint32_t _bucketsNotFoundAtExecutionTime;

        DiskMatrix();
    };

    /** Data kept per disk. */
    struct DiskData {
        std::vector<DiskMatrix> _targetDisks;
        uint32_t _bucketsFoundOnCorrectDisk;
        uint64_t _bucketSize;
        bool _diskDisabled;

        DiskData(uint16_t diskCount);

        DiskMatrix& operator[](uint16_t index) { return _targetDisks[index]; }
        const DiskMatrix& operator[](uint16_t index) const
            { return _targetDisks[index]; }
        double getWronglyPlacedRatio() const;
    };

    framework::Clock* _clock;
    DiskDistribution _distribution;
    document::BucketId _lastBucketProcessed;
    document::BucketId _lastBucketVisited; // Invalid bucket for starting point
    std::vector<DiskData> _diskData;
    framework::SecondTime _startTime;
    framework::SecondTime _endTime;
    framework::SecondTime _lastBucketProcessedTime;

    RunStatistics(DiskDistribution, framework::Clock&, const lib::NodeState&);

    double getWronglyPlacedRatio() const;
    double getProgress() const;
    uint64_t getBucketCount(uint16_t disk, bool includeWrongLocation) const;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

} // bucketmover
} // storage
