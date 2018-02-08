// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.defaults.Defaults;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.text.Lowercase.toLowerCase;

/**
 * Superclass for all Processes.
 *
 * @author gjoranv
 */
public abstract class AbstractService extends AbstractConfigProducer<AbstractConfigProducer<?>> implements Service {

    private static final long serialVersionUID = 1L;

    // The physical host this Service runs on.
    private HostResource hostResource = null;

    /**
     * Identifier that ensures that multiple instances of the same
     * Service subclass will have unique names on the host. The first
     * instance of one kind of Service will have the id 1, and the id
     * will increase by 1 for each new instance.
     * TODO: Do something more intelligent in Host?
     */
    private int id = 0;

    /** The actual base port for this Service. */
    private int basePort;

    /** The ports allocated to this Service. */
    private List<Integer> ports = new ArrayList<>();

    /** The optional JVM execution args for this Service. */
    // Please keep non-null, as passed to command line in service startup
    private String jvmArgs = "";

    /** The optional PRELOAD libraries for this Service. */
    // Please keep non-null, as passed to command line in service startup
    private String preload = Defaults.getDefaults().underVespaHome("lib64/vespa/malloc/libvespamalloc.so");

    // If larger or equal to 0 it mean that explicit mmaps shall not be included in coredump.
    private long mmapNoCoreLimit = -1l;

    // If this is true it will dump core when OOM
    private boolean coreOnOOM = false;

    private String noVespaMalloc = "";
    private String vespaMalloc = "";
    private String vespaMallocDebug = "";
    private String vespaMallocDebugStackTrace = "";

    /** The ports metainfo object */
    protected PortsMeta portsMeta = new PortsMeta();

    /**
     * Custom properties that a service may populate to communicate
     * more key/value pairs to the service-list dump.
     * Supported key datatypes are String, and values may be String or Integer.
     */
    private Map<String, Object> serviceProperties = new LinkedHashMap<>();

    /** The affinity properties of this service. */
    private Optional<Affinity> affinity = Optional.empty();

    private boolean initialized = false;

    /**
     * Preferred constructor when building from XML. Use this if you are building
     * in doBuild() in an AbstractConfigProducerBuilder.
     * build() will call initService() in that case, after setting hostalias and baseport.
     * @param parent Parent config producer in the model tree.
     * @param name   Name of this service.
     */
    public AbstractService(AbstractConfigProducer parent, String name) {
        super(parent, name);
    }

    /**
     * Only used for testing. Stay away.
     * @param name   Name of this service.
     */
    public AbstractService(String name) {
        super(name);
    }

    /**
     * Distribute affinity on a collection of services. Services that are located on the same host
     * will be assigned a specific cpu socket on that host.
     *
     * @param services A {@link Collection} of services of the same type, not necessarily on the same host.
     */
    public static <SERVICE extends AbstractService> void distributeCpuSocketAffinity(Collection<SERVICE> services) {
        Map<HostResource, List<SERVICE>> affinityMap = new HashMap<>();
        for (SERVICE service : services) {
            if (!affinityMap.containsKey(service.getHostResource())) {
                affinityMap.put(service.getHostResource(), new ArrayList<>());
            }
            int cpuSocket = affinityMap.get(service.getHostResource()).size();
            affinityMap.get(service.getHostResource()).add(service);
            service.setAffinity(new Affinity.Builder().cpuSocket(cpuSocket).build());
        }
    }

    /**
     * Helper method to avoid replicating code.
     *
     * @param hostResource The physical host on which this service should run.
     * @param userPort The wanted port given by the user.
     */
    private void initService(HostResource hostResource, int userPort) {
        if (initialized) {
            throw new IllegalStateException("Service '" + getConfigId() + "' already initialized.");
        }
        if (hostResource == null) {
            throw new RuntimeException("No host found for service '" + getServiceName() + "'. " +
                                       "The hostalias is probably missing from hosts.xml.");
        }
        id = getIndex(hostResource);
        ports = hostResource.allocateService(this, getInstanceWantedPort(userPort));
        initialized = true;
    }

