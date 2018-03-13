// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.dummyreceiver;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.documentapi.ThroughputLimitQueue;
import com.yahoo.documentapi.messagebus.MessageBusDocumentAccess;
import com.yahoo.documentapi.messagebus.MessageBusParams;
import com.yahoo.documentapi.messagebus.loadtypes.LoadTypeSet;
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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.lang.System.out;

public class DummyReceiver implements MessageHandler {
    String name = null;
    DestinationSession session;
    MessageBusDocumentAccess da;
    long sleepTime = 0;
    long messageCount = 0;
    int maxPendingCount = 0;
    long silentNum = 0;
    boolean instant = false;
    ThreadPoolExecutor executor = null;
    int threads = 10;
    long maxQueueTime = -1;
    BlockingQueue<Runnable> queue;
    boolean verbose = false;
    private boolean helpOption = false;

    DummyReceiver() {
    }

    public class Task implements Runnable {
        Reply reply;

        public Task(Reply reply) {
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

    public void init() {
        MessageBusParams params = new MessageBusParams(new LoadTypeSet());
        params.setRPCNetworkParams(new RPCNetworkParams().setIdentity(new Identity(name)));
        params.setDocumentManagerConfigId("client");
        params.getMessageBusParams().setMaxPendingCount(maxPendingCount);
        params.getMessageBusParams().setMaxPendingSize(0);
        da = new MessageBusDocumentAccess(params);
        queue = (maxQueueTime < 0) ? new LinkedBlockingDeque<>() : new ThroughputLimitQueue<>(maxQueueTime);
        session = da.getMessageBus().createDestinationSession("default", true, this);
        executor = new ThreadPoolExecutor(threads, threads, 5, TimeUnit.SECONDS, queue, new DaemonThreadFactory());
        System.out.println("Registered listener at " + name + "/default with " + maxPendingCount + " max pending and sleep time of " + sleepTime);
    }

    public void handleMessage(Message message) {
        messageCount++;
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

    String getParam(List<String> args, String arg) throws IllegalArgumentException {
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

    boolean parseArgs(List<String> args) {
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
                } else if ("--maxqueuetime".equals(arg)) {
                    maxQueueTime = Long.parseLong(getParam(args, arg));
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

        List<String> l = new LinkedList<>();
        for (String arg : args) {
            l.add(arg);
        }
        if (!rcv.parseArgs(l) && !rcv.helpOption) {
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
