// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

/**
 * @author Simon Thoresen Hult
 */
class OffsetContinuation extends EncodableContinuation {

    public static final int FLAG_UNSTABLE = 1;
    private final ResultId resultId;
    private final int tag;
    private final int offset;
    private final int flags;

    public OffsetContinuation(ResultId resultId, int tag, int offset, int flags) {
        resultId.getClass(); // throws NullPointerException
        this.resultId = resultId;
        this.tag = tag;
        this.offset = offset;
        this.flags = flags;
    }

    public ResultId getResultId() {
        return resultId;
    }

    public int getTag() {
        return tag;
    }

    public int getOffset() {
        return offset;
    }

    public int getFlags() {
        return flags;
    }

    public boolean testFlag(int flag) {
        return (flags & flag) != 0;
    }

    @Override
    public int hashCode() {
        return resultId.hashCode() + offset + flags;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof OffsetContinuation)) {
            return false;
        }
        OffsetContinuation rhs = (OffsetContinuation)obj;
        if (!resultId.equals(rhs.resultId)) {
            return false;
        }
        if (tag != rhs.tag) {
            return false;
        }
        if (offset != rhs.offset) {
            return false;
        }
        if (flags != rhs.flags) {
            return false;
        }
        return true;
    }

    @Override
    public void encode(IntegerEncoder out) {
        resultId.encode(out);
        out.append(tag);
        out.append(offset);
        out.append(flags);
    }

    public static OffsetContinuation decode(IntegerDecoder in) {
        ResultId resultId = ResultId.decode(in);
        int tag = in.next();
        int offset = in.next();
        int flags = in.next();
        return new OffsetContinuation(resultId, tag, offset, flags);
    }
}