    /**
     * Called by builder class which has not given the host or port in a constructor, hence
     * initService is not yet run for this.
     */
    public void initService() {
        initService(this.hostResource, this.basePort);
    }

    /**
     * Returns the desired base port for the first instance of the
     * service type. Returns '0' as default, which means that the
     * service type should use the default port allocation mechanism.
     *
     * @return The desired base port for the first instance of the service type.
     */
    public int getWantedPort() {
        return 0;
    }

    /**
     * Returns the desired base port for this service instance, '0' if
     * it should use the default port allocation mechanism.
     *
     * @param userWantedPort The wanted port given by the user.
     * @return The desired base port for this service instance, '0' by default
     */
    private int getInstanceWantedPort(int userWantedPort) {
        int wantedPort = 0;
        if (userWantedPort == 0) {
            if (requiresWantedPort())
                wantedPort = getWantedPort();
            else if (getWantedPort() > 0)
                wantedPort = getWantedPort() + ((getId() - 1) * getPortCount());
        } else {
            // User defined from spec
            wantedPort = userWantedPort;
/*            if ((wantedPort >= Host.BASE_PORT) &&
                    (wantedPort <= (Host.BASE_PORT + Host.MAX_PORTS))) {
                throw new RuntimeException
                        ("Attribute 'basePort=" + wantedPort +
                                "' is not allowed to be inside Vespa's reserved port range "
                                + Host.BASE_PORT + "-"
                                + (Host.BASE_PORT + Host.MAX_PORTS) + ".");
            }
*/
        }
        return wantedPort;
    }

    /**
     * Override if the desired base port (returned by getWantedPort()) is the only allowed base port.
     *
     * @return false by default
     */
    public boolean requiresWantedPort() {
        return false;
    }

    /**
     * Override if the services does not require consecutive port numbers. I.e. if any ports
     * in addition to the baseport should be allocated from Vespa's default port range.
     *
     * @return true by default
     */
    public boolean requiresConsecutivePorts() {
        return true;
    }

    /**
     * Gets the ports metainfo object. The service implementation
     * must populate this object in the constructor.
     */
    public PortsMeta getPortsMeta() {
        return portsMeta;
    }

    /**
     * Computes and returns the i'th port for this service, based on
     * this Service's baseport.
     *
     * @param i The offset from 'basePort' of the port to return
     * @return the i'th port relative to the base port.
     * @throws IllegalStateException if i is out of range.
     */
    public int getRelativePort(int i) {
        if (ports.size() < 1) {
            throw new IllegalStateException
                    ("Requested port with offset " + i + " for service that " +
                            "has not reserved any ports: " + this);
        }
        if (i >= ports.size()) {
            throw new IllegalStateException
                    ("Requested port with offset " + i + " for service that " +
                            "only has reserved " + ports.size() + " ports: " + this);
        }
        return ports.get(i);
    }

    /**
     * Must be overridden by services that should be started by
     * config-sentinel. The returned value will be used in
     * config-sentinel configuration. Returns null by default.
     *
     * @return null by default.
     */
    public String getStartupCommand() {
        return null;
    }

    public Optional<String> getPreShutdownCommand() {
        return Optional.empty();
    }

    /**
     * Tells if this service should be autostarted by
     * config-sentinel. Returned value will be used to configure the
     * config-sentinel.
     *
     * @return true by default.
     */
    public boolean getAutostartFlag() {
        return true;
    }

    /**
     * Tells if this service should be autorestarted by
     * config-sentinel. Returned value will be used to configure the
     * config-sentinel.
     *
     * @return true by default.
     */
    public boolean getAutorestartFlag() {
        return true;
    }

    /**
     * Returns the name that identifies this service for the config-sentinel.
     *
     * @return the name that identifies this service for the config-sentinel.
     */
    public String getServiceName() {
        return getServiceType() + ((id == 1) ? "" : Integer.toString(id));
    }

    /**
     * Returns the type of service. This is the class name without the
     * package prefix by default.
     */
    public String getServiceType() {
        return toLowerCase(getShortClassName());
    }

