// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.model.content.DispatchTuning;

import static com.yahoo.text.Lowercase.toLowerCase;

/**
 * Class representing the tuning config used for a search cluster.
 * Take a look at proton.def and vespa doc for detailed explanations.
 *
 * @author geirst
 */
public class Tuning extends AnyConfigProducer implements ProtonConfig.Producer {

    public static class SearchNode implements ProtonConfig.Producer {

        public enum IoType {
            NORMAL("NORMAL"),
            DIRECTIO("DIRECTIO"),
            MMAP("MMAP"),
            POPULATE("POPULATE");

            public final String name;

            IoType(String name) {
                this.name = name;
            }

            public static IoType fromString(String name) {
                for (IoType type : IoType.values()) {
                    if (toLowerCase(name).equals(toLowerCase(type.name))) {
                        return type;
                    }
                }
                return NORMAL;
            }
        }

        public static class RequestThreads implements ProtonConfig.Producer {
            public Integer numSearchThreads = null;
            public Integer numThreadsPerSearch = null;
            public Integer numSummaryThreads = null;

            @Override
            public void getConfig(ProtonConfig.Builder builder) {
                if (numSearchThreads!=null) builder.numsearcherthreads(numSearchThreads);
                if (numThreadsPerSearch!=null) builder.numthreadspersearch(numThreadsPerSearch);
                if (numSummaryThreads!=null) builder.numsummarythreads(numSummaryThreads);
            }
        }

        public static class RemovedDB implements ProtonConfig.Producer {

            public static class Prune implements ProtonConfig.Producer {
                public Double age = null;
                public Double interval = null;

                @Override
                public void getConfig(ProtonConfig.Builder builder) {
                    if (age != null) builder.pruneremoveddocumentsage(age);
                    if (interval != null) builder.pruneremoveddocumentsinterval(interval);
                }
            }

            public Prune prune;
            @Override
            public void getConfig(ProtonConfig.Builder builder) {
                if (prune != null) prune.getConfig(builder);
            }
        }

        public static class FlushStrategy implements ProtonConfig.Producer {
            public Long totalMaxMemoryGain = null;
            public Double totalDiskBloatFactor = null;
            public Long componentMaxMemoryGain = null;
            public Double componentDiskBloatFactor = null;
            public Double componentMaxage = null;
            public Long transactionLogMaxSize = null;
            public Double conservativeMemoryLimitFactor = null;
            public Double conservativeDiskLimitFactor = null;

            @Override
            public void getConfig(ProtonConfig.Builder builder) {
                // Here, the config building gets very ugly, because we have to check for null because of autoconversion Long/long etc.

                ProtonConfig.Flush.Memory.Builder memoryBuilder = builder.flush.memory;
                if (totalMaxMemoryGain != null) memoryBuilder.maxmemory(totalMaxMemoryGain);
                if (totalDiskBloatFactor != null) memoryBuilder.diskbloatfactor(totalDiskBloatFactor);
                if (transactionLogMaxSize != null) memoryBuilder.maxtlssize(transactionLogMaxSize);

                ProtonConfig.Flush.Memory.Each.Builder eachBuilder = memoryBuilder.each;
                if (componentMaxMemoryGain != null) eachBuilder.maxmemory(componentMaxMemoryGain);
                if (componentDiskBloatFactor != null) eachBuilder.diskbloatfactor(componentDiskBloatFactor);

                ProtonConfig.Flush.Memory.Maxage.Builder maxageBuilder = memoryBuilder.maxage;
                if (componentMaxage != null) maxageBuilder.time(componentMaxage);

                ProtonConfig.Flush.Memory.Conservative.Builder conservativeBuilder = memoryBuilder.conservative;
                if (conservativeMemoryLimitFactor != null) {
                    conservativeBuilder.memorylimitfactor(conservativeMemoryLimitFactor);
                }
                if (conservativeDiskLimitFactor != null) {
                    conservativeBuilder.disklimitfactor(conservativeDiskLimitFactor);
                }
            }
        }

        public static class Resizing implements ProtonConfig.Producer {
            public Integer initialDocumentCount = null;
            public Integer amortizeCount = null;

            @Override
            public void getConfig(ProtonConfig.Builder builder) {
                if (initialDocumentCount!=null) {
                    for (ProtonConfig.Documentdb.Builder db : builder.documentdb) {
                        db.allocation.initialnumdocs(initialDocumentCount);
                    }
                }
                if (amortizeCount !=null) {
                    for (ProtonConfig.Documentdb.Builder db : builder.documentdb) {
                        db.allocation.amortizecount(amortizeCount);
                    }
                }
            }

        }

