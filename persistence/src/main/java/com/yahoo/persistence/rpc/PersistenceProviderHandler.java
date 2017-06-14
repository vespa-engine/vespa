// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.rpc;

import com.yahoo.document.*;
import com.yahoo.document.fieldset.AllFields;
import com.yahoo.document.fieldset.FieldSet;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.document.serialization.*;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.jrt.*;
import com.yahoo.persistence.PersistenceRpcConfig;
import com.yahoo.persistence.spi.*;
import com.yahoo.persistence.spi.result.*;

import java.nio.ByteBuffer;
import java.util.TreeSet;

/**
 * @author thomasg
 */
public class PersistenceProviderHandler extends RPCHandler {
    DocumentTypeManager docTypeManager;
    PersistenceProvider provider = null;
    boolean started = false;

    int magic_number = 0xf00ba2;

    public PersistenceProviderHandler(PersistenceRpcConfig config) {
        super(config.port());
    }

    public void initialize(PersistenceProvider provider, DocumentTypeManager manager) {
        this.provider = provider;
        this.docTypeManager = manager;

        if (!started) {
            addMethod(new Method("vespa.persistence.connect", "s", "", this, "RPC_connect")
                    .paramDesc(0, "buildId", "Id to make sure client and server come from the same build"));
            addMethod(new PersistenceProviderMethod("initialize", this));
            addMethod(new PersistenceProviderMethod("getPartitionStates", this, "", "IS"));
            addMethod(new PersistenceProviderMethod("listBuckets", this, "l", "L")
                    .paramDesc("partitionId", "The partition to list buckets for")
                    .returnDesc("bucketIds", "An array of bucketids"));
            addMethod(new PersistenceProviderMethod("getModifiedBuckets", this, "", "L")
                    .returnDesc("bucketIds", "An array of bucketids"));
            addMethod(new PersistenceProviderMethod("setClusterState", this, "x")
                    .paramDesc("clusterState", "The updated cluster state"));
            addMethod(new BucketProviderMethod("setActiveState", this, "b")
                    .paramDesc("bucketState", "The new state (active/not active)"));
            addMethod(new BucketProviderMethod("getBucketInfo", this, "", "iiiiibb")
                    .returnDesc("checksum", "The bucket checksum")
                    .returnDesc("documentCount", "The number of unique documents stored in the bucket")
                    .returnDesc("documentSize", "The size of the unique documents")
                    .returnDesc("entryCount", "The number of entries (inserts/removes) in the bucket")
                    .returnDesc("usedSize", "The number of bytes used by the bucket in total")
                    .returnDesc("ready", "Whether the bucket is \"ready\" for external reads or not")
                    .returnDesc("active", "Whether the bucket has been activated for external reads or not"));
            addMethod(new TimestampedProviderMethod("put", this, "x")
                    .paramDesc("document", "The serialized document"));
            addMethod(new TimestampedProviderMethod("removeById", this, "s", "b")
                    .paramDesc("documentId", "The ID of the document to remove")
                    .returnDesc("existed", "Whether or not the document existed"));
            addMethod(new TimestampedProviderMethod("removeIfFound", this, "s", "b")
                    .paramDesc("documentId", "The ID of the document to remove")
                    .returnDesc("existed", "Whether or not the document existed"));
            addMethod(new TimestampedProviderMethod("update", this, "x", "l")
                    .paramDesc("update", "The document update to apply")
                    .returnDesc("existingTimestamp", "The timestamp of the document that the update was applied to, or 0 if it didn't exist"));
            addMethod(new BucketProviderMethod("flush", this));
            addMethod(new BucketProviderMethod("get", this, "ss", "lx")
                    .paramDesc("fieldSet", "A set of fields to return")
                    .paramDesc("documentId", "The document ID to fetch")
                    .returnDesc("timestamp", "The timestamp of the document fetched")
                    .returnDesc("document", "A serialized document"));
            addMethod(new BucketProviderMethod("createIterator", this, "ssllLb", "l")
                    .paramDesc("fieldSet", "A set of fields to return")
                    .paramDesc("documentSelectionString", "Document selection to match with")
                    .paramDesc("timestampFrom", "lowest timestamp to include")
                    .paramDesc("timestampTo", "Highest timestamp to include")
                    .paramDesc("timestampSubset", "Array of timestamps to include")
                    .paramDesc("includedVersions", "Document versions to include")
                    .returnDesc("iteratorId", "An iterator id to use for further calls to iterate and destroyIterator"));
            addMethod(new PersistenceProviderMethod("iterate", this, "ll", "LISXb")
                    .paramDesc("iteratorId", "An iterator id previously returned by createIterator")
                    .paramDesc("maxByteSize", "The maximum number of bytes to return in this call (approximate)")
                    .returnDesc("timestampArray", "Array of timestamps for DocEntries")
                    .returnDesc("flagArray", "Array of flags for DocEntries")
                    .returnDesc("docIdArray", "Array of document ids for DocEntries")
                    .returnDesc("docArray", "Array of documents for DocEntries")
                    .returnDesc("completed", "Whether or not iteration completed"));
            addMethod(new PersistenceProviderMethod("destroyIterator", this, "l")
                    .paramDesc("iteratorId", "An iterator id previously returned by createIterator"));
            addMethod(new BucketProviderMethod("createBucket", this));
            addMethod(new BucketProviderMethod("deleteBucket", this));
            addMethod(new BucketProviderMethod("split", this, "llll")
                    .paramDesc("target1Bucket", "Bucket id of first split target")
                    .paramDesc("target1Partition", "Partition id of first split target")
                    .paramDesc("target2Bucket", "Bucket id of second split target")
                    .paramDesc("target2Partition", "Partition id of second split target"));
            addMethod(new PersistenceProviderMethod("join", this, "llllll")
                    .paramDesc("source1Bucket", "Bucket id of first source bucket")
                    .paramDesc("source1Partition", "Partition id of first source bucket")
                    .paramDesc("source1Bucket", "Bucket id of second source bucket")
                    .paramDesc("source1Partition", "Partition id of second source bucket")
                    .paramDesc("source1Bucket", "Bucket id of target bucket")
                    .paramDesc("source1Partition", "Partition id of target bucket"));
            addMethod(new BucketProviderMethod("move", this, "l")
                    .paramDesc("partitionId", "The partition to move the bucket to"));
            addMethod(new BucketProviderMethod("maintain", this, "b")
                    .paramDesc("maintenanceLevel", "LOW or HIGH maintenance"));
            addMethod(new TimestampedProviderMethod("removeEntry", this));

            start();
            started = false;
        }
    }

