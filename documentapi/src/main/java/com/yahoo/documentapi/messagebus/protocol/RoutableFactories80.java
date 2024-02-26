package com.yahoo.documentapi.messagebus.protocol;

import ai.vespa.documentapi.protobuf.DocapiCommon;
import ai.vespa.documentapi.protobuf.DocapiFeed;
import ai.vespa.documentapi.protobuf.DocapiInspect;
import ai.vespa.documentapi.protobuf.DocapiVisiting;
import com.google.protobuf.AbstractMessage;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Parser;
import com.yahoo.document.BucketId;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.GlobalId;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.document.serialization.DocumentDeserializer;
import com.yahoo.document.serialization.DocumentDeserializerFactory;
import com.yahoo.document.serialization.DocumentSerializer;
import com.yahoo.document.serialization.DocumentSerializerFactory;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.messagebus.Routable;
import com.yahoo.vdslib.DocumentSummary;
import com.yahoo.vdslib.SearchResult;
import com.yahoo.vdslib.VisitorStatistics;
import com.yahoo.vespa.objects.BufferSerializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Implementation of MessageBus message request/response serialization built around Protocol Buffers.
 */
abstract class RoutableFactories80 {

    private static final Logger log = Logger.getLogger(RoutableFactories80.class.getName());

    private static class ProtobufCodec<DocApiT extends Routable, ProtoT extends AbstractMessage> implements RoutableFactory {

        private final Class<DocApiT>                          apiClass;
        private final Function<DocApiT, ProtoT>               encoderFn;
        private final Function<DocumentDeserializer, DocApiT> decoderFn;

        ProtobufCodec(Class<DocApiT> apiClass,
                      Function<DocApiT, ProtoT> encoderFn,
                      Function<DocumentDeserializer, DocApiT> decoderFn) {
            this.apiClass = apiClass;
            this.encoderFn = encoderFn;
            this.decoderFn = decoderFn;
        }

        @Override
        public byte[] encode(int msgType, Routable obj) {
            try {
                var protoMsg = encoderFn.apply(apiClass.cast(obj));
                int protoSize = protoMsg.getSerializedSize();
                // The message payload contains a 4-byte header int which specifies the type of the message
                // that follows. We want to write this header and the subsequence message bytes using a single
                // allocation and without unneeded copying, so we create one array for both purposes and encode
                // directly into it. Aside from the header, this is pretty much a mirror image of what the
                // toByteArray() method on Protobuf message objects already does.
                var buf = new byte[4 + protoSize];
                ByteBuffer.wrap(buf).putInt(msgType); // In network order (default setting)
                var protoStream = CodedOutputStream.newInstance(buf, 4, protoSize);
                protoMsg.writeTo(protoStream); // Writing straight to array, no need to flush
                protoStream.checkNoSpaceLeft();
                return buf;
            } catch (IOException | RuntimeException e) {
                log.severe("Error during Protobuf encoding of message type %s: %s".formatted(apiClass.getSimpleName(), e.getMessage()));
                return null;
            }
        }

        @Override
        public boolean encode(Routable obj, DocumentSerializer out) {
            // Legacy encode; not supported
            return false;
        }

        @Override
        public Routable decode(DocumentDeserializer in) {
            try {
                return decoderFn.apply(in);
            } catch (RuntimeException e) {
                throw new RuntimeException("Error during Protobuf decoding of message type %s: %s"
                        .formatted(apiClass.getSimpleName(), e.getMessage()), e);
            }
        }

    }

    private static class ProtobufCodecBuilder<DocApiT extends Routable, ProtoT extends AbstractMessage> {

        private final Class<DocApiT>                    apiClass;
        private final Class<ProtoT>                     protoClass;
        private Function<DocApiT, ProtoT>               encoderFn;
        private Function<DocumentDeserializer, DocApiT> decoderFn;

        ProtobufCodecBuilder(Class<DocApiT> apiClass, Class<ProtoT> protoClass) {
            this.apiClass = apiClass;
            this.protoClass = protoClass;
        }

        static <DocApiT extends Routable, ProtoT extends AbstractMessage> ProtobufCodecBuilder<DocApiT, ProtoT>
        of(Class<DocApiT> apiClass, Class<ProtoT> protoClass) {
            return new ProtobufCodecBuilder<>(apiClass, protoClass);
        }

        ProtobufCodecBuilder<DocApiT, ProtoT> encoder(Function<DocApiT, ProtoT> fn) {
            if (encoderFn != null) {
                throw new IllegalArgumentException("Encoder already set");
            }
            encoderFn = fn;
            return this;
        }

