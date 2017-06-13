// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.BucketId;
import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.DocumentId;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.metrics.MetricSet;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingContext;
import com.yahoo.messagebus.routing.RoutingNodeIterator;
import com.yahoo.vdslib.BucketDistribution;

import java.util.*;
import java.util.logging.Logger;

/**
 * <p>This policy implements the logic to select recipients for a single search column. It has 2 different modes of
 * operation;</p>
 *
 * <ol>
 * <li>If the "maxbadparts" parameter is 0, select recipient based on document id hash and use
 * shared merge logic. Do not allow any out-of-service replies.</li>
 * <li>Else do best-effort validation of system
 * state. This means;
 * <ol>
 * <li>if the message is sending to all recipients (typicall start- and
 * end-of-feed), allow at most "maxbadparts" out-of-service replies,</li>
 * <li>else always allow out-of-service reply by masking it with an empty
 * reply.</li>
 * </ol>
 * </li>
 * </ol>
 * <p>For systems that allow bad parts, one will not know whether or not feeding
 * was a success until the RTX attempts to set the new index live, because it is
 * only the RTX that is now able to verify that the service level requirements
 * are met. Feeding will still break if a message that was supposed to be sent
 * to all recipients receives more than "maxbadparts" out-of-service replies,
 * according to (2.a) above.</p>
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class SearchColumnPolicy implements DocumentProtocolRoutingPolicy {

    private static Logger log = Logger.getLogger(SearchColumnPolicy.class.getName());
    private BucketIdFactory factory = new BucketIdFactory();
    private Map<Integer, BucketDistribution> distributions = new HashMap<Integer, BucketDistribution>();
    private int maxOOS = 0; // The maximum OUT_OF_SERVICE replies to hide.

    public static final int DEFAULT_NUM_BUCKET_BITS = 16;

    /**
     * Constructs a new policy object for the given parameter string. The string can be null or empty, which is a
     * request to not allow any bad columns.
     *
     * @param param The maximum number of allowed bad columns.
     */
    public SearchColumnPolicy(String param) {
        if (param != null && param.length() > 0) {
            try {
                maxOOS = Integer.parseInt(param);
            } catch (NumberFormatException e) {
                log.log(LogLevel.WARNING, "Parameter '" + param + "' could not be parsed as an integer.", e);
            }
            if (maxOOS < 0) {
                log.log(LogLevel.WARNING, "Ignoring a request to set the maximum number of OOS replies to " + maxOOS +
                                          " because it makes no sense. This routing policy will not allow any recipient" +
                                          " to be out of service.");
            }
        }
    }

    @Override
    public void select(RoutingContext context) {
        List<Route> recipients = context.getMatchedRecipients();
        if (recipients == null || recipients.size() == 0) {
            return;
        }
        DocumentId id = null;
        BucketId bucketId = null;
        Message msg = context.getMessage();
        switch (msg.getType()) {

            case DocumentProtocol.MESSAGE_PUTDOCUMENT:
                id = ((PutDocumentMessage)msg).getDocumentPut().getDocument().getId();
                break;

            case DocumentProtocol.MESSAGE_GETDOCUMENT:
                id = ((GetDocumentMessage)msg).getDocumentId();
                break;

            case DocumentProtocol.MESSAGE_REMOVEDOCUMENT:
                id = ((RemoveDocumentMessage)msg).getDocumentId();
                break;

            case DocumentProtocol.MESSAGE_UPDATEDOCUMENT:
                id = ((UpdateDocumentMessage)msg).getDocumentUpdate().getId();
                break;

            case DocumentProtocol.MESSAGE_BATCHDOCUMENTUPDATE:
                bucketId = ((BatchDocumentUpdateMessage)msg).getBucketId();
                break;

            case DocumentProtocol.MESSAGE_GETBUCKETSTATE:
                bucketId = ((GetBucketStateMessage)msg).getBucketId();
                break;

            default:
                throw new UnsupportedOperationException("Message type '" + msg.getType() + "' not supported.");
        }
        if (bucketId == null && id != null) {
            bucketId = factory.getBucketId(id);
        }
        int recipient = getRecipient(bucketId, recipients.size());
        context.addChild(recipients.get(recipient));
        context.setSelectOnRetry(true);
        if (maxOOS > 0) {
            context.addConsumableError(ErrorCode.SERVICE_OOS);
        }
    }

    @Override
    public void merge(RoutingContext context) {
        if (maxOOS > 0) {
            if (context.getNumChildren() > 1) {
                Set<Integer> oosReplies = new HashSet<Integer>();
                int idx = 0;
                for (RoutingNodeIterator it = context.getChildIterator();
                     it.isValid(); it.next())
                {
                    Reply ref = it.getReplyRef();
                    if (ref.hasErrors() && DocumentProtocol.hasOnlyErrorsOfType(ref, ErrorCode.SERVICE_OOS)) {
                        oosReplies.add(idx);
                    }
                    ++idx;
                }
                if (oosReplies.size() <= maxOOS) {
                    DocumentProtocol.merge(context, oosReplies);
                    return; // may the rtx be with you
                }
            } else {
                Reply ref = context.getChildIterator().getReplyRef();
                if (ref.hasErrors() && DocumentProtocol.hasOnlyErrorsOfType(ref, ErrorCode.SERVICE_OOS)) {
                    context.setReply(new EmptyReply());
                    return; // god help us all
                }
            }
        }
        DocumentProtocol.merge(context);
    }

    /**
     * Returns the recipient index for the given bucket id. This updates the shared internal distribution map, so it
     * needs to be synchronized.
     *
     * @param bucketId      The bucket whose recipient to return.
     * @param numRecipients The number of recipients being distributed to.
     * @return The recipient to use.
     */
    private synchronized int getRecipient(BucketId bucketId, int numRecipients) {
        BucketDistribution distribution = distributions.get(numRecipients);
        if (distribution == null) {
            distribution = new BucketDistribution(1, DEFAULT_NUM_BUCKET_BITS);
            distribution.setNumColumns(numRecipients);
            distributions.put(numRecipients, distribution);
        }
        return distribution.getColumn(bucketId);
    }

    @Override
    public void destroy() {
        // empty
    }

    @Override
    public MetricSet getMetrics() {
        return null;
    }
}
