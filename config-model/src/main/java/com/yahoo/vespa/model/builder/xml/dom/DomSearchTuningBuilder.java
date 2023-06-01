// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.text.XML;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.search.Tuning;
import org.w3c.dom.Element;

/**
 * Builder for the tuning config for a search cluster.
 *
 * @author geirst
 */
public class DomSearchTuningBuilder extends VespaDomBuilder.DomConfigProducerBuilderBase<Tuning> {

    @Override
    protected Tuning doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> parent, Element spec) {
        Tuning tuning = new Tuning(parent);
        for (Element e : XML.getChildren(spec)) {
            if (equals("searchnode", e))
                handleSearchNode(e, tuning);
        }
        return tuning;
    }

    private static boolean equals(String name, Element e) {
        return name.equals(e.getNodeName());
    }

    private static String asString(Element e) {
        return e.getFirstChild().getNodeValue();
    }

    private static Long asLong(Element e) {
        return Long.parseLong(e.getFirstChild().getNodeValue());
    }

    private static Integer asInt(Element e) {
        return Integer.parseInt(e.getFirstChild().getNodeValue());
    }

    private static Double asDouble(Element e) {
        return Double.parseDouble(e.getFirstChild().getNodeValue());
    }

    private void handleSearchNode(Element spec, Tuning t) {
        t.searchNode = new Tuning.SearchNode();
        for (Element e : XML.getChildren(spec)) {
            if (equals("requestthreads", e)) {
               handleRequestThreads(e, t.searchNode);
            } else if (equals("flushstrategy", e)) {
                handleFlushStrategy(e, t.searchNode);
            } else if (equals("resizing", e)) {
                handleResizing(e, t.searchNode);
            } else if (equals("index", e)) {
                handleIndex(e, t.searchNode);
            } else if (equals("attribute", e)) {
                handleAttribute(e, t.searchNode);
            } else if (equals("summary", e)) {
                handleSummary(e, t.searchNode);
            } else if (equals("initialize", e)) {
                handleInitialize(e, t.searchNode);
            } else if (equals("feeding", e)) {
                handleFeeding(e, t.searchNode);
            } else if (equals("removed-db", e)) {
                handleRemovedDB(e, t.searchNode);
            } else if (equals("lidspace", e)) {
                handleLidSpace(e, t.searchNode);
            }
        }
    }

    private void handleLidSpace(Element spec, Tuning.SearchNode t) {
        t.lidSpace = new Tuning.SearchNode.LidSpace();
        for (Element e : XML.getChildren(spec)) {
            if (equals("max-bloat-factor", e)) {
                t.lidSpace.bloatFactor = asDouble(e);
            }
        }

    }

    private void handleRequestThreads(Element spec, Tuning.SearchNode sn) {
        sn.threads = new Tuning.SearchNode.RequestThreads();
        Tuning.SearchNode.RequestThreads rt = sn.threads;
        for (Element e : XML.getChildren(spec)) {
            if (equals("search", e)) {
                rt.numSearchThreads = asInt(e);
            } else if (equals("persearch", e)) {
                rt.numThreadsPerSearch = asInt(e);
            } else if (equals("summary", e)) {
                rt.numSummaryThreads = asInt(e);
            }
        }
    }

    private void handleRemovedDB(Element spec, Tuning.SearchNode sn) {
        sn.removedDB = new Tuning.SearchNode.RemovedDB();
        for (Element e : XML.getChildren(spec)) {
            if (equals("prune", e)) {
                sn.removedDB.prune = new Tuning.SearchNode.RemovedDB.Prune();
                Tuning.SearchNode.RemovedDB.Prune prune = sn.removedDB.prune;
                for (Element e2 : XML.getChildren(e)) {
                    if (equals("age", e2)) {
                        prune.age = asDouble(e2);
                    } else if (equals("interval", e2)) {
                        prune.interval = asDouble(e2);
                    }
                }
            }
        }
    }

    private void handleFlushStrategy(Element spec, Tuning.SearchNode sn) {
        for (Element e : XML.getChildren(spec)) {
            if (equals("native", e)) {
                handleNativeStrategy(e, sn);
            }
        }
    }

    private void handleNativeStrategy(Element spec, Tuning.SearchNode sn) {
        sn.strategy = new Tuning.SearchNode.FlushStrategy();
        Tuning.SearchNode.FlushStrategy fs = sn.strategy;
        for (Element e : XML.getChildren(spec)) {
            if (equals("total", e)) {
                for (Element subElem : XML.getChildren(e)) {
                    if (equals("maxmemorygain", subElem)) {
                        fs.totalMaxMemoryGain = asLong(subElem);
                    } else if (equals("diskbloatfactor", subElem)) {
                        fs.totalDiskBloatFactor = asDouble(subElem);
                    }
                }
            } else if (equals("component", e)) {
                for (Element subElem : XML.getChildren(e)) {
                    if (equals("maxmemorygain", subElem)) {
                        fs.componentMaxMemoryGain = asLong(subElem);
                    } else if (equals("diskbloatfactor", subElem)) {
                        fs.componentDiskBloatFactor = asDouble(subElem);
                    } else if (equals("maxage", subElem)) {
                        fs.componentMaxage = asDouble(subElem);
                    }
                }
            } else if (equals("transactionlog", e)) {
                for (Element subElem : XML.getChildren(e)) {
                    if (equals("maxsize", subElem)) {
                        fs.transactionLogMaxSize = asLong(subElem);
                    }
                }
            } else if (equals("conservative", e)) {
                for (Element subElem : XML.getChildren(e)) {
                    if (equals("memory-limit-factor", subElem)) {
                        fs.conservativeMemoryLimitFactor = asDouble(subElem);
                    } else if (equals("disk-limit-factor", subElem)) {
                        fs.conservativeDiskLimitFactor = asDouble(subElem);
                    }
                }
            }
        }
    }

    private void handleResizing(Element spec, Tuning.SearchNode sn) {
        sn.resizing = new Tuning.SearchNode.Resizing();
        for (Element e : XML.getChildren(spec)) {
            if (equals("initialdocumentcount", e)) {
                sn.resizing.initialDocumentCount = asInt(e);
            } else if (equals("amortize-count", e)) {
                sn.resizing.amortizeCount = asInt(e);
            }
        }
    }

    private void handleIndex(Element spec, Tuning.SearchNode sn) {
        sn.index = new Tuning.SearchNode.Index();
        for (Element e : XML.getChildren(spec)) {
            if (equals("io", e)) {
                sn.index.io = new Tuning.SearchNode.Index.Io();
                Tuning.SearchNode.Index.Io io = sn.index.io;
                for (Element e2 : XML.getChildren(e)) {
                    if (equals("write", e2)) {
                        io.write = Tuning.SearchNode.IoType.fromString(asString(e2));
                    } else if (equals("read", e2)) {
                        io.read = Tuning.SearchNode.IoType.fromString(asString(e2));
                    } else if (equals("search", e2)) {
                        io.search = Tuning.SearchNode.IoType.fromString(asString(e2));
                    }
                }
            } else if (equals("warmup", e)) {
                sn.index.warmup = new Tuning.SearchNode.Index.Warmup();
                Tuning.SearchNode.Index.Warmup warmup = sn.index.warmup;
                for (Element e2 : XML.getChildren(e)) {
                    if (equals("time", e2)) {
                        warmup.time = asDouble(e2);
                    } else if (equals("unpack", e2)) {
                        warmup.unpack = Boolean.parseBoolean(asString(e2));
                    }
                }
            }
        }
    }

    private void handleAttribute(Element spec, Tuning.SearchNode sn) {
        sn.attribute = new Tuning.SearchNode.Attribute();
        for (Element e : XML.getChildren(spec)) {
            if (equals("io", e)) {
                sn.attribute.io = new Tuning.SearchNode.Attribute.Io();
                for (Element e2 : XML.getChildren(e)) {
                    if (equals("write", e2)) {
                        sn.attribute.io.write = Tuning.SearchNode.IoType.fromString(asString(e2));
                    }
                }
            }
        }
    }

    private void handleSummary(Element spec, Tuning.SearchNode sn) {
        sn.summary = new Tuning.SearchNode.Summary();
        for (Element e : XML.getChildren(spec)) {
            if (equals("io", e)) {
                sn.summary.io = new Tuning.SearchNode.Summary.Io();
                for (Element e2 : XML.getChildren(e)) {
                    if (equals("write", e2)) {
                        sn.summary.io.write = Tuning.SearchNode.IoType.fromString(asString(e2));
                    } else if (equals("read", e2)) {
                        sn.summary.io.read = Tuning.SearchNode.IoType.fromString(asString(e2));
                    }
                }
            } else if (equals("store", e)) {
                handleSummaryStore(e, sn.summary);
            }
        }
    }

    private void handleSummaryStore(Element spec, Tuning.SearchNode.Summary s) {
        s.store = new Tuning.SearchNode.Summary.Store();
        for (Element e : XML.getChildren(spec)) {
            if (equals("cache", e)) {
                s.store.cache = new Tuning.SearchNode.Summary.Store.Component();
                handleSummaryStoreComponent(e, s.store.cache);
            } else if (equals("logstore", e)) {
                handleSummaryLogStore(e, s.store);
            }
        }
    }

    private void handleSummaryStoreComponent(Element spec, Tuning.SearchNode.Summary.Store.Component c) {
         for (Element e : XML.getChildren(spec)) {
            if (equals("maxsize", e)) {
                c.maxSize = asLong(e);
            } else if (equals("maxsize-percent", e)) {
                c.maxSizePercent = asDouble(e);
            } else if (equals("initialentries", e)) {
                c.initialEntries = asLong(e);
            } else if (equals("compression", e)) {
                c.compression = new Tuning.SearchNode.Summary.Store.Compression();
                handleSummaryStoreCompression(e, c.compression);
            }
        }
    }

    private void handleSummaryStoreCompression(Element spec, Tuning.SearchNode.Summary.Store.Compression c) {
        for (Element e : XML.getChildren(spec)) {
            if (equals("type", e)) {
                c.type = Tuning.SearchNode.Summary.Store.Compression.Type.fromString(asString(e));
            } else if (equals("level", e)) {
                c.level = asInt(e);
            }
        }
    }

    private void handleSummaryLogStore(Element spec, Tuning.SearchNode.Summary.Store s) {
        s.logStore = new Tuning.SearchNode.Summary.Store.LogStore();
        for (Element e : XML.getChildren(spec)) {
            if (equals("maxfilesize", e)) {
                s.logStore.maxFileSize = asLong(e);
            } else if (equals("minfilesizefactor", e)) {
                s.logStore.minFileSizeFactor = asDouble(e);
            } else if (equals("chunk", e)) {
                s.logStore.chunk = new Tuning.SearchNode.Summary.Store.Component(true);
                handleSummaryStoreComponent(e, s.logStore.chunk);
            }
        }
    }

    private void handleInitialize(Element spec, Tuning.SearchNode sn) {
        sn.initialize = new Tuning.SearchNode.Initialize();
        for (Element e : XML.getChildren(spec)) {
            if (equals("threads", e)) {
                sn.initialize.threads = asInt(e);
            }
        }
    }

    private void handleFeeding(Element spec, Tuning.SearchNode sn) {
        sn.feeding = new Tuning.SearchNode.Feeding();
        for (Element e : XML.getChildren(spec)) {
            if (equals("concurrency", e)) {
                sn.feeding.concurrency = asDouble(e);
            }
        }
    }

}