    public void RPC_connect(Request req) {
    }

    public void addResult(Result result, Request req) {
        req.returnValues().add(new Int8Value((byte) result.getErrorType().ordinal()));
        req.returnValues().add(new StringValue(result.getErrorMessage()));
    }

    public void RPC_initialize(Request req) {
        addResult(provider.initialize(), req);
    }

    public void RPC_getPartitionStates(Request req) {
        PartitionStateListResult result = provider.getPartitionStates();
        addResult(result, req);

        int[] states = new int[result.getPartitionStates().size()];
        String[] reasons = new String[result.getPartitionStates().size()];

        for (int i = 0; i < states.length; ++i) {
            states[i] = result.getPartitionStates().get(i).getState().ordinal();
            reasons[i] = result.getPartitionStates().get(i).getReason();
        }

        req.returnValues().add(new Int32Array(states));
        req.returnValues().add(new StringArray(reasons));
    }

    void addBucketIdListResult(BucketIdListResult result, Request req) {
        addResult(result, req);

        long[] retVal = new long[result.getBuckets().size()];
        for (int i = 0; i < retVal.length; ++i) {
            retVal[i] = result.getBuckets().get(i).getRawId();
        }

        req.returnValues().add(new Int64Array(retVal));
    }

    public void RPC_listBuckets(Request req) {
        addBucketIdListResult(provider.listBuckets((short) req.parameters().get(0).asInt64()), req);
    }

