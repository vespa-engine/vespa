// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.defaults.Defaults;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.yahoo.text.Lowercase.toLowerCase;

/**
 * Superclass for all Processes.
 *
 * @author gjoranv
 */
public abstract class AbstractService extends TreeConfigProducer<AnyConfigProducer> implements Service {

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
    private int basePort = 0;

    /** The ports allocated to this Service. */
    private List<Integer> ports = new ArrayList<>();

    /** The optional JVM execution options for this Service. */
    // Please keep non-null, as passed to command line in service startup
    private String jvmOptions = "";

    /** The optional PRELOAD libraries for this Service. */
    // Please keep non-null, as passed to command line in service startup
    private String preload = null;

    private final Map<String, Object> environmentVariables = new TreeMap<>();

    /** The ports metainfo object */
    protected PortsMeta portsMeta = new PortsMeta();

    /**
     * Custom properties that a service may populate to communicate
     * more key/value pairs to the service-list dump.
     * Supported key datatypes are String, and values may be String or Integer.
     */
    private final Map<String, Object> serviceProperties = new LinkedHashMap<>();

    /** The affinity properties of this service. */
    private Optional<Affinity> affinity = Optional.empty();

    private boolean initialized = false;

    protected String defaultPreload() {
        return Defaults.getDefaults().underVespaHome("lib64/vespa/malloc/libvespamalloc.so");
    }

    /**
     * Preferred constructor when building from XML. Use this if you are building
     * in doBuild() in an TreeConfigProducerBuilder.
     * build() will call initService() in that case, after setting hostalias and baseport.
     *
     * @param parent the parent config producer in the model tree
     * @param name   the name of this service
     */
    public AbstractService(TreeConfigProducer<?> parent, String name) {
        super(parent, name);
        environmentVariables.put("VESPA_SILENCE_CORE_ON_OOM", true);
    }

    /**
     * Only used for testing. Stay away.
     *
     * @param name the name of this service.
     */
    public AbstractService(String name) {
        this(null, name);
    }

