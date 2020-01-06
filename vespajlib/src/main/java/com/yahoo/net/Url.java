// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.net;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Simon Thoresen Hult
 */
public class Url {

    private static final Pattern pattern = Pattern.compile(
            //12            3  456          7 8               9ab   c                     d e              f        g   h        i j
            //          2 1                6           87  5                c            b          ed a4         f           hg      ji
            "^(([^:/?#]+):)?(//((([^:@/?#]+)(:([^@/?#]+))?@))?(((\\[([^\\]]+)\\]|[^:/?#]+)(:([^/?#]+))?)))?([^?#]+)?(\\?([^#]*))?(#(.*))?");
    private final String image;
    private final int schemeBegin;
    private final int schemeEnd;
    private final int userInfoBegin;
    private final int userInfoEnd;
    private final int passwordBegin;
    private final int passwordEnd;
    private final int hostBegin;
    private final int hostEnd;
    private final int portBegin;
    private final int portEnd;
    private final int pathBegin;
    private final int pathEnd;
    private final int queryBegin;
    private final int queryEnd;
    private final int fragmentBegin;
    private final int fragmentEnd;

    public Url(String scheme, String user, String password, String host, Integer port, String path, String query,
               String fragment)
    {
        StringBuilder image = new StringBuilder();
        schemeBegin = image.length();
        if (scheme != null) {
            image.append(scheme);
            schemeEnd = image.length();
            image.append(':');
        } else {
            schemeEnd = schemeBegin;
        }
        if (host != null) {
            image.append("//");
        }
        userInfoBegin = image.length();
        if (user != null) {
            image.append(user);
            userInfoEnd = image.length();
        } else {
            userInfoEnd = userInfoBegin;
        }
        if (password != null) {
            image.append(':');
            passwordBegin = image.length();
            image.append(password);
            passwordEnd = image.length();
        } else {
            passwordBegin = image.length();
            passwordEnd = passwordBegin;
        }
        if (user != null || password != null) {
            image.append('@');
        }
        if (host != null) {
            boolean esc = host.indexOf(':') >= 0;
            if (esc) {
                image.append('[');
            }
            hostBegin = image.length();
            image.append(host);
            hostEnd = image.length();
            if (esc) {
                image.append(']');
            }
        } else {
            hostBegin = image.length();
            hostEnd = hostBegin;
        }
        if (port != null) {
            image.append(':');
            portBegin = image.length();
            image.append(port);
            portEnd = image.length();
        } else {
            portBegin = image.length();
            portEnd = portBegin;
        }
        pathBegin = image.length();
        if (path != null) {
            image.append(path);
            pathEnd = image.length();
        } else {
            pathEnd = pathBegin;
        }
        if (query != null) {
            image.append('?');
            queryBegin = image.length();
            image.append(query);
            queryEnd = image.length();
        } else {
            queryBegin = image.length();
            queryEnd = queryBegin;
        }
        if (fragment != null) {
            image.append("#");
            fragmentBegin = image.length();
            image.append(fragment);
            fragmentEnd = image.length();
        } else {
            fragmentBegin = image.length();
            fragmentEnd = fragmentBegin;
        }
        this.image = image.toString();
    }

    public static Url fromString(String image) {
        Matcher matcher = pattern.matcher(image);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Malformed URL.");
        }
        String host = matcher.group(12);
        if (host == null) {
            host = matcher.group(11);
        }
        if (host == null) {
            host = matcher.group(9);
        }
        String port = matcher.group(14);
        return new Url(matcher.group(2), matcher.group(6), matcher.group(8), host,
                       port != null ? Integer.valueOf(port) : null, matcher.group(15), matcher.group(17),
                       matcher.group(19));
    }

    public int getSchemeBegin() {
        return schemeBegin;
    }

    public int getSchemeEnd() {
        return schemeEnd;
    }

    public int getUserInfoBegin() {
        return userInfoBegin;
    }

    public int getUserInfoEnd() {
        return userInfoEnd;
    }

    public int getPasswordBegin() {
        return passwordBegin;
    }

    public int getPasswordEnd() {
        return passwordEnd;
    }

    public int getHostBegin() {
        return hostBegin;
    }

    public int getHostEnd() {
        return hostEnd;
    }

    public int getPortBegin() {
        return portBegin;
    }

    public int getPortEnd() {
        return portEnd;
    }

    public int getPathBegin() {
        return pathBegin;
    }

    public int getPathEnd() {
        return pathEnd;
    }

    public int getQueryBegin() {
        return queryBegin;
    }

    public int getQueryEnd() {
        return queryEnd;
    }

    public int getFragmentBegin() {
        return fragmentBegin;
    }

    public int getFragmentEnd() {
        return fragmentEnd;
    }

    public String getScheme() {
        return schemeBegin < schemeEnd ? image.substring(schemeBegin, schemeEnd) : null;
    }

    public String getUserInfo() {
        return userInfoBegin < userInfoEnd ? image.substring(userInfoBegin, userInfoEnd) : null;
    }

    public String getPassword() {
        return passwordBegin < passwordEnd ? image.substring(passwordBegin, passwordEnd) : null;
    }

    public String getHost() {
        return hostBegin < hostEnd ? image.substring(hostBegin, hostEnd) : null;
    }

    public Integer getPort() {
        String str = getPortString();
        return str != null ? Integer.valueOf(str) : null;
    }

    public String getPortString() {
        return portBegin < portEnd ? image.substring(portBegin, portEnd) : null;
    }

    public String getPath() {
        return pathBegin < pathEnd ? image.substring(pathBegin, pathEnd) : null;
    }

    public String getQuery() {
        return queryBegin < queryEnd ? image.substring(queryBegin, queryEnd) : null;
    }

    public String getFragment() {
        return fragmentBegin < fragmentEnd ? image.substring(fragmentBegin, fragmentEnd) : null;
    }

    @Override
    public int hashCode() {
        return image.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof Url) && image.equals(((Url)obj).image);
    }

    @Override
    public String toString() {
        return image;
    }

}