    public void RPC_setClusterState(Request req) throws java.text.ParseException {
        ClusterStateImpl state = new ClusterStateImpl(req.parameters().get(0).asData());
        addResult(provider.setClusterState(state), req);
    }

    Bucket getBucket(Request req, int index) {
        return new Bucket((short)req.parameters().get(index + 1).asInt64(),
                          new BucketId(req.parameters().get(index).asInt64()));
    }

    public void RPC_setActiveState(Request req) {
        try {
            addResult(provider.setActiveState(getBucket(req, 0),
                BucketInfo.ActiveState.values()[req.parameters().get(2).asInt8()]), req);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void RPC_getBucketInfo(Request req) {
        BucketInfoResult result = provider.getBucketInfo(getBucket(req, 0));

        addResult(result, req);
        req.returnValues().add(new Int32Value(result.getBucketInfo().getChecksum()));
        req.returnValues().add(new Int32Value(result.getBucketInfo().getDocumentCount()));
        req.returnValues().add(new Int32Value(result.getBucketInfo().getDocumentSize()));
        req.returnValues().add(new Int32Value(result.getBucketInfo().getEntryCount()));
        req.returnValues().add(new Int32Value(result.getBucketInfo().getUsedSize()));
        req.returnValues().add(new Int8Value(result.getBucketInfo().isReady() ? (byte)1 : (byte)0));
        req.returnValues().add(new Int8Value(result.getBucketInfo().isActive() ? (byte)1 : (byte)0));
    }

    public void RPC_put(Request req) {
        try {
            GrowableByteBuffer buffer = new GrowableByteBuffer(ByteBuffer.wrap(req.parameters().get(3).asData()));
            Document doc = new Document(DocumentDeserializerFactory.create42(docTypeManager, buffer));
            addResult(provider.put(getBucket(req, 0), req.parameters().get(2).asInt64(), doc), req);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void addRemoveResult(RemoveResult result, Request req) {
        addResult(result, req);
        req.returnValues().add(new Int8Value(result.wasFound() ? (byte)1 : (byte)0));
    }

    public void RPC_removeById(Request req) {
        addRemoveResult(
                provider.remove(
                        getBucket(req, 0),
                        req.parameters().get(2).asInt64(),
                        new DocumentId(req.parameters().get(3).asString())), req);
    }

    public void RPC_removeIfFound(Request req) {
        addRemoveResult(
                provider.removeIfFound(
                        getBucket(req, 0),
                        req.parameters().get(2).asInt64(),
                        new DocumentId(req.parameters().get(3).asString())), req);
    }

    public void RPC_removeEntry(Request req) {
        addResult(
                provider.removeEntry(
                        getBucket(req, 0),
                        req.parameters().get(2).asInt64()), req);
    }

    public void RPC_update(Request req) {
        try {
            GrowableByteBuffer buffer = new GrowableByteBuffer(ByteBuffer.wrap(req.parameters().get(3).asData()));
            DocumentUpdate update = new DocumentUpdate(DocumentDeserializerFactory.createHead(docTypeManager, buffer));
            UpdateResult result = provider.update(getBucket(req, 0), req.parameters().get(2).asInt64(), update);
            addResult(result, req);

            req.returnValues().add(new Int64Value(result.getExistingTimestamp()));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void RPC_flush(Request req) {
        addResult(provider.flush(getBucket(req, 0)), req);
    }

    FieldSet getFieldSet(Request req, int index) {
        return new AllFields();

        //return new FieldSetRepo().parse(docTypeManager, req.parameters().get(index).asString());
    }

    byte[] serializeDocument(Document doc) {
        if (doc != null) {
            GrowableByteBuffer buf = new GrowableByteBuffer();
            DocumentSerializer serializer = DocumentSerializerFactory.create42(buf);
            doc.serialize(serializer);
            buf.flip();
            return buf.array();
        } else {
            return new byte[0];
        }
    }

    public void RPC_get(Request req) {
        GetResult result = provider.get(getBucket(req, 0),
                getFieldSet(req, 2),
                new DocumentId(req.parameters().get(3).asString()));
        addResult(result, req);
        req.returnValues().add(new Int64Value(result.getLastModifiedTimestamp()));
        req.returnValues().add(new DataValue(serializeDocument(result.getDocument())));
    }

    public void RPC_createIterator(Request req) {
        try {
            TreeSet<Long> timestampSet = new TreeSet<Long>();
            long[] timestamps = req.parameters().get(6).asInt64Array();
            for (long l : timestamps) {
                timestampSet.add(l);
            }

            Selection selection;
            if (timestamps.length > 0) {
                selection = new Selection(timestampSet);
            } else {
                selection = new Selection(
                        req.parameters().get(3).asString(),
                        req.parameters().get(4).asInt64(),
                        req.parameters().get(5).asInt64());
            }

            CreateIteratorResult result = provider.createIterator(
                    getBucket(req, 0),
                    getFieldSet(req, 2),
                    selection,
                    PersistenceProvider.IncludedVersions.values()[req.parameters().get(7).asInt8()]);

            addResult(result, req);
            req.returnValues().add(new Int64Value(result.getIteratorId()));
        } catch (ParseException e) {
            addResult(new Result(Result.ErrorType.PERMANENT_ERROR, "Unparseable document selection expression"), req);
            req.returnValues().add(new Int64Value(0));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void RPC_iterate(Request req) {
        try {
            long iteratorId = req.parameters().get(0).asInt64();
            long maxByteSize = req.parameters().get(1).asInt64();

            IterateResult result = provider.iterate(iteratorId, maxByteSize);

            addResult(result, req);

            int count = result.getEntries() != null ? result.getEntries().size() : 0;
            long[] timestamps = new long[count];
            int[] flags = new int[count];
            String[] docIds = new String[count];
            byte[][] documents = new byte[count][];

            for (int i = 0; i < count; ++i) {
                DocEntry entry = result.getEntries().get(i);
                timestamps[i] = entry.getTimestamp();
                flags[i] = entry.getType().ordinal();

                if (entry.getDocumentId() != null) {
                    docIds[i] = entry.getDocumentId().toString();
                } else {
                    docIds[i] = "";
                }

                if (entry.getDocument() != null) {
                    documents[i] = serializeDocument(entry.getDocument());
                } else {
                    documents[i] = (new byte[0]);
                }
            }

            req.returnValues().add(new Int64Array(timestamps));
            req.returnValues().add(new Int32Array(flags));
            req.returnValues().add(new StringArray(docIds));
            req.returnValues().add(new DataArray(documents));
            req.returnValues().add(new Int8Value(result.isCompleted() ? (byte)1 : (byte)0));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void RPC_destroyIterator(Request req) {
        try {
            addResult(provider.destroyIterator(req.parameters().get(0).asInt64()), req);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void RPC_createBucket(Request req) {
        addResult(provider.createBucket(getBucket(req, 0)), req);
    }

    public void RPC_deleteBucket(Request req) {
        addResult(provider.deleteBucket(getBucket(req, 0)), req);
    }

    public void RPC_getModifiedBuckets(Request req) {
        addBucketIdListResult(provider.getModifiedBuckets(), req);
    }

    public void RPC_maintain(Request req) {
        addResult(provider.maintain(getBucket(req, 0),
                PersistenceProvider.MaintenanceLevel.values()[req.parameters().get(2).asInt8()]), req);
    }

    public void RPC_split(Request req) {
        addResult(provider.split(
                getBucket(req, 0),
                getBucket(req, 2),
                getBucket(req, 4)), req);
    }

    public void RPC_join(Request req) {
        addResult(provider.join(
                getBucket(req, 0),
                getBucket(req, 2),
                getBucket(req, 4)), req);
    }

    public void RPC_move(Request req) {
        addResult(provider.move(
                getBucket(req, 0),
                (short)req.parameters().get(2).asInt64()), req);
    }
}
