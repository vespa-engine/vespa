// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http.hc5;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * {@link HttpRoutePlanner} that changes assumes requests specify the HTTP scheme,
 * and then changes this to HTTPS, keeping the other host parameters.
 *
 * @author jonmv
 */
class HttpToHttpsRoutePlanner implements HttpRoutePlanner {

    @Override
    public HttpRoute determineRoute(HttpHost target, HttpContext context) throws HttpException {
        if ( ! target.getSchemeName().equals("http") && ! target.getSchemeName().equals("https"))
            throw new IllegalArgumentException("Scheme must be 'http' or 'https' when using HttpToHttpsRoutePlanner, was '" + target.getSchemeName() + "'");

        if (target.getPort() == -1)
            throw new IllegalArgumentException("Port must be set when using HttpToHttpsRoutePlanner");

        if (HttpClientContext.adapt(context).getRequestConfig().getProxy() != null)
            throw new IllegalArgumentException("Proxies are not supported with HttpToHttpsRoutePlanner");

        return new HttpRoute(new HttpHost("https", target.getAddress(), target.getHostName(), target.getPort()));
    }

}