        public static class Index implements ProtonConfig.Producer {
            public static class Io implements ProtonConfig.Producer {
                public IoType write = null;
                public IoType read = null;
                public IoType search = null;

                @Override
                public void getConfig(ProtonConfig.Builder builder) {
                    if (write != null) {
                        builder.indexing.write.io(ProtonConfig.Indexing.Write.Io.Enum.valueOf(write.name));
                    }
                    if (read != null) {
                        builder.indexing.read.io(ProtonConfig.Indexing.Read.Io.Enum.valueOf(read.name));
                    }
                    if (search != null) {
                        if (search.equals(IoType.POPULATE)) {
                            builder.search.mmap.options.add(ProtonConfig.Search.Mmap.Options.POPULATE);
                        }
                    }
                }
            }
            public static class Warmup implements ProtonConfig.Producer {
                public double time = 0;
                public boolean unpack = false;

                @Override
                public void getConfig(ProtonConfig.Builder builder) {
                    if (time > 0) {
                        builder.index.warmup.time(time);
                        builder.index.warmup.unpack(unpack);
                    }
                }

            }

            public Io io;
            public Warmup warmup;

            @Override
            public void getConfig(ProtonConfig.Builder builder) {
                if (io != null) io.getConfig(builder);
                if (warmup != null) warmup.getConfig(builder);
            }
        }

        public static class Attribute implements ProtonConfig.Producer {
            public static class Io implements ProtonConfig.Producer {
                public IoType write = null;

                public Io() {}

                @Override
                public void getConfig(ProtonConfig.Builder builder) {
                    if (write != null) {
                        builder.attribute.write.io(ProtonConfig.Attribute.Write.Io.Enum.valueOf(write.name));
                    }
                }
            }

            public Io io;

            @Override
            public void getConfig(ProtonConfig.Builder builder) {
                if (io != null) io.getConfig(builder);
            }

        }

        public static class Summary implements ProtonConfig.Producer {
            public static class Io {
                public IoType write = null;
                public IoType read = null;

                public void getConfig(ProtonConfig.Summary.Builder builder) {
                    if (write != null) {
                        builder.write.io(ProtonConfig.Summary.Write.Io.Enum.valueOf(write.name));
                    }
                    if (read != null) {
                        if (read.equals(IoType.POPULATE)) {
                            builder.read.io(ProtonConfig.Summary.Read.Io.MMAP);
                            builder.read.mmap.options.add(ProtonConfig.Summary.Read.Mmap.Options.POPULATE);
                        } else {
                            builder.read.io(ProtonConfig.Summary.Read.Io.Enum.valueOf(read.name));
                        }
                    }
                }
            }

            public static class Store {
                public static class Compression {
                    public enum Type {
                        NONE("NONE"),
                        ZSTD("ZSTD"),
                        LZ4("LZ4");

                        public final String name;

                        Type(String name) {
                            this.name = name;
                        }
                        public static Type fromString(String name) {
                            for (Type type : Type.values()) {
                                if (toLowerCase(name).equals(toLowerCase(type.name))) {
                                    return type;
                                }
                            }
                            return NONE;
                        }
                    }
                    public Type type = null;
                    public Integer level = null;

                    public void getConfig(ProtonConfig.Summary.Cache.Compression.Builder compression) {
                        if (type != null) compression.type(ProtonConfig.Summary.Cache.Compression.Type.Enum.valueOf(type.name));
                        if (level != null) compression.level(level);
                    }

                    public void getConfig(ProtonConfig.Summary.Log.Compact.Compression.Builder compression) {
                        if (type != null) compression.type(ProtonConfig.Summary.Log.Compact.Compression.Type.Enum.valueOf(type.name));
                        if (level != null) compression.level(level);
                    }

                    public void getConfig(ProtonConfig.Summary.Log.Chunk.Compression.Builder compression) {
                        if (type != null) compression.type(ProtonConfig.Summary.Log.Chunk.Compression.Type.Enum.valueOf(type.name));
                        if (level != null) compression.level(level);
                    }
                }

                public static class Component {
                    public Long maxSize = null;
                    public Double maxSizePercent = null;
                    public Long initialEntries = null;
                    public Compression compression = null;
                    private final boolean outputInt;

                    public Component() {
                        this.outputInt = false;
                    }