    @Override
    public void remove() {
        super.remove();
        if (hostResource != null)
            hostResource.deallocateService(this);
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
    private void initService(DeployState deployState, HostResource hostResource, int userPort) {
        if (initialized) {
            throw new IllegalStateException("Service '" + getConfigId() + "' already initialized.");
        }
        if (hostResource == null) {
            throw new IllegalArgumentException("No host found for service '" + getServiceName() + "'. " +
                                               "The hostalias is probably missing from hosts.xml.");
        }
        id = getIndex(hostResource);
        ports = hostResource.allocateService(deployState.getDeployLogger(), this, getInstanceWantedPort(userPort));
        initialized = true;
        for(String envVar : deployState.getProperties().environmentVariables()) {
            addEnvironmentVariable(envVar);
        }
    }

    /**
     * Called by builder class which has not given the host or port in a constructor, hence
     * initService is not yet run for this.
     */
    public void initService(DeployState deployState) {
        initService(deployState, this.hostResource, this.basePort);
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
     * Gets the ports metainfo object. The service implementation must populate this object in the constructor.
     */
    public PortsMeta getPortsMeta() {
        return portsMeta;
    }

    /**
     * Computes and returns the i'th port for this service, based on this Service's baseport.
     *
     * @param i the offset from 'basePort' of the port to return
     * @return the i'th port relative to the base port.
     * @throws IllegalStateException if i is out of range.
     */
    public int getRelativePort(int i) {
        if (ports.isEmpty()) {
            throw new IllegalStateException("Requested port with offset " + i + " for service that " +
                                            "has not reserved any ports: " + this);
        }
        if (i >= ports.size()) {
            throw new IllegalStateException("Requested port with offset " + i + " for service that " +
                                            "only has reserved " + ports.size() + " ports: " + this);
        }
        return ports.get(i);
    }

    /**
     * Must be overridden by services that should be started by
     * config-sentinel. The returned value will be used in
     * config-sentinel configuration. Returns empty by default.
     *
     * @return empty by default.
     */
    public Optional<String> getStartupCommand() { return Optional.empty(); }

    public Optional<String> getPreShutdownCommand() {
        return Optional.empty();
    }

    /** Returns the name that identifies this service for the config-sentinel, never null */
    @Override
    public String getServiceName() {
        return getServiceType() + ((id == 1) ? "" : Integer.toString(id));
    }

    /**
     * Returns the type of service. This is the class name without the
     * package prefix by default, never null
     */
    @Override
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

    @Override
    public HostResource getHost() { return hostResource; }

    @Override
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

    @Override
    public String getServicePropertyString(String key, String defStr) {
        Object result = serviceProperties.get(key);
        return (result == null) ? defStr : result.toString();
    }

    /** Optional execution args for this service */
    public String getJvmOptions() {
        return jvmOptions;
    }
    public final void setJvmOptions(String args) {
        jvmOptions = (args == null) ? "" : args;
    }
    public final void appendJvmOptions(String args) {
        if ((args != null) && !args.isEmpty()) {
            setJvmOptions(jvmOptions + getSeparator(jvmOptions) + args);
        }
    }
    private static String getSeparator(String current) {
        return ("".equals(current)) ? "" : " ";
    }
    public final void prependJvmOptions(String args) {
        if ((args != null) && !args.isEmpty()) {
            setJvmOptions(args + getSeparator(jvmOptions) + jvmOptions);
        }
    }
    public String getPreLoad() {
        return preload != null ? preload : defaultPreload();
    }
    public void setPreLoad(String preload) {
        this.preload = preload;
    }
    /** If larger or equal to 0 it mean that explicit mmaps shall not be included in coredump.*/
    public void setMMapNoCoreLimit(long noCoreLimit) {
        if (noCoreLimit >= 0) {
            environmentVariables.put("VESPA_MMAP_NOCORE_LIMIT", noCoreLimit);
        } else {
            environmentVariables.remove("VESPA_MMAP_NOCORE_LIMIT");
        }
    }
    public void setCoreOnOOM(boolean coreOnOOM) {
        if ( ! coreOnOOM) {
            environmentVariables.put("VESPA_SILENCE_CORE_ON_OOM", true);
        } else {
            environmentVariables.remove("VESPA_SILENCE_CORE_ON_OOM");
        }
    }

    public void setNoVespaMalloc(String s) { environmentVariables.put("VESPA_USE_NO_VESPAMALLOC", s); }
    public void setVespaMalloc(String s) { environmentVariables.put("VESPA_USE_VESPAMALLOC", s); }
    public void setVespaMallocDebug(String s) { environmentVariables.put("VESPA_USE_VESPAMALLOC_D", s); }
    public void setVespaMallocDebugStackTrace(String s) { environmentVariables.put("VESPA_USE_VESPAMALLOC_DST", s); }

    private static String toEnvValue(Object o) {
        if (o instanceof Number || o instanceof Boolean) {
            return o.toString();
        }
        return '"' + o.toString() + '"';
    }

    public void addEnvironmentVariable(String nameAndValue) {
        int pos = nameAndValue.indexOf('=');
        environmentVariables.put(nameAndValue.substring(0, pos), nameAndValue.substring(pos+1));
    }

    public void addEnvironmentVariable(String name, Object value) {
        environmentVariables.put(name, value);
    }

    @Override
    public Map<String, Object> getEnvVars() {
        return Collections.unmodifiableMap(environmentVariables);
    }

    public String getEnvStringForTesting() {
        return environmentVariables.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + '=' + toEnvValue(e.getValue())).collect(Collectors.joining(" "));
    }

    /**
     * WARNING: should only be called before initService()
     */
    public void setBasePort(int wantedPort) {
        this.basePort = wantedPort;
    }

    public void setHostResource(HostResource hostResource) {
        this.hostResource = hostResource;
    }

    public boolean isInitialized() {
        return initialized;
    }

    /** The service HTTP port for health status */
    public int getHealthPort() { return -1;}

    /**
     * Overridden by subclasses. List of default dimensions to be added to this services metrics
     *
     * @return the default dimensions for this service
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
