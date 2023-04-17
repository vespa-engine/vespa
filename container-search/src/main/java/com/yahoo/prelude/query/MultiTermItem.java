// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.nio.ByteBuffer;

/**
 * A term which wraps a set of similar simple terms, all joined with {@code AND} or {@code OR}.
 * <p>
 * This class exists to encompass similarities in the serialization of such multi-term constructs.
 *
 * @author jonmv
 */
abstract class MultiTermItem extends SimpleTaggableItem {

    enum OperatorType {

        AND(0),
        OR(1);

        private final byte code;

        OperatorType(int code) { this.code = (byte) code; }

    }

    enum TermType {

        RANGES(0);

        private final byte code;

        TermType(int code) { this.code = (byte) code; }

    }

    /** The operator used to join all wrapped terms. */
    abstract OperatorType operatorType();

    /** The term type of the wrapped terms. */
    abstract TermType termType();

    /** The number of wrapped terms. */
    abstract int terms();

    /** Encode term type and common properties to the buffer. */
    abstract void encodeBlueprint(ByteBuffer buffer);

    /** Encode all wrapped terms to the buffer. */
    abstract void encodeTerms(ByteBuffer buffer);

    abstract Item asCompositeItem();

    @Override
    public final ItemType getItemType() {
        return ItemType.MULTI_TERM;
    }

    @Override
    public final String getName() {
        return getItemType().name();
    }

    @Override
    public final int encode(ByteBuffer buffer) {
        // TODO: Remove once backend support deserialisation of this type.
        if (getClass() == MultiRangeItem.class) return asCompositeItem().encode(buffer);

        super.encodeThis(buffer);
        byte metadata = 0;
        metadata |= (byte)((byte)(operatorType().code << 5) & (byte)0b11100000);
        metadata |= (byte)(termType().code & (byte)0b00011111);
        buffer.put(metadata);
        buffer.putInt(terms());
        encodeBlueprint(buffer);
        encodeTerms(buffer);
        return 1;
    }

    @Override
    public final int getTermCount() {
        return 1;
    }

}
