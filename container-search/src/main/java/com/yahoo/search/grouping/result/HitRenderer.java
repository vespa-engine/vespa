// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

import com.yahoo.search.grouping.Continuation;
import com.yahoo.search.result.HitGroup;
import com.yahoo.text.Utf8String;
import com.yahoo.text.XMLWriter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

/**
 * This is a helper class for rendering grouping results.
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public abstract class HitRenderer {

    private static final Utf8String ATR_LABEL = new Utf8String("label");
    private static final Utf8String ATR_RELEVANCE = new Utf8String("relevance");
    private static final Utf8String ATR_TYPE = new Utf8String("type");
    private static final Utf8String TAG_BUCKET_FROM = new Utf8String("from");
    private static final Utf8String TAG_BUCKET_TO = new Utf8String("to");
    private static final Utf8String TAG_CONTINUATION = new Utf8String("continuation");
    private static final Utf8String TAG_CONTINUATION_ID = new Utf8String("id");
    private static final Utf8String TAG_GROUP_LIST = new Utf8String("grouplist");
    private static final Utf8String TAG_GROUP = new Utf8String("group");
    private static final Utf8String TAG_GROUP_ID = new Utf8String("id");
    private static final Utf8String TAG_HIT_LIST = new Utf8String("hitlist");
    private static final Utf8String TAG_OUTPUT = new Utf8String("output");

    /**
     * Renders the header for the given grouping hit. If the hit is not a grouping hit, this method does nothing and
     * returns false.
     * <p>Post-condition if this is a grouping hit: The hit tag is open.
     *
     * @param hit    The hit whose header to render.
     * @param writer The writer to render to.
     * @return True if the hit was rendered.
     * @throws IOException Thrown if there was a problem writing.
     */
    public static boolean renderHeader(HitGroup hit, XMLWriter writer) throws IOException {
        if (hit instanceof GroupList) {
            writer.openTag(TAG_GROUP_LIST).attribute(ATR_LABEL, ((GroupList)hit).getLabel());
            renderContinuations(((GroupList)hit).continuations(), writer);
        } else if (hit instanceof Group) {
            writer.openTag(TAG_GROUP).attribute(ATR_RELEVANCE, hit.getRelevance().toString());
            renderGroupId(((Group)hit).getGroupId(), writer);
            if (hit instanceof RootGroup) {
                renderContinuation(Continuation.THIS_PAGE, ((RootGroup)hit).continuation(), writer);
            }
            for (String label : hit.fieldKeys()) {
                writer.openTag(TAG_OUTPUT).attribute(ATR_LABEL, label).content(hit.getField(label), false).closeTag();
            }
        } else if (hit instanceof HitList) {
            writer.openTag(TAG_HIT_LIST).attribute(ATR_LABEL, ((HitList)hit).getLabel());
            renderContinuations(((HitList)hit).continuations(), writer);
        } else {
            return false;
        }
        writer.closeStartTag();
        return true;
    }

    private static void renderGroupId(GroupId id, XMLWriter writer) {
        writer.openTag(TAG_GROUP_ID).attribute(ATR_TYPE, id.getTypeName());
        if (id instanceof ValueGroupId) {
            writer.content(getIdValue((ValueGroupId)id), false);
        } else if (id instanceof BucketGroupId) {
            BucketGroupId bucketId = (BucketGroupId)id;
            writer.openTag(TAG_BUCKET_FROM).content(getBucketFrom(bucketId), false).closeTag();
            writer.openTag(TAG_BUCKET_TO).content(getBucketTo(bucketId), false).closeTag();
        }
        writer.closeTag();
    }

    private static Object getIdValue(ValueGroupId id) {
        return id instanceof RawId ? Arrays.toString(((RawId)id).getValue()) : id.getValue();
    }

    private static Object getBucketFrom(BucketGroupId id) {
        return id instanceof RawBucketId ? Arrays.toString(((RawBucketId)id).getFrom()) : id.getFrom();
    }

    private static Object getBucketTo(BucketGroupId id) {
        return id instanceof RawBucketId ? Arrays.toString(((RawBucketId)id).getTo()) : id.getTo();
    }

    private static void renderContinuations(Map<String, Continuation> continuations, XMLWriter writer) {
        for (Map.Entry<String, Continuation> entry : continuations.entrySet()) {
            renderContinuation(entry.getKey(), entry.getValue(), writer);
        }
    }

    private static void renderContinuation(String id, Continuation continuation, XMLWriter writer) {
        writer.openTag(TAG_CONTINUATION).attribute(TAG_CONTINUATION_ID, id).content(continuation, false).closeTag();
    }
}