        ProtobufCodecBuilder<DocApiT, ProtoT> decoder(Parser<ProtoT> parser, Function<ProtoT, DocApiT> fn) {
            if (decoderFn != null) {
                throw new IllegalArgumentException("Decoder already set");
            }
            decoderFn = (buf) -> {
                try {
                    var protoObj = parser.parseFrom(buf.getBuf().getByteBuffer());
                    return fn.apply(protoObj);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            };
            return this;
        }

        ProtobufCodecBuilder<DocApiT, ProtoT> decoderWithRepo(Parser<ProtoT> parser, BiFunction<ProtoT, DocumentTypeManager, DocApiT> fn) {
            if (decoderFn != null) {
                throw new IllegalArgumentException("Decoder already set");
            }
            decoderFn = (buf) -> {
                try {
                    var protoObj = parser.parseFrom(buf.getBuf().getByteBuffer());
                    return fn.apply(protoObj, buf.getTypeRepo());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            };
            return this;
        }

        ProtobufCodec<DocApiT, ProtoT> build() {
            Objects.requireNonNull(encoderFn, "Encoder has not been set");
            Objects.requireNonNull(decoderFn, "Decoder has not been set");
            return new ProtobufCodec<>(apiClass, encoderFn, decoderFn);
        }
    }

    // Protobuf codec helpers for common types

    private static DocapiCommon.GlobalId toProtoGlobalId(GlobalId gid) {
        return DocapiCommon.GlobalId.newBuilder().setRawGid(ByteString.copyFrom(gid.getRawId())).build();
    }

    private static GlobalId fromProtoGlobalId(DocapiCommon.GlobalId gid) {
        return new GlobalId(gid.getRawGid().toByteArray());
    }

    private static DocapiCommon.BucketId toProtoBucketId(BucketId id) {
        return DocapiCommon.BucketId.newBuilder().setRawId(id.getRawId()).build();
    }

    private static BucketId fromProtoBucketId(DocapiCommon.BucketId id) {
        return new BucketId(id.getRawId());
    }

    private static DocapiCommon.DocumentId toProtoDocId(DocumentId id) {
        return DocapiCommon.DocumentId.newBuilder().setId(id.toString()).build();
    }

    private static DocumentId fromProtoDocId(DocapiCommon.DocumentId id) {
        return new DocumentId(id.getId());
    }

    private static DocapiCommon.FieldSet toProtoFieldSet(String rawFieldSpec) {
        return DocapiCommon.FieldSet.newBuilder().setSpec(rawFieldSpec).build();
    }

    private static String fromProtoFieldSet(DocapiCommon.FieldSet fieldSet) {
        return fieldSet.getSpec();
    }

    private static ByteBuffer serializeDoc(Document doc) {
        var buf = new GrowableByteBuffer();
        doc.serialize(buf);
        buf.flip();
        return buf.getByteBuffer();
    }

    private static DocapiCommon.Document toProtoDocument(Document doc) {
        // TODO a lot of copying here... Consider adding Document serialization to OutputStream
        //  so that we can serialize directly into a ByteString.Output instance.
        return toProtoDocument(serializeDoc(doc));
    }

    private static DocapiCommon.Document toProtoDocument(ByteBuffer rawDocData) {
        return DocapiCommon.Document.newBuilder()
                .setPayload(ByteString.copyFrom(rawDocData))
                .build();
    }

    private static Document fromProtoDocument(DocapiCommon.Document protoDoc, DocumentTypeManager repo) {
        var deserializer = DocumentDeserializerFactory.createHead(repo, new GrowableByteBuffer(protoDoc.getPayload().asReadOnlyByteBuffer()));
        return Document.createDocument(deserializer);
    }

    private static Document deserializeDoc(ByteBuffer rawDocData, DocumentTypeManager repo) {
        var deserializer = DocumentDeserializerFactory.createHead(repo, new GrowableByteBuffer(rawDocData));
        return Document.createDocument(deserializer);
    }

    private static DocapiFeed.TestAndSetCondition toProtoTasCondition(TestAndSetCondition tasCond) {
        return DocapiFeed.TestAndSetCondition.newBuilder()
                .setSelection(tasCond.getSelection())
                .build();
    }

    private static TestAndSetCondition fromProtoTasCondition(DocapiFeed.TestAndSetCondition protoTasCond) {
        // Note: the empty (default) string implies "no condition present"
        return new TestAndSetCondition(protoTasCond.getSelection());
    }

    private static ByteBuffer serializeUpdate(DocumentUpdate update) {
        var buf = new GrowableByteBuffer();
        update.serialize(DocumentSerializerFactory.createHead(buf));
        buf.flip();
        return buf.getByteBuffer();
    }

    private static DocapiFeed.DocumentUpdate toProtoUpdate(DocumentUpdate update) {
        // TODO also consider DocumentUpdate serialization directly to OutputStream to avoid unneeded copying
        return DocapiFeed.DocumentUpdate.newBuilder()
                .setPayload(ByteString.copyFrom(serializeUpdate(update)))
                .build();
    }

    private static DocumentUpdate fromProtoUpdate(DocapiFeed.DocumentUpdate protoUpdate, DocumentTypeManager repo) {
        var deserializer = DocumentDeserializerFactory.createHead(repo, new GrowableByteBuffer(protoUpdate.getPayload().asReadOnlyByteBuffer()));
        return new DocumentUpdate(deserializer);
    }

    private static DocapiCommon.DocumentSelection toProtoDocumentSelection(String rawSelection) {
        return DocapiCommon.DocumentSelection.newBuilder()
                .setSelection(rawSelection)
                .build();
    }

    private static String fromProtoDocumentSelection(DocapiCommon.DocumentSelection protoSelection) {
        return protoSelection.getSelection();
    }

    private static DocapiCommon.BucketSpace toProtoBucketSpace(String spaceName) {
        return DocapiCommon.BucketSpace.newBuilder()
                .setName(spaceName)
                .build();
    }

    private static String fromProtoBucketSpace(DocapiCommon.BucketSpace protoSpace) {
        return protoSpace.getName();
    }

    private static DocapiCommon.ClusterState toProtoClusterState(String stateStr) {
        return DocapiCommon.ClusterState.newBuilder().setStateString(stateStr).build();
    }

    private static String fromProtoClusterState(DocapiCommon.ClusterState state) {
        return state.getStateString();
    }

    // Message codec implementations

    // ---------------------------------------------
    // Get request and response
    // ---------------------------------------------

    static RoutableFactory createGetDocumentMessageFactory() {
        return ProtobufCodecBuilder
                .of(GetDocumentMessage.class, DocapiFeed.GetDocumentRequest.class)
                .encoder((apiMsg) ->
                        DocapiFeed.GetDocumentRequest.newBuilder()
                            .setDocumentId(toProtoDocId(apiMsg.getDocumentId()))
                            .setFieldSet(toProtoFieldSet(apiMsg.getFieldSet()))
                            .build())
                .decoder(DocapiFeed.GetDocumentRequest.parser(), (protoMsg) ->
                        new GetDocumentMessage(
                                fromProtoDocId(protoMsg.getDocumentId()),
                                fromProtoFieldSet(protoMsg.getFieldSet())))
                .build();
    }

    static RoutableFactory createGetDocumentReplyFactory() {
        return ProtobufCodecBuilder
                .of(GetDocumentReply.class, DocapiFeed.GetDocumentResponse.class)
                .encoder((apiReply) -> {
                    var builder = DocapiFeed.GetDocumentResponse.newBuilder()
                            .setLastModified(apiReply.getLastModified());
                    var maybeDoc = apiReply.getDocument();
                    if (maybeDoc != null) {
                        builder.setDocument(toProtoDocument(serializeDoc(maybeDoc)));
                    }
                    return builder.build();
                })
                .decoderWithRepo(DocapiFeed.GetDocumentResponse.parser(), (protoReply, repo) -> {
                    GetDocumentReply reply;
                    if (protoReply.hasDocument()) {
                        var doc = fromProtoDocument(protoReply.getDocument(), repo);
                        doc.setLastModified(protoReply.getLastModified());
                        reply = new GetDocumentReply(doc);
                    } else {
                        reply = new GetDocumentReply(null);
                    }
                    reply.setLastModified(protoReply.getLastModified());
                    return reply;
                })
                .build();
    }

    // ---------------------------------------------
    // Put request and response
    // ---------------------------------------------

    static RoutableFactory createPutDocumentMessageFactory() {
        return ProtobufCodecBuilder
                .of(PutDocumentMessage.class, DocapiFeed.PutDocumentRequest.class)
                .encoder((apiMsg) -> {
                    var builder = DocapiFeed.PutDocumentRequest.newBuilder()
                            .setForceAssignTimestamp(apiMsg.getTimestamp())
                            .setCreateIfMissing(apiMsg.getCreateIfNonExistent())
                            .setDocument(toProtoDocument(apiMsg.getDocumentPut().getDocument()));
                    if (apiMsg.getCondition().isPresent()) {
                        builder.setCondition(toProtoTasCondition(apiMsg.getCondition()));
                    }
                    return builder.build();
                })
                .decoderWithRepo(DocapiFeed.PutDocumentRequest.parser(), (protoMsg, repo) -> {
                    var doc = fromProtoDocument(protoMsg.getDocument(), repo);
                    var msg = new PutDocumentMessage(new DocumentPut(doc));
                    if (protoMsg.hasCondition()) {
                        msg.setCondition(fromProtoTasCondition(protoMsg.getCondition()));
                    }
                    msg.setTimestamp(protoMsg.getForceAssignTimestamp());
                    msg.setCreateIfNonExistent(protoMsg.getCreateIfMissing());
                    return msg;
                })
                .build();
    }

    static RoutableFactory createPutDocumentReplyFactory() {
        return ProtobufCodecBuilder
                .of(WriteDocumentReply.class, DocapiFeed.PutDocumentResponse.class)
                .encoder((apiReply) ->
                        DocapiFeed.PutDocumentResponse.newBuilder()
                                .setModificationTimestamp(apiReply.getHighestModificationTimestamp())
                                .build())
                .decoder(DocapiFeed.PutDocumentResponse.parser(), (protoReply) -> {
                    var reply = new WriteDocumentReply(DocumentProtocol.REPLY_PUTDOCUMENT);
                    reply.setHighestModificationTimestamp(protoReply.getModificationTimestamp());
                    return reply;
                })
                .build();
    }

    // ---------------------------------------------
    // Update request and response
    // ---------------------------------------------

    static RoutableFactory createUpdateDocumentMessageFactory() {
        return ProtobufCodecBuilder
                .of(UpdateDocumentMessage.class, DocapiFeed.UpdateDocumentRequest.class)
                .encoder((apiMsg) -> {
                    var builder = DocapiFeed.UpdateDocumentRequest.newBuilder()
                            .setUpdate(toProtoUpdate(apiMsg.getDocumentUpdate()))
                            .setExpectedOldTimestamp(apiMsg.getOldTimestamp())
                            .setForceAssignTimestamp(apiMsg.getNewTimestamp());
                    if (apiMsg.getCondition().isPresent()) {
                        builder.setCondition(toProtoTasCondition(apiMsg.getCondition()));
                    }
                    return builder.build();
                })
                .decoderWithRepo(DocapiFeed.UpdateDocumentRequest.parser(), (protoMsg, repo) -> {
                    var msg = new UpdateDocumentMessage(fromProtoUpdate(protoMsg.getUpdate(), repo));
                    msg.setOldTimestamp(protoMsg.getExpectedOldTimestamp());
                    msg.setNewTimestamp(protoMsg.getForceAssignTimestamp());
                    if (protoMsg.hasCondition()) {
                        msg.setCondition(fromProtoTasCondition(protoMsg.getCondition()));
                    }
                    return msg;
                })
                .build();
    }

    static RoutableFactory createUpdateDocumentReplyFactory() {
        return ProtobufCodecBuilder
                .of(UpdateDocumentReply.class, DocapiFeed.UpdateDocumentResponse.class)
                .encoder((apiReply) ->
                        DocapiFeed.UpdateDocumentResponse.newBuilder()
                                .setModificationTimestamp(apiReply.getHighestModificationTimestamp())
                                .setWasFound(apiReply.wasFound())
                                .build())
                .decoder(DocapiFeed.UpdateDocumentResponse.parser(), (protoReply) -> {
                    var reply = new UpdateDocumentReply();
                    reply.setHighestModificationTimestamp(protoReply.getModificationTimestamp());
                    reply.setWasFound(protoReply.getWasFound());
                    return reply;
                })
                .build();
    }

    // ---------------------------------------------
    // Remove request and response
    // ---------------------------------------------

    static RoutableFactory createRemoveDocumentMessageFactory() {
        return ProtobufCodecBuilder
                .of(RemoveDocumentMessage.class, DocapiFeed.RemoveDocumentRequest.class)
                .encoder((apiMsg) -> {
                    var builder = DocapiFeed.RemoveDocumentRequest.newBuilder()
                            .setDocumentId(toProtoDocId(apiMsg.getDocumentId()));
                    if (apiMsg.getCondition().isPresent()) {
                        builder.setCondition(toProtoTasCondition(apiMsg.getCondition()));
                    }
                    return builder.build();
                })
                .decoder(DocapiFeed.RemoveDocumentRequest.parser(), (protoMsg) -> {
                    var msg = new RemoveDocumentMessage(fromProtoDocId(protoMsg.getDocumentId()));
                    if (protoMsg.hasCondition()) {
                        msg.setCondition(fromProtoTasCondition(protoMsg.getCondition()));
                    }
                    return msg;
                })
                .build();
    }

    static RoutableFactory createRemoveDocumentReplyFactory() {
        return ProtobufCodecBuilder
                .of(RemoveDocumentReply.class, DocapiFeed.RemoveDocumentResponse.class)
                .encoder((apiReply) ->
                        DocapiFeed.RemoveDocumentResponse.newBuilder()
                                .setWasFound(apiReply.wasFound())
                                .setModificationTimestamp(apiReply.getHighestModificationTimestamp())
                                .build())
                .decoder(DocapiFeed.RemoveDocumentResponse.parser(), (protoReply) -> {
                    var reply = new RemoveDocumentReply();
                    reply.setWasFound(protoReply.getWasFound());
                    reply.setHighestModificationTimestamp(protoReply.getModificationTimestamp());
                    return reply;
                })
                .build();
    }

    // ---------------------------------------------
    // RemoveLocation request and response
    // ---------------------------------------------

    static RoutableFactory createRemoveLocationMessageFactory() {
        return ProtobufCodecBuilder
                .of(RemoveLocationMessage.class, DocapiFeed.RemoveLocationRequest.class)
                .encoder((apiMsg) ->
                        DocapiFeed.RemoveLocationRequest.newBuilder()
                                .setBucketSpace(toProtoBucketSpace(apiMsg.getBucketSpace()))
                                .setSelection(toProtoDocumentSelection(apiMsg.getDocumentSelection()))
                                .build())
                .decoder(DocapiFeed.RemoveLocationRequest.parser(), (protoMsg) ->
                        new RemoveLocationMessage(
                                fromProtoDocumentSelection(protoMsg.getSelection()),
                                fromProtoBucketSpace(protoMsg.getBucketSpace())))
                .build();
    }

    static RoutableFactory createRemoveLocationReplyFactory() {
        return ProtobufCodecBuilder
                .of(DocumentReply.class, DocapiFeed.RemoveLocationResponse.class)
                .encoder((apiReply) -> DocapiFeed.RemoveLocationResponse.newBuilder().build())
                .decoder(DocapiFeed.RemoveLocationResponse.parser(),
                        (protoReply) -> new DocumentReply(DocumentProtocol.REPLY_REMOVELOCATION))
                .build();
    }

    // ---------------------------------------------
    // CreateVisitor request and response
    // ---------------------------------------------

    private static DocapiVisiting.VisitorParameter toProtoVisitorParameter(String key, byte[] value) {
        return DocapiVisiting.VisitorParameter.newBuilder()
                .setKey(key)
                .setValue(ByteString.copyFrom(value))
                .build();
    }

    static RoutableFactory createCreateVisitorMessageFactory() {
        return ProtobufCodecBuilder
                .of(CreateVisitorMessage.class, DocapiVisiting.CreateVisitorRequest.class)
                .encoder((apiMsg) -> {
                    var builder = DocapiVisiting.CreateVisitorRequest.newBuilder()
                            .setBucketSpace(toProtoBucketSpace(apiMsg.getBucketSpace()))
                            .setVisitorLibraryName(apiMsg.getLibraryName())
                            .setInstanceId(apiMsg.getInstanceId())
                            .setControlDestination(apiMsg.getControlDestination())
                            .setDataDestination(apiMsg.getDataDestination())
                            .setSelection(toProtoDocumentSelection(apiMsg.getDocumentSelection()))
                            .setFieldSet(toProtoFieldSet(apiMsg.getFieldSet()))
                            .setMaxPendingReplyCount(apiMsg.getMaxPendingReplyCount())
                            .setFromTimestamp(apiMsg.getFromTimestamp())
                            .setToTimestamp(apiMsg.getToTimestamp())
                            .setVisitTombstones(apiMsg.getVisitRemoves())
                            .setVisitInconsistentBuckets(apiMsg.getVisitInconsistentBuckets())
                            .setMaxBucketsPerVisitor(apiMsg.getMaxBucketsPerVisitor());
                    for (var id : apiMsg.getBuckets()) {
                        builder.addBuckets(toProtoBucketId(id));
                    }
                    for (var entry : apiMsg.getParameters().entrySet()) {
                        builder.addParameters(toProtoVisitorParameter(entry.getKey(), entry.getValue()));
                    }
                    return builder.build();
                })
                .decoder(DocapiVisiting.CreateVisitorRequest.parser(), (protoMsg) -> {
                    var msg = new CreateVisitorMessage();
                    msg.setBucketSpace(fromProtoBucketSpace(protoMsg.getBucketSpace()));
                    msg.setLibraryName(protoMsg.getVisitorLibraryName());
                    msg.setInstanceId(protoMsg.getInstanceId());
                    msg.setControlDestination(protoMsg.getControlDestination());
                    msg.setDataDestination(protoMsg.getDataDestination());
                    msg.setDocumentSelection(fromProtoDocumentSelection(protoMsg.getSelection()));
                    msg.setFieldSet(fromProtoFieldSet(protoMsg.getFieldSet()));
                    msg.setMaxPendingReplyCount(protoMsg.getMaxPendingReplyCount());
                    msg.setFromTimestamp(protoMsg.getFromTimestamp());
                    msg.setToTimestamp(protoMsg.getToTimestamp());
                    msg.setVisitRemoves(protoMsg.getVisitTombstones());
                    msg.setVisitInconsistentBuckets(protoMsg.getVisitInconsistentBuckets());
                    msg.setMaxBucketsPerVisitor(protoMsg.getMaxBucketsPerVisitor());
                    for (var protoId : protoMsg.getBucketsList()) {
                        msg.getBuckets().add(fromProtoBucketId(protoId));
                    }
                    for (var protoParam : protoMsg.getParametersList()) {
                        msg.getParameters().put(protoParam.getKey(), protoParam.getValue().toByteArray());
                    }
                    return msg;
                })
                .build();
    }

    static RoutableFactory createCreateVisitorReplyFactory() {
        return ProtobufCodecBuilder
                .of(CreateVisitorReply.class, DocapiVisiting.CreateVisitorResponse.class)
                .encoder((apiReply) -> {
                    var stats = apiReply.getVisitorStatistics();
                    return DocapiVisiting.CreateVisitorResponse.newBuilder()
                            .setLastBucket(toProtoBucketId(apiReply.getLastBucket()))
                            .setStatistics(DocapiVisiting.VisitorStatistics.newBuilder()
                                    .setBucketsVisited(stats.getBucketsVisited())
                                    .setDocumentsVisited(stats.getDocumentsVisited())
                                    .setBytesVisited(stats.getBytesVisited())
                                    .setDocumentsReturned(stats.getDocumentsReturned())
                                    .setBytesReturned(stats.getBytesReturned())
                                    .build())
                            .build();
                })
                .decoder(DocapiVisiting.CreateVisitorResponse.parser(), (protoReply) -> {
                    var reply = new CreateVisitorReply(DocumentProtocol.REPLY_CREATEVISITOR);
                    reply.setLastBucket(fromProtoBucketId(protoReply.getLastBucket()));
                    var protoVs = protoReply.getStatistics();
                    var vs = new VisitorStatistics();
                    vs.setBucketsVisited(protoVs.getBucketsVisited());
                    vs.setDocumentsVisited(protoVs.getDocumentsVisited());
                    vs.setBytesVisited(protoVs.getBytesVisited());
                    vs.setDocumentsReturned(protoVs.getDocumentsReturned());
                    vs.setBytesReturned(protoVs.getBytesReturned());
                    reply.setVisitorStatistics(vs);
                    return reply;
                })
                .build();
    }

    // ---------------------------------------------
    // DestroyVisitor request and response
    // ---------------------------------------------

    static RoutableFactory createDestroyVisitorMessageFactory() {
        return ProtobufCodecBuilder
                .of(DestroyVisitorMessage.class, DocapiVisiting.DestroyVisitorRequest.class)
                .encoder((apiMsg) ->
                        DocapiVisiting.DestroyVisitorRequest.newBuilder()
                                .setInstanceId(apiMsg.getInstanceId())
                                .build())
                .decoder(DocapiVisiting.DestroyVisitorRequest.parser(),
                        (protoMsg) -> new DestroyVisitorMessage(protoMsg.getInstanceId()))
                .build();
    }

    static RoutableFactory createDestroyVisitorReplyFactory() {
        return ProtobufCodecBuilder
                .of(VisitorReply.class, DocapiVisiting.DestroyVisitorResponse.class)
                .encoder((apiReply) -> DocapiVisiting.DestroyVisitorResponse.newBuilder().build())
                .decoder(DocapiVisiting.DestroyVisitorResponse.parser(),
                        (protoReply) -> new VisitorReply(DocumentProtocol.REPLY_DESTROYVISITOR))
                .build();
    }

    // ---------------------------------------------
    // MapVisitor request and response
    // ---------------------------------------------

    static RoutableFactory createMapVisitorMessageFactory() {
        return ProtobufCodecBuilder
                .of(MapVisitorMessage.class, DocapiVisiting.MapVisitorRequest.class)
                .encoder((apiMsg) -> {
                    var builder = DocapiVisiting.MapVisitorRequest.newBuilder();
                    for (var entry : apiMsg.getData().entrySet()) {
                        // FIXME MapVisitorMessage uses Parameters (i.e. string -> bytes) in C++, but string -> string in Java...
                        //  ... but due to this, UTF-8 is effectively enforced anyway. Not that anything actually uses this :I
                        builder.addData(toProtoVisitorParameter(entry.getKey(), entry.getValue().getBytes(StandardCharsets.UTF_8)));
                    }
                    return builder.build();
                })
                .decoder(DocapiVisiting.MapVisitorRequest.parser(), (protoMsg) -> {
                    var msg = new MapVisitorMessage();
                    for (var param : protoMsg.getDataList()) {
                        msg.getData().put(param.getKey(), param.getValue().toStringUtf8());
                    }
                    return msg;
                })
                .build();
    }

    static RoutableFactory createMapVisitorReplyFactory() {
        return ProtobufCodecBuilder
                .of(VisitorReply.class, DocapiVisiting.MapVisitorResponse.class)
                .encoder((apiReply) -> DocapiVisiting.MapVisitorResponse.newBuilder().build())
                .decoder(DocapiVisiting.MapVisitorResponse.parser(),
                        (protoReply) -> new VisitorReply(DocumentProtocol.REPLY_MAPVISITOR))
                .build();
    }

    // ---------------------------------------------
    // QueryResult request and response
    // ---------------------------------------------

    static RoutableFactory createQueryResultMessageFactory() {
        return ProtobufCodecBuilder
                .of(QueryResultMessage.class, DocapiVisiting.QueryResultRequest.class)
                .encoder((apiMsg) -> {
                    // Serialization of QueryResultMessages is not implemented in Java (receive only)
                    throw new UnsupportedOperationException("Serialization of QueryResultMessage instances is not supported");
                })
                .decoder(DocapiVisiting.QueryResultRequest.parser(), (protoMsg) -> {
                    var msg = new QueryResultMessage();
                    // Explicitly enforce presence of result/summary fields, as our object is not necessarily
                    // well-defined if these have not been initialized.
                    if (!protoMsg.hasSearchResult() || !protoMsg.hasDocumentSummary()) {
                        throw new IllegalArgumentException("Query result does not have all required fields set");
                    }
                    // We have to use toByteArray() instead of asReadOnlyByteBuffer(), as the deserialization routines
                    // try to fetch the raw arrays, which are considered mutable (causing a ReadOnlyBufferException).
                    msg.setSearchResult(new SearchResult(new BufferSerializer(
                            protoMsg.getSearchResult().getPayload().toByteArray())));
                    msg.setSummary(new DocumentSummary(new BufferSerializer(
                            protoMsg.getDocumentSummary().getPayload().toByteArray())));
                    return msg;
                })
                .build();
    }

    static RoutableFactory createQueryResultReplyFactory() {
        return ProtobufCodecBuilder
                .of(VisitorReply.class, DocapiVisiting.QueryResultResponse.class)
                .encoder((apiReply) -> DocapiVisiting.QueryResultResponse.newBuilder().build())
                .decoder(DocapiVisiting.QueryResultResponse.parser(),
                        (protoReply) -> new VisitorReply(DocumentProtocol.REPLY_QUERYRESULT))
                .build();
    }

    // ---------------------------------------------
    // VisitorInfo request and response
    // ---------------------------------------------

    static RoutableFactory createVisitorInfoMessageFactory() {
        return ProtobufCodecBuilder
                .of(VisitorInfoMessage.class, DocapiVisiting.VisitorInfoRequest.class)
                .encoder((apiMsg) -> {
                    var builder = DocapiVisiting.VisitorInfoRequest.newBuilder()
                            .setErrorMessage(apiMsg.getErrorMessage());
                    for (var id : apiMsg.getFinishedBuckets()) {
                        builder.addFinishedBuckets(toProtoBucketId(id));
                    }
                    return builder.build();
                })
                .decoder(DocapiVisiting.VisitorInfoRequest.parser(), (protoMsg) -> {
                    var msg = new VisitorInfoMessage();
                    msg.setErrorMessage(protoMsg.getErrorMessage());
                    for (var protoId : protoMsg.getFinishedBucketsList()) {
                        msg.getFinishedBuckets().add(fromProtoBucketId(protoId));
                    }
                    return msg;
                })
                .build();
    }

    static RoutableFactory createVisitorInfoReplyFactory() {
        return ProtobufCodecBuilder
                .of(VisitorReply.class, DocapiVisiting.VisitorInfoResponse.class)
                .encoder((apiReply) -> DocapiVisiting.VisitorInfoResponse.newBuilder().build())
                .decoder(DocapiVisiting.VisitorInfoResponse.parser(),
                        (protoReply) -> new VisitorReply(DocumentProtocol.REPLY_VISITORINFO))
                .build();
    }

    // ---------------------------------------------
    // DocumentList request and response
    // TODO this should be deprecated
    // ---------------------------------------------

    static RoutableFactory createDocumentListMessageFactory() {
        return ProtobufCodecBuilder
                .of(DocumentListMessage.class, DocapiVisiting.DocumentListRequest.class)
                .encoder((apiMsg) -> {
                    var builder = DocapiVisiting.DocumentListRequest.newBuilder()
                            .setBucketId(toProtoBucketId(apiMsg.getBucketId()));
                    for (var doc : apiMsg.getDocuments()) {
                        builder.addEntries(DocapiVisiting.DocumentListRequest.Entry.newBuilder()
                                .setTimestamp(doc.getTimestamp())
                                .setIsTombstone(doc.isRemoveEntry())
                                .setDocument(toProtoDocument(doc.getDocument())));
                    }
                    return builder.build();
                })
                .decoderWithRepo(DocapiVisiting.DocumentListRequest.parser(), (protoMsg, repo) -> {
                    var msg = new DocumentListMessage();
                    msg.setBucketId(fromProtoBucketId(protoMsg.getBucketId()));
                    for (var entry : protoMsg.getEntriesList()) {
                        msg.getDocuments().add(new DocumentListEntry(
                                fromProtoDocument(entry.getDocument(), repo),
                                entry.getTimestamp(),
                                entry.getIsTombstone()));
                    }
                    return msg;
                })
                .build();
    }

    static RoutableFactory createDocumentListReplyFactory() {
        return ProtobufCodecBuilder
                .of(VisitorReply.class, DocapiVisiting.DocumentListResponse.class)
                .encoder((apiReply) -> DocapiVisiting.DocumentListResponse.newBuilder().build())
                .decoder(DocapiVisiting.DocumentListResponse.parser(),
                        (protoReply) -> new VisitorReply(DocumentProtocol.REPLY_DOCUMENTLIST))
                .build();
    }

    // ---------------------------------------------
    // EmptyBuckets request and response
    // TODO this should be deprecated
    // ---------------------------------------------

    static RoutableFactory createEmptyBucketsMessageFactory() {
        return ProtobufCodecBuilder
                .of(EmptyBucketsMessage.class, DocapiVisiting.EmptyBucketsRequest.class)
                .encoder((apiMsg) -> {
                    var builder = DocapiVisiting.EmptyBucketsRequest.newBuilder();
                    for (var id : apiMsg.getBucketIds()) {
                        builder.addBucketIds(toProtoBucketId(id));
                    }
                    return builder.build();
                })
                .decoder(DocapiVisiting.EmptyBucketsRequest.parser(), (protoMsg) -> {
                    var msg = new EmptyBucketsMessage();
                    for (var protoId : protoMsg.getBucketIdsList()) {
                        msg.getBucketIds().add(fromProtoBucketId(protoId));
                    }
                    return msg;
                })
                .build();
    }

    static RoutableFactory createEmptyBucketsReplyFactory() {
        return ProtobufCodecBuilder
                .of(VisitorReply.class, DocapiVisiting.EmptyBucketsResponse.class)
                .encoder((apiReply) -> DocapiVisiting.EmptyBucketsResponse.newBuilder().build())
                .decoder(DocapiVisiting.EmptyBucketsResponse.parser(),
                        (protoReply) -> new VisitorReply(DocumentProtocol.REPLY_EMPTYBUCKETS))
                .build();
    }

    // ---------------------------------------------
    // GetBucketList request and response
    // ---------------------------------------------

    static RoutableFactory createGetBucketListMessageFactory() {
        return ProtobufCodecBuilder
                .of(GetBucketListMessage.class, DocapiInspect.GetBucketListRequest.class)
                .encoder((apiMsg) ->
                        DocapiInspect.GetBucketListRequest.newBuilder()
                                .setBucketId(toProtoBucketId(apiMsg.getBucketId()))
                                .setBucketSpace(toProtoBucketSpace(apiMsg.getBucketSpace()))
                                .build())
                .decoder(DocapiInspect.GetBucketListRequest.parser(), (protoMsg) ->
                        new GetBucketListMessage(
                                fromProtoBucketId(protoMsg.getBucketId()),
                                fromProtoBucketSpace(protoMsg.getBucketSpace())))
                .build();
    }

    static RoutableFactory createGetBucketListReplyFactory() {
        return ProtobufCodecBuilder
                .of(GetBucketListReply.class, DocapiInspect.GetBucketListResponse.class)
                .encoder((apiReply) -> {
                    var builder = DocapiInspect.GetBucketListResponse.newBuilder();
                    for (var info : apiReply.getBuckets()) {
                        builder.addBucketInfo(DocapiInspect.BucketInformation.newBuilder()
                                .setBucketId(toProtoBucketId(info.getBucketId()))
                                .setInfo(info.getBucketInformation()));
                    }
                    return builder.build();
                })
                .decoder(DocapiInspect.GetBucketListResponse.parser(), (protoReply) -> {
                    var reply = new GetBucketListReply();
                    for (var info : protoReply.getBucketInfoList()) {
                        reply.getBuckets().add(new GetBucketListReply.BucketInfo(
                                fromProtoBucketId(info.getBucketId()),
                                info.getInfo()));
                    }
                    return reply;
                })
                .build();
    }

    // ---------------------------------------------
    // GetBucketState request and response
    // ---------------------------------------------

    static RoutableFactory createGetBucketStateMessageFactory() {
        return ProtobufCodecBuilder
                .of(GetBucketStateMessage.class, DocapiInspect.GetBucketStateRequest.class)
                .encoder((apiMsg) ->
                        DocapiInspect.GetBucketStateRequest.newBuilder()
                                .setBucketId(toProtoBucketId(apiMsg.getBucketId()))
                                .build())
                .decoder(DocapiInspect.GetBucketStateRequest.parser(), (protoMsg) ->
                        new GetBucketStateMessage(fromProtoBucketId(protoMsg.getBucketId())))
                .build();
    }

    static RoutableFactory createGetBucketStateReplyFactory() {
        return ProtobufCodecBuilder
                .of(GetBucketStateReply.class, DocapiInspect.GetBucketStateResponse.class)
                .encoder((apiReply) -> {
                    var builder = DocapiInspect.GetBucketStateResponse.newBuilder();
                    for (var state : apiReply.getBucketState()) {
                        var stateBuilder = DocapiInspect.DocumentState.newBuilder()
                                .setTimestamp(state.getTimestamp())
                                .setIsTombstone(state.isRemoveEntry());
                        if (state.hasDocId()) {
                            stateBuilder.setDocumentId(toProtoDocId(state.getDocId()));
                        } else {
                            stateBuilder.setGlobalId(toProtoGlobalId(state.getGid()));
                        }
                        builder.addStates(stateBuilder);
                    }
                    return builder.build();
                })
                .decoder(DocapiInspect.GetBucketStateResponse.parser(), (protoReply) -> {
                    var reply = new GetBucketStateReply();
                    for (var state : protoReply.getStatesList()) {
                        if (state.hasDocumentId()) {
                            reply.getBucketState().add(new DocumentState(
                                    fromProtoDocId(state.getDocumentId()),
                                    state.getTimestamp(),
                                    state.getIsTombstone()));
                        } else {
                            reply.getBucketState().add(new DocumentState(
                                    fromProtoGlobalId(state.getGlobalId()),
                                    state.getTimestamp(),
                                    state.getIsTombstone()));
                        }
                    }
                    return reply;
                })
                .build();
    }

    // ---------------------------------------------
    // StatBucket request and response
    // ---------------------------------------------

    static RoutableFactory createStatBucketMessageFactory() {
        return ProtobufCodecBuilder
                .of(StatBucketMessage.class, DocapiInspect.StatBucketRequest.class)
                .encoder((apiMsg) ->
                        DocapiInspect.StatBucketRequest.newBuilder()
                                .setBucketId(toProtoBucketId(apiMsg.getBucketId()))
                                .setBucketSpace(toProtoBucketSpace(apiMsg.getBucketSpace()))
                                .setSelection(toProtoDocumentSelection(apiMsg.getDocumentSelection()))
                                .build())
                .decoder(DocapiInspect.StatBucketRequest.parser(), (protoMsg) ->
                        new StatBucketMessage(
                                fromProtoBucketId(protoMsg.getBucketId()),
                                fromProtoBucketSpace(protoMsg.getBucketSpace()),
                                fromProtoDocumentSelection(protoMsg.getSelection())))
                .build();
    }

    static RoutableFactory createStatBucketReplyFactory() {
        return ProtobufCodecBuilder
                .of(StatBucketReply.class, DocapiInspect.StatBucketResponse.class)
                .encoder((apiReply) ->
                        DocapiInspect.StatBucketResponse.newBuilder()
                                .setResults(apiReply.getResults())
                                .build())
                .decoder(DocapiInspect.StatBucketResponse.parser(), (protoReply) ->
                        new StatBucketReply(protoReply.getResults()))
                .build();
    }

    // ---------------------------------------------
    // WrongDistribution response (no request type)
    // ---------------------------------------------

    static RoutableFactory createWrongDistributionReplyFactory() {
        return ProtobufCodecBuilder
                .of(WrongDistributionReply.class, DocapiCommon.WrongDistributionResponse.class)
                .encoder((apiReply) ->
                        DocapiCommon.WrongDistributionResponse.newBuilder()
                                .setClusterState(toProtoClusterState(apiReply.getSystemState()))
                                .build())
                .decoder(DocapiCommon.WrongDistributionResponse.parser(), (protoReply) ->
                        new WrongDistributionReply(fromProtoClusterState(protoReply.getClusterState())))
                .build();
    }

    // ---------------------------------------------
    // DocumentIgnored response (no request type)
    // ---------------------------------------------

    static RoutableFactory createDocumentIgnoredReplyFactory() {
        return ProtobufCodecBuilder
                .of(DocumentIgnoredReply.class, DocapiCommon.DocumentIgnoredResponse.class)
                .encoder((apiReply) -> DocapiCommon.DocumentIgnoredResponse.newBuilder().build())
                .decoder(DocapiCommon.DocumentIgnoredResponse.parser(),
                        (protoReply) -> new DocumentIgnoredReply())
                .build();
    }

}