                    public Component(boolean outputInt) {
                        this.outputInt = outputInt;
                    }

                    public void getConfig(ProtonConfig.Summary.Cache.Builder cache) {
                        if (outputInt) {
                            if (maxSizePercent !=null) cache.maxbytes(-maxSizePercent.longValue());
                            if (maxSize!=null) cache.maxbytes(maxSize.intValue());
                            if (initialEntries!=null) cache.initialentries(initialEntries.intValue());
                        } else {
                            if (maxSizePercent !=null) cache.maxbytes(-maxSizePercent.longValue());
                            if (maxSize!=null) cache.maxbytes(maxSize);
                            if (initialEntries!=null) cache.initialentries(initialEntries);
                        }
                        if (compression != null) {
                            compression.getConfig(cache.compression);
                        }
                    }

                    public void getConfig(ProtonConfig.Summary.Log.Compact.Builder compact) {
                        if (compression != null) {
                            compression.getConfig(compact.compression);
                        }
                    }

                    public void getConfig(ProtonConfig.Summary.Log.Chunk.Builder chunk) {
                        if (outputInt) {
                            if (maxSize != null) chunk.maxbytes(maxSize.intValue());
                        } else {
                            throw new IllegalStateException("Fix this, chunk does not have long types");
                        }
                        if (compression != null) {
                            compression.getConfig(chunk.compression);
                        }
                    }
                }

                public static class LogStore {

                    public Long maxFileSize = null;
                    public Component chunk = null;
                    public Double minFileSizeFactor = null;

                    public void getConfig(ProtonConfig.Summary.Log.Builder log) {
                        if (maxFileSize!=null) log.maxfilesize(maxFileSize);
                        if (minFileSizeFactor!=null) log.minfilesizefactor(minFileSizeFactor);
                        if (chunk != null) {
                            chunk.getConfig(log.chunk);
                            chunk.getConfig(log.compact);
                        }
                    }
                }

                public Component cache;
                public LogStore logStore;

                public void getConfig(ProtonConfig.Summary.Builder builder) {
                    if (cache != null) {
                        cache.getConfig(builder.cache);
                    }
                    if (logStore != null) {
                        logStore.getConfig(builder.log);
                    }
                }
            }

            public Io io;
            public Store store;

            @Override
            public void getConfig(ProtonConfig.Builder builder) {
                if (io != null) {
                    io.getConfig(builder.summary);
                }
                if (store != null) {
                    store.getConfig(builder.summary);
                }
            }
        }

        public static class Initialize implements ProtonConfig.Producer {
            public Integer threads = null;

            @Override
            public void getConfig(ProtonConfig.Builder builder) {
                if (threads != null) {
                    builder.initialize.threads(threads);
                }
            }
        }

        public static class Feeding implements ProtonConfig.Producer {
            public Double concurrency = null;

            @Override
            public void getConfig(ProtonConfig.Builder builder) {
                if (concurrency != null) {
                    builder.feeding.concurrency(concurrency);
                }
            }
        }

        public RequestThreads threads = null;
        public FlushStrategy strategy = null;
        public Resizing resizing = null;
        public Index index = null;
        public Attribute attribute = null;
        public Summary summary = null;
        public Initialize initialize = null;
        public Feeding feeding = null;
        public RemovedDB removedDB = null;

        @Override
        public void getConfig(ProtonConfig.Builder builder) {
            if (threads != null) threads.getConfig(builder);
            if (strategy != null) strategy.getConfig(builder);
            if (resizing != null) resizing.getConfig(builder);
            if (index != null) index.getConfig(builder);
            if (attribute != null) attribute.getConfig(builder);
            if (summary != null) summary.getConfig(builder);
            if (initialize != null) initialize.getConfig(builder);
            if (feeding != null) feeding.getConfig(builder);
            if (removedDB != null) removedDB.getConfig(builder);
        }
    }

    public DispatchTuning dispatch = DispatchTuning.empty;
    public SearchNode searchNode;

    public Tuning(TreeConfigProducer<AnyConfigProducer> parent) {
        super(parent, "tuning");
    }

    @Override
    public void getConfig(ProtonConfig.Builder builder) {
        if (searchNode != null) searchNode.getConfig(builder);
    }

    public int threadsPerSearch() {
        if (searchNode == null) return 1;
        if (searchNode.threads == null) return 1;
        if (searchNode.threads.numThreadsPerSearch == null) return 1;
        return searchNode.threads.numThreadsPerSearch;
    }

}
