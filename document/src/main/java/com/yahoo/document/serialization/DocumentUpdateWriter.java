// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.document.update.TensorModifyUpdate;

/**
 * Interface for writing document updates in custom serializers.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.27
 */
public interface DocumentUpdateWriter {
    public void write(DocumentUpdate update);
    public void write(FieldUpdate update);
    public void write(AddValueUpdate update, DataType superType);
    public void write(MapValueUpdate update, DataType superType);
    public void write(ArithmeticValueUpdate update);
    public void write(AssignValueUpdate update, DataType superType);
    public void write(RemoveValueUpdate update, DataType superType);
    public void write(ClearValueUpdate clearValueUpdate, DataType superType);
    public void write(TensorModifyUpdate update);
}
