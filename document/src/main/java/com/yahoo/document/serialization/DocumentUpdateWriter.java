// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.update.AddValueUpdate;
import com.yahoo.document.update.ArithmeticValueUpdate;
import com.yahoo.document.update.AssignValueUpdate;
import com.yahoo.document.update.ClearValueUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.MapValueUpdate;
import com.yahoo.document.update.RemoveValueUpdate;
import com.yahoo.document.update.TensorAddUpdate;
import com.yahoo.document.update.TensorModifyUpdate;
import com.yahoo.document.update.TensorRemoveUpdate;

/**
 * Interface for writing document updates in custom serializers.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.27
 */
public interface DocumentUpdateWriter {
    void write(DocumentUpdate update);
    void write(FieldUpdate update);
    void write(AddValueUpdate update, DataType superType);
    void write(MapValueUpdate update, DataType superType);
    void write(ArithmeticValueUpdate update);
    void write(AssignValueUpdate update, DataType superType);
    void write(RemoveValueUpdate update, DataType superType);
    void write(ClearValueUpdate clearValueUpdate, DataType superType);
    void write(TensorModifyUpdate update);
    void write(TensorAddUpdate update);
    void write(TensorRemoveUpdate update);
}
