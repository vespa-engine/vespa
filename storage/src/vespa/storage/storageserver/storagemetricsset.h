// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/metrics.h>
#include <vespa/document/fieldvalue/serializablearray.h>

namespace storage {

class MessageMemoryUseMetricSet : public metrics::MetricSet
{
public:
    metrics::LongValueMetric total;
    metrics::LongValueMetric lowpri;
    metrics::LongValueMetric normalpri;
    metrics::LongValueMetric highpri;
    metrics::LongValueMetric veryhighpri;

    MessageMemoryUseMetricSet(metrics::MetricSet* owner)
        : metrics::MetricSet("message_memory_use", "memory",
                "Message use from storage messages", owner),
          total("total", "memory",
                "Message use from storage messages", this),
          lowpri("lowpri", "memory",
                "Message use from low priority storage messages", this),
          normalpri("normalpri", "memory",
                "Message use from normal priority storage messages", this),
          highpri("highpri", "memory",
                "Message use from high priority storage messages", this),
          veryhighpri("veryhighpri", "memory",
                "Message use from very high priority storage messages", this)
    {
    }

};

struct DocumentSerializationMetricSet : public metrics::MetricSet
{
    metrics::LongCountMetric usedCachedSerializationCount;
    metrics::LongCountMetric compressedDocumentCount;
    metrics::LongCountMetric compressionDidntHelpCount;
    metrics::LongCountMetric uncompressableCount;
    metrics::LongCountMetric serializedUncompressed;
    metrics::LongCountMetric inputWronglySerialized;

    DocumentSerializationMetricSet(metrics::MetricSet* owner)
        : metrics::MetricSet("document_serialization", "docserialization",
                "Counts of document serialization of various types", owner),
          usedCachedSerializationCount(
                "cached_serialization_count", "docserialization",
                "Number of times we didn't need to serialize the document as "
                "we already had serialized version cached", this),
          compressedDocumentCount(
                "compressed_serialization_count", "docserialization",
                "Number of times we compressed document when serializing",
                this),
          compressionDidntHelpCount(
                "compressed_didnthelp_count", "docserialization",
                "Number of times we compressed document when serializing, but "
                "the compressed version was bigger, so it was dumped", this),
          uncompressableCount(
                "uncompressable_serialization_count", "docserialization",
                "Number of times we didn't attempt compression as document "
                "had already been tagged uncompressable", this),
          serializedUncompressed(
                "uncompressed_serialization_count", "docserialization",
                "Number of times we serialized a document uncompressed", this),
          inputWronglySerialized(
                "input_wrongly_serialized_count", "docserialization",
                "Number of times we reserialized a document because the "
                "compression it had in cache did not match what was configured",
                this)
    {
    }

};

struct StorageMetricSet : public metrics::MetricSet
{
    metrics::LongValueMetric memoryUse;
    MessageMemoryUseMetricSet memoryUse_messages;
    metrics::LongValueMetric memoryUse_visiting;
    DocumentSerializationMetricSet documentSerialization;

    StorageMetricSet()
        : metrics::MetricSet("server", "memory",
              "Metrics for VDS applications"),
          memoryUse("memoryusage", "memory", "", this),
          memoryUse_messages(this),
          memoryUse_visiting("memoryusage_visiting", "memory",
                "Message use from visiting", this),
          documentSerialization(this)
    {
    }

    void updateMetrics() {
        document::SerializableArray::Statistics stats(
                document::SerializableArray::getStatistics());

        documentSerialization.usedCachedSerializationCount.set(
                stats._usedCachedSerializationCount);
        documentSerialization.compressedDocumentCount.set(
                stats._compressedDocumentCount);
        documentSerialization.compressionDidntHelpCount.set(
                stats._compressionDidntHelpCount);
        documentSerialization.uncompressableCount.set(
                stats._uncompressableCount);
        documentSerialization.serializedUncompressed.set(
                stats._serializedUncompressed);
        documentSerialization.inputWronglySerialized.set(
                stats._inputWronglySerialized);
    }
};

} // storage

