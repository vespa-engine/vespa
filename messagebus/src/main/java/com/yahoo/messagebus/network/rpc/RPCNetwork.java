// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.jrt.Acceptor;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.MethodHandler;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Task;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.slobrok.api.IMirror;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.jrt.slobrok.api.Register;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Protocol;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.Network;
import com.yahoo.messagebus.network.NetworkOwner;
import com.yahoo.messagebus.routing.Hop;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingNode;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * An RPC implementation of the Network interface.
 *
 * @author <a href="mailto:havardpe@yahoo-inc.com">Haavard Pettersen</a>
 */
public class RPCNetwork implements Network, MethodHandler {

    private static final Logger log = Logger.getLogger(RPCNetwork.class.getName());
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private final Identity identity;
    private final Supervisor orb;
    private final RPCTargetPool targetPool;
    private final RPCServicePool servicePool;
    private final Acceptor listener;
    private final Mirror mirror;
    private final Register register;
    private final TreeMap<Version, RPCSendAdapter> sendAdapters = new TreeMap<>();
    private NetworkOwner owner;
    private final SlobrokConfigSubscriber slobroksConfig;
    private final LinkedHashMap<String, Route> lruRouteMap = new LinkedHashMap<>(10000, 0.5f, true);
    private final ExecutorService executor =
            new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(),
                                   0L, TimeUnit.SECONDS,
                                   new SynchronousQueue<>(false),
                                   ThreadFactoryFactory.getDaemonThreadFactory("mbus.net"), new ThreadPoolExecutor.CallerRunsPolicy());

    /**
     * Create an RPCNetwork. The servicePrefix is combined with session names to create service names. If the service
     * prefix is 'a/b' and the session name is 'c', the resulting service name that identifies the session on the
     * message bus will be 'a/b/c'
     *
     * @param params        A complete set of parameters.
     * @param slobrokConfig subscriber for slobroks config
     */
    public RPCNetwork(RPCNetworkParams params, SlobrokConfigSubscriber slobrokConfig) {
        this.slobroksConfig = slobrokConfig;
        identity = params.getIdentity();
        orb = new Supervisor(new Transport());
        orb.setMaxInputBufferSize(params.getMaxInputBufferSize());
        orb.setMaxOutputBufferSize(params.getMaxOutputBufferSize());
        targetPool = new RPCTargetPool(params.getConnectionExpireSecs());
        servicePool = new RPCServicePool(this, 4096);

        Method method = new Method("mbus.getVersion", "", "s", this);
        method.methodDesc("Retrieves the message bus version.");
        method.returnDesc(0, "version", "The message bus version.");
        orb.addMethod(method);

        try {
            listener = orb.listen(new Spec(params.getListenPort()));
        } catch (ListenFailedException e) {
            orb.transport().shutdown().join();
            throw new RuntimeException(e);
        }
        TargetPoolTask task = new TargetPoolTask(targetPool, orb);
        task.jrtTask.scheduleNow();
        register = new Register(orb, slobrokConfig.getSlobroks(), identity.getHostname(), listener.port());
        mirror = new Mirror(orb, slobrokConfig.getSlobroks());
    }

    /**
     * Create an RPCNetwork. The servicePrefix is combined with session names to create service names. If the service
     * prefix is 'a/b' and the session name is 'c', the resulting service name that identifies the session on the
     * message bus will be 'a/b/c'
     *
     * @param params A complete set of parameters.
     */
    public RPCNetwork(RPCNetworkParams params) {
        this(params, params.getSlobroksConfig() != null ? new SlobrokConfigSubscriber(params.getSlobroksConfig())
                                                        : new SlobrokConfigSubscriber(params.getSlobrokConfigId()));
    }

    /**
     * The network uses a cache of RPC targets (see {@link RPCTargetPool}) that allows it to save time by reusing open
     * connections. It works by keeping a set of the most recently used targets open. Calling this method forces all
     * unused connections to close immediately.
     */
    protected void flushTargetPool() {
        targetPool.flushTargets(true);
    }

    final Route getRoute(String routeString) {
        Route route = lruRouteMap.get(routeString);
        if (route == null) {
            route = Route.parse(routeString);
            lruRouteMap.put(routeString, route);
        }
        return new Route(route);
    }

    @Override
    public boolean waitUntilReady(double seconds) {
        for (int i = 0; i < seconds * 100; ++i) {
            if (mirror.ready()) {
                return true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // empty
            }
        }
        return false;
    }

    @Override
    public boolean allocServiceAddress(RoutingNode recipient) {
        Hop hop = recipient.getRoute().getHop(0);
        String service = hop.getServiceName();
        Error error = resolveServiceAddress(recipient, service);
        if (error == null) {
            return true; // service address resolved
        }
        recipient.setError(error);
        return false; // service address not resolved
    }

    @Override
    public void freeServiceAddress(RoutingNode recipient) {
        RPCTarget target = ((RPCServiceAddress)recipient.getServiceAddress()).getTarget();
        if (target != null) {
            target.subRef();
        }
        recipient.setServiceAddress(null);
    }

    @Override
    public void attach(NetworkOwner owner) {
        if (this.owner != null) {
            throw new IllegalStateException("Network is already attached to another owner.");
        }
        this.owner = owner;

        RPCSendAdapter adapter1 = new RPCSendV1();
        RPCSendAdapter adapter2 = new RPCSendV2();
        addSendAdapter(new Version(5), adapter1);
        addSendAdapter(new Version(6,149), adapter2);
    }

    @Override
    public void registerSession(String session) {
        register.registerName(identity.getServicePrefix() + "/" + session);
    }

    @Override
    public void unregisterSession(String session) {
        register.unregisterName(identity.getServicePrefix() + "/" + session);
    }

    @Override
    public void sync() {
        SyncTask sh = new SyncTask();
        orb.transport().perform(sh);
        sh.await();
    }

    @Override
    public void shutdown() {
        destroy();
    }

    @Override
    public String getConnectionSpec() {
        return "tcp/" + identity.getHostname() + ":" + listener.port();
    }

    @Override
    public IMirror getMirror() {
        return mirror;
    }

    @Override
    public void invoke(Request request) {
        request.returnValues().add(new StringValue(getVersion().toString()));
    }

    @Override
    public void send(Message msg, List<RoutingNode> recipients) {
        SendContext ctx = new SendContext(this, msg, recipients);
        double timeout = ctx.msg.getTimeRemainingNow() / 1000.0;
        for (RoutingNode recipient : ctx.recipients) {
            RPCServiceAddress address = (RPCServiceAddress)recipient.getServiceAddress();
            address.getTarget().resolveVersion(timeout, ctx);
        }
    }

    /**
     * This method is a callback invoked after {@link #send(Message, List)} once the version of all recipients have been
     * resolved. If all versions were resolved ahead of time, this method is invoked by the same thread as the former.
     * If not, this method is invoked by the network thread during the version callback.
     *
     * @param ctx All the required send-data.
     */
    private void send(SendContext ctx) {
        if (destroyed.get()) {
            replyError(ctx, ErrorCode.NETWORK_SHUTDOWN, "Network layer has performed shutdown.");
        } else if (ctx.hasError) {
            replyError(ctx, ErrorCode.HANDSHAKE_FAILED, "An error occured while resolving version.");
        } else {
            executor.execute(new SendTask(owner.getProtocol(ctx.msg.getProtocol()), ctx));
        }
    }

    /**
     * Sets the destroyed flag to true. The very first time this method is called, it cleans up all its dependencies.
     * Even if you retain a reference to this object, all of its content is allowed to be garbage collected.
     *
     * @return True if content existed and was destroyed.
     */
    public boolean destroy() {
        if (!destroyed.getAndSet(true)) {
            if (slobroksConfig != null) {
                slobroksConfig.shutdown();
            }
            register.shutdown();
            mirror.shutdown();
            listener.shutdown().join();
            orb.transport().shutdown().join();
            targetPool.flushTargets(true);
            executor.shutdown();
            return true;
        }
        return false;
    }

    /**
     * Returns the version of this network. This gets called when the "mbus.getVersion" method is invoked on this
     * network, and is separated into its own function so that unit tests can override it to simulate other versions
     * than current.
     *
     * @return The version to claim to be.
     */
    protected Version getVersion() {
        return Vtag.currentVersion;
    }

    /**
     * Resolves and assigns a service address for the given recipient using the given address. This is called by the
     * {@link #allocServiceAddress(RoutingNode)} method. The target allocated here is released when the routing node
     * calls {@link #freeServiceAddress(RoutingNode)}.
     *
     * @param recipient   The recipient to assign the service address to.
     * @param serviceName The name of the service to resolve.
     * @return Any error encountered, or null.
     */
    public Error resolveServiceAddress(RoutingNode recipient, String serviceName) {
        RPCServiceAddress ret = servicePool.resolve(serviceName);
        if (ret == null) {
            return new Error(ErrorCode.NO_ADDRESS_FOR_SERVICE,
                             "The address of service '" + serviceName + "' could not be resolved. It is not currently " +
                             "registered with the Vespa name server. " +
                             "The service must be having problems, or the routing configuration is wrong.");
        }
        RPCTarget target = targetPool.getTarget(orb, ret);
        if (target == null) {
            return new Error(ErrorCode.CONNECTION_ERROR,
                             "Failed to connect to service '" + serviceName + "'.");
        }
        ret.setTarget(target); // free by freeServiceAddress()
        recipient.setServiceAddress(ret);
        return null; // no error
    }

    /**
     * Registers a send adapter for a given version. This will overwrite whatever is already registered under the same
     * version.
     *
     * @param version The version for which to register an adapter.
     * @param adapter The adapter to register.
     */
    private void addSendAdapter(Version version, RPCSendAdapter adapter) {
        adapter.attach(this);
        sendAdapters.put(version, adapter);
    }

    /**
     * Determines and returns the send adapter that is compatible with the given version. If no adapter can be found,
     * this method returns null.
     *
     * @param version The version for which to return an adapter.
     * @return The compatible adapter.
     */
    public RPCSendAdapter getSendAdapter(Version version) {
        Map.Entry<Version, RPCSendAdapter> lower = sendAdapters.floorEntry(version);
        return (lower != null) ? lower.getValue() : null;
    }

    /**
     * Deliver an error reply to the recipients of a {@link SendContext} in a way that avoids entanglement.
     *
     * @param ctx     The send context that contains the recipient data.
     * @param errCode The error code to return.
     * @param errMsg  The error string to return.
     */
    private void replyError(SendContext ctx, int errCode, String errMsg) {
        for (RoutingNode recipient : ctx.recipients) {
            Reply reply = new EmptyReply();
            reply.getTrace().setLevel(ctx.traceLevel);
            reply.addError(new Error(errCode, errMsg));
            owner.deliverReply(reply, recipient);
        }
    }

    /**
     * Get the owner of this network
     *
     * @return network owner
     */
    NetworkOwner getOwner() {
        return owner;
    }

    /**
     * Returns the identity of this network.
     *
     * @return The identity.
     */
    public Identity getIdentity() {
        return identity;
    }

    /**
     * Obtain the port number this network listens to
     *
     * @return listening port number
     */
    public int getPort() {
        return listener.port();
    }

    /**
     * Returns the JRT supervisor.
     *
     * @return The supervisor.
     */
    Supervisor getSupervisor() {
        return orb;
    }

    ExecutorService getExecutor() {
        return executor;
    }

    private class SendTask implements Runnable {

        final Protocol protocol;
        final SendContext ctx;

        SendTask(Protocol protocol, SendContext ctx) {
            this.protocol = protocol;
            this.ctx = ctx;
        }

        public void run() {
            long timeRemaining = ctx.msg.getTimeRemainingNow();
            if (timeRemaining <= 0) {
                replyError(ctx, ErrorCode.TIMEOUT, "Aborting transmission because zero time remains.");
                return;
            }
            byte[] payload;
            try {
                payload = protocol.encode(ctx.version, ctx.msg);
            } catch (Exception e) {
                StringWriter out = new StringWriter();
                e.printStackTrace(new PrintWriter(out));
                replyError(ctx, ErrorCode.ENCODE_ERROR, out.toString());
                return;
            }
            if (payload == null || payload.length == 0) {
                replyError(ctx, ErrorCode.ENCODE_ERROR,
                           "Protocol '" + ctx.msg.getProtocol() + "' failed to encode message.");
                return;
            }
            RPCSendAdapter adapter = getSendAdapter(ctx.version);
            if (adapter == null) {
                replyError(ctx, ErrorCode.INCOMPATIBLE_VERSION,
                           "Can not send to version '" + ctx.version + "' recipient.");
                return;
            }
            for (RoutingNode recipient : ctx.recipients) {
                adapter.send(recipient, ctx.version, payload, timeRemaining);
            }
        }
    }

    /**
     * Implements a helper class for {@link RPCNetwork#sync()}. It provides a blocking method {@link #await()} that will
     * wait until the internal state of this object is set to 'done'. By scheduling this task in the network thread and
     * then calling this method, we achieve handshaking with the network thread.
     */
    private static class SyncTask implements Runnable {

        final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void run() {
            latch.countDown();
        }

        public void await() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    /**
     * Implements a helper class for {@link RPCNetwork#send(com.yahoo.messagebus.Message, java.util.List)}. It works by
     * encapsulating all the data required for sending a message, but postponing the call to {@link
     * RPCNetwork#send(com.yahoo.messagebus.network.rpc.RPCNetwork.SendContext)} until the version of all targets have
     * been resolved.
     */
    private static class SendContext implements RPCTarget.VersionHandler {

        final RPCNetwork net;
        final Message msg;
        final int traceLevel;
        final List<RoutingNode> recipients = new LinkedList<>();
        boolean hasError = false;
        int pending;
        Version version;

        SendContext(RPCNetwork net, Message msg, List<RoutingNode> recipients) {
            this.net = net;
            this.msg = msg;
            this.traceLevel = this.msg.getTrace().getLevel();
            this.recipients.addAll(recipients);
            this.pending = this.recipients.size();
            this.version = this.net.getVersion();
        }

        @Override
        public void handleVersion(Version version) {
            boolean shouldSend = false;
            synchronized (this) {
                if (version == null) {
                    hasError = true;
                } else if (version.compareTo(this.version) < 0) {
                    this.version = version;
                }
                if (--pending == 0) {
                    shouldSend = true;
                }
            }
            if (shouldSend) {
                net.send(this);
            }
        }
    }

    /**
     * Implements a helper class to invoke {@link RPCTargetPool#flushTargets(boolean)} once every second. This is to
     * unentangle the target pool from the scheduler.
     */
    private static class TargetPoolTask implements Runnable {

        final RPCTargetPool pool;
        final Task jrtTask;

        TargetPoolTask(RPCTargetPool pool, Supervisor orb) {
            this.pool = pool;
            this.jrtTask = orb.transport().createTask(this);
            this.jrtTask.schedule(1.0);
        }

        @Override
        public void run() {
            pool.flushTargets(false);
            jrtTask.schedule(1.0);
        }
    }
}
