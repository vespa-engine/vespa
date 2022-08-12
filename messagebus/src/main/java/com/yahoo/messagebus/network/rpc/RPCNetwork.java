// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * An RPC implementation of the Network interface.
 *
 * @author havardpe
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
            new ThreadPoolExecutor(getNumThreads(), getNumThreads(), 0L, TimeUnit.SECONDS,
                                   new LinkedBlockingQueue<>(),
                                   ThreadFactoryFactory.getDaemonThreadFactory("mbus.net"));

    private static int getNumThreads() {
        return Math.max(2, Runtime.getRuntime().availableProcessors()/2);
    }

    private static boolean shouldEnableTcpNodelay(RPCNetworkParams.Optimization optimization) {
        return optimization == RPCNetworkParams.Optimization.LATENCY;
    }

    /**
     * Create an RPCNetwork. The servicePrefix is combined with session names to create service names. If the service
     * prefix is 'a/b' and the session name is 'c', the resulting service name that identifies the session on the
     * message bus will be 'a/b/c'
     *
     * @param params        a complete set of parameters
     * @param slobrokConfig subscriber for slobroks config
     */
    private RPCNetwork(RPCNetworkParams params, SlobrokConfigSubscriber slobrokConfig) {
        this.slobroksConfig = slobrokConfig;
        identity = params.getIdentity();
        orb = new Supervisor(new Transport("mbus-rpc-" + identity.getServicePrefix(), params.getNumNetworkThreads(),
                shouldEnableTcpNodelay(params.getOptimization()), params.getTransportEventsBeforeWakeup()));
        orb.setMaxInputBufferSize(params.getMaxInputBufferSize());
        orb.setMaxOutputBufferSize(params.getMaxOutputBufferSize());
        targetPool = new RPCTargetPool(params.getConnectionExpireSecs(), params.getNumTargetsPerSpec());
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
     * @param params a complete set of parameters
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
        int millis = (int) seconds * 1000;
        int i = 0;
        do {
            if (mirror.ready()) {
                if (i > 200) {
                    log.log(Level.INFO, "network became ready (at "+i+" ms)");
                }
                return true;
            }
            if ((i == 200) || ((i > 200) && ((i % 1000) == 0))) {
                log.log(Level.INFO, "waiting for network to become ready ("+i+" of "+millis+" ms)");
                mirror.dumpState();
            }
            try {
                // could maybe have some back-off here, fixed at 10ms for now
                i += 10;
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // empty
            }
        } while (i < millis);
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

        sendAdapters.put(new Version(6,149), new RPCSendV2(this));
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
        orb.transport().sync();
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
        Duration timeout = Duration.ofMillis(ctx.msg.getTimeRemainingNow());
        for (RoutingNode recipient : ctx.recipients) {
            RPCServiceAddress address = (RPCServiceAddress)recipient.getServiceAddress();
            address.getTarget().resolveVersion(timeout, ctx);
        }
    }

    private static String buildRecipientListString(SendContext ctx) {
        return ctx.recipients.stream().map(r -> {
            if (!(r.getServiceAddress() instanceof RPCServiceAddress)) {
                return "<non-RPC service address>";
            }
            RPCServiceAddress addr = (RPCServiceAddress)r.getServiceAddress();
            return String.format("%s at %s", addr.getServiceName(), addr.getConnectionSpec());
        }).collect(Collectors.joining(", "));
    }

    /**
     * This method is a callback invoked after {@link #send(Message, List)} once the version of all recipients have been
     * resolved. If all versions were resolved ahead of time, this method is invoked by the same thread as the former.
     * If not, this method is invoked by the network thread during the version callback.
     *
     * @param ctx all the required send-data
     */
    private void send(SendContext ctx) {
        if (destroyed.get()) {
            replyError(ctx, ErrorCode.NETWORK_SHUTDOWN, "Network layer has performed shutdown.");
        } else if (ctx.hasError) {
            replyError(ctx, ErrorCode.HANDSHAKE_FAILED,
                    String.format("An error occurred while resolving version of recipient(s) [%s] from host '%s'.",
                                  buildRecipientListString(ctx), identity.getHostname()));
        } else {
            new SendTask(owner.getProtocol(ctx.msg.getProtocol()), ctx).run();
        }
    }

    /**
     * Sets the destroyed flag to true. The very first time this method is called, it cleans up all its dependencies.
     * Even if you retain a reference to this object, all of its content is allowed to be garbage collected.
     *
     * @return true if content existed and was destroyed
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
     * @return the version to claim to be
     */
    protected Version getVersion() {
        return Vtag.currentVersion;
    }

    /**
     * Resolves and assigns a service address for the given recipient using the given address. This is called by the
     * {@link #allocServiceAddress(RoutingNode)} method. The target allocated here is released when the routing node
     * calls {@link #freeServiceAddress(RoutingNode)}.
     *
     * @param recipient   the recipient to assign the service address to
     * @param serviceName the name of the service to resolve
     * @return any error encountered, or null
     */
    public Error resolveServiceAddress(RoutingNode recipient, String serviceName) {
        RPCServiceAddress ret = servicePool.resolve(serviceName);
        if (ret == null) {
            return new Error(ErrorCode.NO_ADDRESS_FOR_SERVICE,
                             String.format("The address of service '%s' could not be resolved. It is not currently " +
                                           "registered with the Vespa name server. " +
                                           "The service must be having problems, or the routing configuration is wrong. " +
                                           "Address resolution attempted from host '%s'", serviceName, identity.getHostname()));
        }
        RPCTarget target = targetPool.getTarget(orb, ret);
        if (target == null) {
            return new Error(ErrorCode.CONNECTION_ERROR,
                             String.format("Failed to connect to service '%s' from host '%s'.",
                                           serviceName, identity.getHostname()));
        }
        ret.setTarget(target); // free by freeServiceAddress()
        recipient.setServiceAddress(ret);
        return null; // no error
    }

    /**
     * Determines and returns the send adapter that is compatible with the given version. If no adapter can be found,
     * this method returns null.
     *
     * @param version the version for which to return an adapter
     * @return the compatible adapter
     */
    public RPCSendAdapter getSendAdapter(Version version) {
        Map.Entry<Version, RPCSendAdapter> lower = sendAdapters.floorEntry(version);
        return (lower != null) ? lower.getValue() : null;
    }

    /**
     * Deliver an error reply to the recipients of a {@link SendContext} in a way that avoids entanglement.
     *
     * @param ctx     the send context that contains the recipient data
     * @param errCode the error code to return
     * @param errMsg  the error string to return
     */
    private void replyError(SendContext ctx, int errCode, String errMsg) {
        for (RoutingNode recipient : ctx.recipients) {
            Reply reply = new EmptyReply();
            reply.getTrace().setLevel(ctx.traceLevel);
            reply.addError(new Error(errCode, errMsg));
            recipient.handleReply(reply);
        }
    }

    /** Returns the owner of this network. */
    NetworkOwner getOwner() {
        return owner;
    }

    /** Returns the identity of this network. */
    public Identity getIdentity() {
        return identity;
    }

    /** Returns the port number this network listens to. */
    public int getPort() {
        return listener.port();
    }

    /** Returns the JRT supervisor. */
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
                } else if (version.isBefore(this.version)) {
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
     * Implements a helper class to invoke {@link RPCTargetPool#flushTargets(boolean)} once every second.
     * This is to untangle the target pool from the scheduler.
     */
    private static class TargetPoolTask implements Runnable {

        final RPCTargetPool pool;
        final Task jrtTask;

        TargetPoolTask(RPCTargetPool pool, Supervisor orb) {
            this.pool = pool;
            this.jrtTask = orb.transport().selectThread().createTask(this);
            this.jrtTask.schedule(1.0);
        }

        @Override
        public void run() {
            pool.flushTargets(false);
            jrtTask.schedule(1.0);
        }
    }

}
