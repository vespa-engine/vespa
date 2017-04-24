// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "runstatistics.h"
#include "htmltable.h"

namespace storage {
namespace bucketmover {

RunStatistics::DiskMatrix::DiskMatrix()
    : _bucketsMoved(0),
      _bucketsFailedMoving(0),
      _bucketsLeftOnWrongDisk(0),
      _bucketsNotFoundAtExecutionTime(0)
{
}

RunStatistics::DiskData::DiskData(uint16_t diskCount)
    : _targetDisks(diskCount),
      _bucketsFoundOnCorrectDisk(0),
      _bucketSize(0),
      _diskDisabled(false)
{
}

double
RunStatistics::DiskData::getWronglyPlacedRatio() const
{
    uint64_t wrong = 0;
    for (uint32_t i=0; i<_targetDisks.size(); ++i) {
        wrong += _targetDisks[i]._bucketsLeftOnWrongDisk
               + _targetDisks[i]._bucketsFailedMoving;
    }
    uint64_t total = wrong + _bucketsFoundOnCorrectDisk;
    return static_cast<double>(wrong) / total;
}

RunStatistics::RunStatistics(DiskDistribution d, framework::Clock& clock,
                             const lib::NodeState& ns)
    : _clock(&clock),
      _distribution(d),
      _lastBucketProcessed(0),
      _lastBucketVisited(0),
      _diskData(ns.getDiskCount(), DiskData(ns.getDiskCount())),
      _startTime(_clock->getTimeInSeconds()),
      _endTime(0),
      _lastBucketProcessedTime(0)
{
    for (uint32_t i=0; i<ns.getDiskCount(); ++i) {
        if (!ns.getDiskState(i).getState().oneOf("uis")) {
            _diskData[i]._diskDisabled = true;
        }
    }
}

void
RunStatistics::print(std::ostream& out, bool verbose,
                     const std::string& ind) const
{
    (void) verbose; (void) ind;
    bool completed(_endTime.isSet());
    framework::SecondTime currentTime = _clock->getTimeInSeconds();
    if (completed) {
        out << "<h3>Run from " << _startTime << " to " << _endTime;
    } else {
        out << "<h3>Run started "
            << currentTime.getDiff(_startTime).toString(framework::DIFFERENCE)
            << " ago";
    }
    out << " with distribution "
        << lib::Distribution::getDiskDistributionName(_distribution)
        << "</h3>\n<blockquote>";
    if (!completed) {
        std::ostringstream progress;
        progress << std::fixed << std::setprecision(4)
                 << (100.0 * getProgress());
        out << "<p>Progress: " << progress.str() << " % &nbsp; &nbsp;";
        if (_lastBucketProcessedTime.isSet()) {
            out << "<font color=\"gray\" size=\"-1\">Last move for "
                << _lastBucketProcessed << " "
                << currentTime.getDiff(_lastBucketProcessedTime)
                        .toString(framework::DIFFERENCE)
                << " ago</font>";
        }
        out << "</p>\n";
    }

    HtmlTable table("Disk");
    table.addColumnHeader(completed ? "Buckets in directory after run"
                                    : "Processed buckets in directory", 2);
    LongColumn bucketCount("Count", "", &table);
    PercentageColumn bucketCountPart("Part", 0, &table);

    table.addColumnHeader(completed
            ? "Total document size in directory after run"
            : "Total document size of processed buckets in directory", 2);
    ByteSizeColumn documentSize("Size", &table);
    PercentageColumn documentSizePart("Part", 0, &table);

    table.addColumnHeader(completed ? "Buckets on correct disk after run"
                                    : "Processed buckets on correct disk", 2);
    LongColumn bucketsCorrectDisk("Count", "", &table);
    DoubleColumn bucketsCorrectDiskPart("Part", " %", &table);
    bucketsCorrectDiskPart.setTotalAsAverage();
    bucketsCorrectDiskPart.addColorLimit(95, Column::LIGHT_YELLOW);
    bucketsCorrectDiskPart.addColorLimit(100, Column::LIGHT_GREEN);

    for (uint32_t i=0; i<_diskData.size(); ++i) {
        table.addRow(i);
        if (_diskData[i]._diskDisabled) {
            table.setRowHeaderColor(Column::LIGHT_RED);
        }

        bucketCount[i] = getBucketCount(i, true);
        bucketCountPart[i] = bucketCount[i];

        documentSize[i] = _diskData[i]._bucketSize;
        documentSizePart[i] = documentSize[i];

        bucketsCorrectDisk[i] = getBucketCount(i, false);
        bucketsCorrectDiskPart[i]
                = 100.0 * getBucketCount(i, false) / getBucketCount(i, true);
    }
    table.addTotalRow("Total");
    table.print(out);

    MATRIX_PRINT("Buckets left on wrong disk", _bucketsLeftOnWrongDisk, *this);
    MATRIX_PRINT("Buckets moved", _bucketsMoved, *this);
    MATRIX_PRINT("Buckets not found at move time",
                 _bucketsNotFoundAtExecutionTime, *this);
    MATRIX_PRINT("Buckets failed moving for other reasons",
                 _bucketsFailedMoving, *this);

    out << "</blockquote>\n";
}

double
RunStatistics::getWronglyPlacedRatio() const
{
    uint64_t wrong = 0, total = 0;
    for (uint32_t i=0; i<_diskData.size(); ++i) {
        for (uint32_t j=0; j<_diskData.size(); ++j) {
            wrong += _diskData[i][j]._bucketsLeftOnWrongDisk
                   + _diskData[i][j]._bucketsFailedMoving;
        }
        total += _diskData[i]._bucketsFoundOnCorrectDisk;
    }
    total += wrong;
    return static_cast<double>(wrong) / total;
}

double
RunStatistics::getProgress() const
{
    if (_endTime.isSet()) return 1.0;
    double result = 0;
    double weight = 0.5;
    uint64_t key = _lastBucketProcessed.toKey();
    for (uint16_t i=0; i<64; ++i) {
        uint64_t flag = uint64_t(1) << (63 - i);
        if ((key & flag) == flag) {
            result += weight;
        }
        weight /= 2;
    }
    return result;
}

uint64_t
RunStatistics::getBucketCount(uint16_t disk, bool includeWrongLocation) const
{
    uint64_t total = 0;
    for (uint32_t i=0; i<_diskData.size(); ++i) {
        if (disk == i) total += _diskData[i]._bucketsFoundOnCorrectDisk;
        for (uint32_t j=0; j<_diskData.size(); ++j) {
            if (disk == i) {
                if (includeWrongLocation) {
                    total += _diskData[i][j]._bucketsLeftOnWrongDisk;
                    total += _diskData[i][j]._bucketsFailedMoving;
                }
            } else if (disk == j) {
                total += _diskData[i][j]._bucketsMoved;
            }
        }
    }
    return total;
}

} // bucketmover
} // storage
