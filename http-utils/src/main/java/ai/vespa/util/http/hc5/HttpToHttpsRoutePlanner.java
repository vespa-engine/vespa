// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http.hc5;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
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
    @SuppressWarnings("deprecation")
    public HttpRoute determineRoute(HttpHost target, HttpContext context) {
        if ( ! target.getSchemeName().equals("http") && ! target.getSchemeName().equals("https"))
            throw new IllegalArgumentException("Scheme must be 'http' or 'https' when using HttpToHttpsRoutePlanner, was '" + target.getSchemeName() + "'");

        if (HttpClientContext.adapt(context).getRequestConfig().getProxy() != null)
            throw new IllegalArgumentException("Proxies are not supported with HttpToHttpsRoutePlanner");

        int port = DefaultSchemePortResolver.INSTANCE.resolve(target);
        return new HttpRoute(new HttpHost("https", target.getAddress(), target.getHostName(), port));
    }

}
