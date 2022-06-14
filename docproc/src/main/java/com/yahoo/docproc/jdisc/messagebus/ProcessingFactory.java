// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc.messagebus;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.core.document.ContainerDocumentConfig;
import com.yahoo.docproc.AbstractConcreteDocumentFactory;
import com.yahoo.docproc.Processing;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.messagebus.Message;

/**
 * @author Simon Thoresen Hult
 */
class ProcessingFactory {

    private final static Logger log = Logger.getLogger(ProcessingFactory.class.getName());
    private final ComponentRegistry<AbstractConcreteDocumentFactory> docFactoryRegistry;
    private final ContainerDocumentConfig containerDocConfig;
    private final String serviceName;

    public ProcessingFactory(ComponentRegistry<AbstractConcreteDocumentFactory> docFactoryRegistry,
                             ContainerDocumentConfig containerDocConfig,
                             String serviceName) {
        this.docFactoryRegistry = docFactoryRegistry;
        this.containerDocConfig = containerDocConfig;
        this.serviceName = serviceName;
    }

    public List<Processing> fromMessage(Message message) {
        List<Processing> processings = new ArrayList<>();
        switch (message.getType()) {
            case DocumentProtocol.MESSAGE_PUTDOCUMENT: {
                PutDocumentMessage putMessage = (PutDocumentMessage) message;
                DocumentPut putOperation = new DocumentPut(createPutDocument(putMessage));
                putOperation.setCondition(putMessage.getCondition());
                processings.add(createProcessing(putOperation, message));
                break;
            }
            case DocumentProtocol.MESSAGE_UPDATEDOCUMENT: {
                UpdateDocumentMessage updateMessage = (UpdateDocumentMessage) message;
                DocumentUpdate updateOperation = updateMessage.getDocumentUpdate();
                updateOperation.setCondition(updateMessage.getCondition());
                processings.add(createProcessing(updateOperation, message));
                break;
            }
            case DocumentProtocol.MESSAGE_REMOVEDOCUMENT: {
                RemoveDocumentMessage removeMessage = (RemoveDocumentMessage) message;
                DocumentRemove removeOperation = new DocumentRemove(removeMessage.getDocumentId());
                removeOperation.setCondition(removeMessage.getCondition());
                processings.add(createProcessing(removeOperation, message));
                break;
            }
        }
        return processings;
    }

    private Document createPutDocument(PutDocumentMessage msg) {
        Document document = msg.getDocumentPut().getDocument();
        String typeName = document.getDataType().getName();
        ContainerDocumentConfig.Doctype typeConfig = getDocumentConfig(typeName);
        if (typeConfig == null) return document;

        return createConcreteDocument(document, typeConfig);
    }

    private Document createConcreteDocument(Document document, ContainerDocumentConfig.Doctype typeConfig) {
        String componentId = typeConfig.factorycomponent(); // Class name of the factory
        AbstractConcreteDocumentFactory cdf = docFactoryRegistry.getComponent(new ComponentId(componentId));
        if (cdf == null) {
            log.fine("Unable to get document factory component '" + componentId + "' from document factory registry.");
            return document;
        }
        return cdf.getDocumentCopy(document.getDataType().getName(), document, document.getId());
    }

    private ContainerDocumentConfig.Doctype getDocumentConfig(String name) {
        for (ContainerDocumentConfig.Doctype type : containerDocConfig.doctype()) {
            if (name.equals(type.type())) {
                return type;
            }
        }
        return null;
    }

    private Processing createProcessing(DocumentOperation documentOperation, Message message) {
        Processing processing = new Processing();
        processing.addDocumentOperation(documentOperation);
        processing.setServiceName(serviceName);

        processing.setVariable("route", message.getRoute());
        processing.setVariable("timeout", message.getTimeRemaining());
        return processing;
    }

}