    /**
     * Strips the package prefix and returns the short classname.
     *
     * @return classname without package prefix.
     */
    private String getShortClassName() {
        Class myClass = getClass();
        Package myPackage = myClass.getPackage();
        return myClass.getName().substring(1 + myPackage.getName().length());
    }

    /**
     * @return the physical host on which this service runs.
     */
    public Host getHost() {
        if (hostResource != null) {
            return hostResource.getHost();
        } else {
            return null;
        }
    }

    /**
     * @return The hostname on which this service runs.
     */
    public String getHostName() {
        return hostResource.getHostname();
    }

    /**
     * @return The id (index) of this service on the host where it runs
     */
    public int getId() {
        return id;
    }

    /**
     * Computes a number that identifies the service on the given
     * host. The number of services of the same type (Class) is
     * counted and the number is returned.
     *
     * @param host the host on which the service will run
     * @return id number for the given service.
     */
    // TODO: Do something more intelligent in the Host class..?
    protected int getIndex(HostResource host) {
        int i = 0;
        for (Service s : host.getServices()) {
            //if (s.getClass().equals(getClass()) && (s != this)) {
            if (s.getServiceType().equals(getServiceType()) && (s != this)) {
                i++;
            }
        }
        return i + 1;
    }

    @Override
    public ServiceInfo getServiceInfo() {
        Set<PortInfo> portInfos = new LinkedHashSet<>();
        for (int i = 0; i < portsMeta.getNumPorts(); i++) {
            portInfos.add(new PortInfo(ports.get(i), new LinkedHashSet<>(portsMeta.getTagsAt(i))));
        }
        Map<String, String> properties = new LinkedHashMap<>();
        for (Map.Entry<String, Object> prop : serviceProperties.entrySet()) {
            properties.put(prop.getKey(), prop.getValue().toString());
        }
        return new ServiceInfo(getServiceName(), getServiceType(), portInfos, properties, getConfigId(), getHostName());
    }

    /**
     * Sets a service property value for the given key.
     *
     * @param key   a key used for this property
     * @param value a String value associated with the key
     * @return this service
     */
    public AbstractService setProp(String key, String value) {
        serviceProperties.put(key, value);
        return this;
    }

    /**
     * Sets a service property value for the given key.
     *
     * @param key   a key used for this property
     * @param value an Integer value associated with the key
     * @return this service
     */
    public AbstractService setProp(String key, Integer value) {
        serviceProperties.put(key, value);
        return this;
    }

    /**
     * Gets a service property value mapped to the given key
     * as a String, or null if no such key exists.
     *
     * @param key a key used for lookup in the service properties
     * @return the associated String value for the given key, or null
     */
    public String getServicePropertyString(String key) {
        return getServicePropertyString(key, null);
    }

    public String getServicePropertyString(String key, String defStr) {
        Object result = serviceProperties.get(key);
        return (result == null) ? defStr : result.toString();
    }

    /** Optional execution args for this service */
    public String getJvmArgs() {
        return jvmArgs;
    }
    public void setJvmArgs(String args) {
        jvmArgs = (args == null) ? "" : args;
    }
    public void appendJvmArgs(String args) {
        if ((args != null) && ! "".equals(args)) {
            setJvmArgs(jvmArgs + getSeparator(jvmArgs) + args);
        }
    }
    private static String getSeparator(String current) {
        return ("".equals(current)) ? "" : " ";
    }
    public void prependJvmArgs(String args) {
        if ((args != null) && ! "".equals(args)) {
            setJvmArgs(args + getSeparator(jvmArgs) + jvmArgs);
        }
    }
    public String getPreLoad() { return preload; }
    public void setPreLoad(String preload) {
        this.preload = preload;
    }
    public long getMMapNoCoreLimit() { return mmapNoCoreLimit; }
    public void setMMapNoCoreLimit(long noCoreLimit) { this.mmapNoCoreLimit = noCoreLimit; }
    public boolean getCoreOnOOM() { return coreOnOOM; }
    public void setCoreOnOOM(boolean coreOnOOM) { this.coreOnOOM = coreOnOOM; }

