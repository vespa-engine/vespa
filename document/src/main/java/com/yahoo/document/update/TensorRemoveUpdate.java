// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.google.common.collect.ImmutableSet;
import com.yahoo.document.DataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.serialization.DocumentUpdateWriter;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;

import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 *  An update used to remove cells from a sparse tensor (has only mapped dimensions).
 *
 *  The cells to remove are contained in a set of addresses.
 */
public class TensorRemoveUpdate extends ValueUpdate {

    private TensorType tensorType;
    private ImmutableSet<TensorAddress> addresses;

    public TensorRemoveUpdate(TensorType tensorType, Set<TensorAddress> addresses) {
        super(ValueUpdateClassID.TENSORREMOVE);
        this.tensorType = tensorType;
        this.addresses = ImmutableSet.copyOf(addresses);
    }

    @Override
    protected void checkCompatibility(DataType fieldType) {
        if (!(fieldType instanceof TensorDataType)) {
            throw new UnsupportedOperationException("Expected tensor type, got " + fieldType.getName() + ".");
        }
    }

    @Override
    public void serialize(DocumentUpdateWriter data, DataType superType) {
        // TODO: implement
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public FieldValue applyTo(FieldValue oldValue) {
        // TODO: implement
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public FieldValue getValue() {
        return null;
    }

    public TensorType getTensorType() {
        return tensorType;
    }

    public Set<TensorAddress> getAddresses() {
        return addresses;
    }

    @Override
    public void setValue(FieldValue value) {
        // Ignore
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), addresses);
    }

    @Override
    public String toString() {
        return super.toString() + " " + toStringWithType();
    }

    public String toStringWithType() {
        StringJoiner sj = new StringJoiner(",", "[", "]");
        for (TensorAddress address : addresses) {
            sj.add(address.toString(tensorType));
        }
        return sj.toString();
    }

}
