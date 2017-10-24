package com.yahoo.container.jdisc.athenz;

/**
 * @author mortent
 */
public interface AthenzIdentityProvider {

    String getNToken();

    String getX509Cert();
}