    public String getNoVespaMalloc() { return noVespaMalloc; }
    public String getVespaMalloc() { return vespaMalloc; }
    public String getVespaMallocDebug() { return vespaMallocDebug; }
    public String getVespaMallocDebugStackTrace() { return vespaMallocDebugStackTrace; }
    public void setNoVespaMalloc(String s) { noVespaMalloc = s; }
    public void setVespaMalloc(String s) { vespaMalloc = s; }
    public void setVespaMallocDebug(String s) { vespaMallocDebug = s; }
    public void setVespaMallocDebugStackTrace(String s) { vespaMallocDebugStackTrace = s; }

    public String getMMapNoCoreEnvVariable() {
        return (getMMapNoCoreLimit() >= 0l)
                ? "VESPA_MMAP_NOCORE_LIMIT=" + getMMapNoCoreLimit() + " "
                : "";
    }

    public String getCoreOnOOMEnvVariable() {
        return getCoreOnOOM() ? "" : "VESPA_SILENCE_CORE_ON_OOM=true ";
    }
    public String getNoVespaMallocEnvVariable() {
        return "".equals(getNoVespaMalloc())
                ? ""
                : "VESPA_USE_NO_VESPAMALLOC=\"" + getNoVespaMalloc() + "\" ";
    }
    public String getVespaMallocEnvVariable() {
        return "".equals(getVespaMalloc())
                ? ""
                : "VESPA_USE_VESPAMALLOC=\"" + getVespaMalloc() + "\" ";
    }
    public String getVespaMallocDebugEnvVariable() {
        return "".equals(getVespaMallocDebug())
                ? ""
                : "VESPA_USE_VESPAMALLOC_D=\"" + getVespaMallocDebug() + "\" ";
    }
    public String getVespaMallocDebugStackTraceEnvVariable() {
        return "".equals(getVespaMallocDebugStackTrace())
                ? ""
                : "VESPA_USE_VESPAMALLOC_DST=\"" + getVespaMallocDebugStackTrace() + "\" ";
    }

    public String getEnvVariables() {
        return getCoreOnOOMEnvVariable() + getMMapNoCoreEnvVariable() + getNoVespaMallocEnvVariable() +
                getVespaMallocEnvVariable() + getVespaMallocDebugEnvVariable() + getVespaMallocDebugStackTraceEnvVariable();
    }

    /**
     * WARNING: should only be called before initService(), otherwise call at own risk!
     */
    public void setBasePort(int wantedPort) {
        this.basePort = wantedPort;
    }

    /** Temporary hack: reserve port with index 0
     *  Must be done this way since the system test framework
     *  currently uses the first port as container http port.
     */
    public void reservePortPrepended(int port) {
        hostResource.reservePort(this, port);
        ports.add(0, port);
    }

    public void setHostResource(HostResource hostResource) {
        this.hostResource = hostResource;
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Add the given file to the application's file distributor.
     *
     * @param relativePath path to the file, relative to the app package.
     * @return the file reference hash
     */
    public FileReference sendFile(String relativePath) {
        return getRoot().getFileDistributor().sendFileToHost(relativePath, getHost());
    }
    public FileReference sendUri(String uri) {
        return getRoot().getFileDistributor().sendUriToHost(uri, getHost());
    }

    /**
     *
     * The service HTTP port for health status
     * @return portnumber
     */
    public int getHealthPort() { return -1;}

    /**
     * Overridden by subclasses. List of default dimensions to be added to this services metrics
     * @return The default dimensions for this service
     */
    public HashMap<String, String> getDefaultMetricDimensions(){ return new LinkedHashMap<>(); }

    // For testing
    public int getNumPortsAllocated() {
        return ports.size();
    }

    public HostResource getHostResource() {
        return hostResource;
    }

    public Optional<Affinity> getAffinity() {
        return affinity;
    }

    public void setAffinity(Affinity affinity) {
        this.affinity = Optional.ofNullable(affinity);
    }

    @Override
    public String toString() {
        return getServiceName() + " on " + (getHost() == null ? "no host" : getHost().toString());
    }
}
