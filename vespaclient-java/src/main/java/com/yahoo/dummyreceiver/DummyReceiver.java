// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.dummyreceiver;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.documentapi.messagebus.MessageBusDocumentAccess;
import com.yahoo.documentapi.messagebus.MessageBusParams;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.log.LogSetup;
import com.yahoo.messagebus.DestinationSession;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageHandler;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.System.out;

public class DummyReceiver implements MessageHandler {
    private String name = null;
    private DestinationSession session;
    private MessageBusDocumentAccess da;
    private long sleepTime = 0;
    private AtomicLong messageCount = new AtomicLong(0);
    private long silentNum = 0;
    private boolean instant = false;
    private ThreadPoolExecutor executor = null;
    private int threads = 10;
    private BlockingQueue<Runnable> queue;
    private boolean verbose = false;
    private boolean helpOption = false;

    private DummyReceiver() {
    }

    class Task implements Runnable {
        Reply reply;

        Task(Reply reply) {
            this.reply = reply;
        }

        public void run() {
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            session.reply(reply);
        }
    }

    private void init() {
        MessageBusParams params = new MessageBusParams();
        params.setRPCNetworkParams(new RPCNetworkParams().setIdentity(new Identity(name)));
        params.setDocumentManagerConfigId("client");
        params.getMessageBusParams().setMaxPendingCount(0);
        da = new MessageBusDocumentAccess(params);
        queue = new LinkedBlockingDeque<>();
        session = da.getMessageBus().createDestinationSession("default", true, this);
        executor = new ThreadPoolExecutor(threads, threads, 5, TimeUnit.SECONDS, queue, new DaemonThreadFactory());
        System.out.println("Registered listener at " + name + "/default with 0 max pending and sleep time of " + sleepTime);
    }

    @SuppressWarnings("deprecation")
    public void handleMessage(Message message) {
        long messageCount = this.messageCount.incrementAndGet();
        if ( silentNum == 0 ) {
            System.out.println("Received message " + message + ". Received " + messageCount + " messages so far. In queue size " + queue.size());

            if (verbose) {
                if (message instanceof PutDocumentMessage) {
                    System.out.println("  Document:\n" + ((PutDocumentMessage) message).getDocumentPut().getDocument().toXML("  "));
                } else if (message instanceof RemoveDocumentMessage) {
                    System.out.println("  Document id: " + ((RemoveDocumentMessage) message).getDocumentId());
                } else if (message instanceof UpdateDocumentMessage) {
                    System.out.println("  Update:\n  " + ((UpdateDocumentMessage) message).getDocumentUpdate().toString());
                }
            }
        } else {
            if ((messageCount % silentNum) == 0) {
                System.out.println("Received " + messageCount + " messages so far. In queue size " + queue.size());
            }
        }

        EmptyReply reply = new EmptyReply();
        message.swapState(reply);

        if ( ! instant ) {
            try {
                executor.execute(new Task(reply));
            } catch (RejectedExecutionException e) {
                reply.addError(new Error(ErrorCode.SESSION_BUSY, "Session " + name + "/default is busy"));
                session.reply(reply);
            }
        } else {
            session.reply(reply);
        }
    }

    private String getParam(List<String> args, String arg) throws IllegalArgumentException {
        try {
            return args.remove(0);
        } catch (Exception e) {
            System.err.println("--" + arg + " requires an argument");
            throw new IllegalArgumentException(arg);
        }
    }

    public void help() {
        out.println("Simple receiver for messagebus messages. Prints the messages received to stdout.\n" +
                    "\n" +
                    "The options are:\n" +
                    "  --instant          Reply in message thread." +
                    "  --name arg         Slobrok name to register\n" +
                    "  --maxqueuetime arg Adjust the in queue size to have a maximum queue wait period of this many ms (default -1 = unlimited)\n" +
                    "  --silent #nummsg   Do not dump anything, but progress every #nummsg\n" +
                    "  --sleeptime arg    The number of milliseconds to sleep per message, to simulate processing time\n" +
                    "  --threads arg      The number of threads to process the incoming data\n" +
                    "  --verbose          If set, dump the contents of certain messages to stdout");
    }

    private boolean parseArgs(List<String> args) {
        try {
            while (!args.isEmpty()) {
                String arg = args.remove(0);

                if (arg.equals("-h") || arg.equals("--help")) {
                    help();
                    helpOption = true;
                    return false;
                } else if ("--name".equals(arg)) {
                    name = getParam(args, arg);
                } else if ("--sleeptime".equals(arg)) {
                    sleepTime = Long.parseLong(getParam(args, arg));
                } else if ("--instant".equals(arg)) {
                    instant = true;
                } else if ("--silent".equals(arg)) {
                    silentNum = Long.parseLong(getParam(args, arg));
                } else if ("--threads".equals(arg)) {
                    threads = Integer.parseInt(getParam(args, arg));
                } else if ("--verbose".equals(arg)) {
                    verbose = true;
                } else {
                    help();
                    helpOption = true;
                    return false;
                }
            }

            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static void main(String[] args) {
        LogSetup.initVespaLogging("dummyreceiver");
        DummyReceiver rcv = new DummyReceiver();

        if (!rcv.parseArgs(new LinkedList<>(Arrays.asList(args))) && !rcv.helpOption) {
            System.exit(1);
        }
        if (rcv.helpOption) {
            System.exit(0); // exit with success instead of returning
        }

        rcv.init();
        while (true) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
