// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi;

import com.yahoo.document.DocumentGet;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.documentapi.DocumentOperationParameters;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author jonmv
 */
public class DocumentOperationExecutorMock implements DocumentOperationExecutor {

    final AtomicReference<DocumentOperation> lastOperation = new AtomicReference<>();
    final AtomicReference<DocumentOperationParameters> lastParameters = new AtomicReference<>();
    final AtomicReference<OperationContext> lastOperationContext = new AtomicReference<>();
    final AtomicReference<VisitorOptions> lastOptions = new AtomicReference<>();
    final AtomicReference<VisitOperationsContext> lastVisitContext = new AtomicReference<>();

    @Override
    public void get(DocumentId id, DocumentOperationParameters parameters, OperationContext context) {
        setLastOperation(new DocumentGet(id), parameters, context);
    }

    @Override
    public void put(DocumentPut put, DocumentOperationParameters parameters, OperationContext context) {
        setLastOperation(put, parameters, context);
    }

    @Override
    public void update(DocumentUpdate update, DocumentOperationParameters parameters, OperationContext context) {
        setLastOperation(update, parameters, context);
    }

    @Override
    public void remove(DocumentId id, DocumentOperationParameters parameters, OperationContext context) {
        setLastOperation(new DocumentRemove(id), parameters, context);
    }

    @Override
    public void visit(VisitorOptions options, VisitOperationsContext context) {
        lastOptions.set(options);
        lastVisitContext.set(context);
    }

    @Override
    public String routeToCluster(String cluster) {
        return "route-to-" + cluster;
    }

    public DocumentOperation lastOperation() {
        return lastOperation.get();
    }

    public DocumentOperationParameters lastParameters() {
        return lastParameters.get();
    }

    public OperationContext lastOperationContext() {
        return lastOperationContext.get();
    }

    public VisitorOptions lastOptions() {
        return lastOptions.get();
    }

    public VisitOperationsContext lastVisitContext() {
        return lastVisitContext.get();
    }

    private void setLastOperation(DocumentOperation operation, DocumentOperationParameters parameters, OperationContext context) {
        lastOperation.set(operation);
        lastParameters.set(parameters);
        lastOperationContext.set(context);
    }

}
