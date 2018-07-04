// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.net;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Simon Thoresen Hult
 */
public class UrlTokenizer {

    public static final String TERM_STARTHOST = "StArThOsT";
    public static final String TERM_ENDHOST = "EnDhOsT";

    private static final Map<String, String> schemeToPort = new HashMap<>();
    private static final Map<String, String> portToScheme = new HashMap<>();
    private static final char TO_LOWER = (char)('A' - 'a');
    private final Url url;

    static {
        registerScheme("ftp", 21);
        registerScheme("gopher", 70);
        registerScheme("http", 80);
        registerScheme("https", 443);
        registerScheme("imap", 143);
        registerScheme("mailto", 25);
        registerScheme("news", 119);
        registerScheme("nntp", 119);
        registerScheme("pop", 110);
        registerScheme("rsync", 873);
        registerScheme("rtsp", 554);
        registerScheme("sftp", 22);
        registerScheme("shttp", 443);
        registerScheme("sip", 5060);
        registerScheme("sips", 5061);
        registerScheme("snmp", 161);
        registerScheme("ssh", 22);
        registerScheme("telnet", 23);
        registerScheme("tftp", 69);
    }

    public UrlTokenizer(String url) {
        this(Url.fromString(url));
    }

    public UrlTokenizer(Url url) {
        this.url = url;
    }

    private String guessScheme(String port) {
        String scheme = portToScheme.get(port);
        if (scheme != null) {
            return scheme;
        }
        return "http";
    }

    private String guessPort(String scheme) {
        String port = schemeToPort.get(scheme);
        if (port != null) {
            return port;
        }
        return null;
    }

    public List<UrlToken> tokenize() {
        List<UrlToken> lst = new LinkedList<>();

        int offset = 0;
        String port = url.getPortString();
        String scheme = url.getScheme();
        if (scheme == null) {
            scheme = guessScheme(port);
            addTokens(lst, UrlToken.Type.SCHEME, offset, scheme, false);
        } else {
            addTokens(lst, UrlToken.Type.SCHEME, url.getSchemeBegin(), scheme, true);
            offset = url.getSchemeEnd();
        }

        String userInfo = url.getUserInfo();
        if (userInfo != null) {
            addTokens(lst, UrlToken.Type.USERINFO, url.getUserInfoBegin(), userInfo, true);
            offset = url.getUserInfoEnd();
        }

        String password = url.getPassword();
        if (password != null) {
            addTokens(lst, UrlToken.Type.PASSWORD, url.getPasswordBegin(), password, true);
            offset = url.getPasswordEnd();
        }

        String host = url.getHost();
        if (host == null || host.isEmpty()) {
            if (host != null) {
                offset = url.getHostBegin();
            }
            if ("file".equalsIgnoreCase(scheme)) {
                addHostTokens(lst, offset, offset, "localhost", false);
            }
        } else {
            addHostTokens(lst, url.getHostBegin(), url.getHostEnd(), host, true);
            offset = url.getHostEnd();
        }

        port = url.getPortString();
        if (port == null) {
            if ((port = guessPort(scheme)) != null) {
                addTokens(lst, UrlToken.Type.PORT, offset, port, false);
            }
        } else {
            addTokens(lst, UrlToken.Type.PORT, url.getPortBegin(), port, true);
        }

        String path = url.getPath();
        if (path != null) {
            addTokens(lst, UrlToken.Type.PATH, url.getPathBegin(), path, true);
        }

        String query = url.getQuery();
        if (query != null) {
            addTokens(lst, UrlToken.Type.QUERY, url.getQueryBegin(), query, true);
        }

        String fragment = url.getFragment();
        if (fragment != null) {
            addTokens(lst, UrlToken.Type.FRAGMENT, url.getFragmentBegin(), fragment, true);
        }

        return lst;
    }

    public static void addTokens(List<UrlToken> lst, UrlToken.Type type, int offset, String image, boolean orig) {
        StringBuilder term = new StringBuilder();
        int prev = 0;
        for (int skip, next = 0, len = image.length(); next < len; next += skip) {
            char c = image.charAt(next);
            if (c == '%') {
                c = (char)Integer.parseInt(image.substring(next + 1, next + 3), 16);
                skip = 3;
            } else {
                skip = 1;
            }
            if ((c >= '0' && c <= '9') ||
                (c >= 'a' && c <= 'z') ||
                (c == '-' || c == '_'))
            {
                term.append(c);
            } else if (c >= 'A' && c <= 'Z') {
                term.append((char)(c - TO_LOWER));
            } else {
                if (prev < next) {
                    lst.add(new UrlToken(type, offset + (orig ? prev : 0), orig ? image.substring(prev, next) : null,
                                         term.toString()));
                    term = new StringBuilder();
                }
                prev = next + skip;
            }
        }
        if (term.length() > 0) {
            lst.add(new UrlToken(type, offset + (orig ? prev : 0), orig ? image.substring(prev) : null,
                                 term.toString()));
        }
    }

    private static void addHostTokens(List<UrlToken> lst, int begin, int end, String image, boolean orig) {
        lst.add(new UrlToken(UrlToken.Type.HOST, begin, null, TERM_STARTHOST));
        addTokens(lst, UrlToken.Type.HOST, begin, image, orig);
        lst.add(new UrlToken(UrlToken.Type.HOST, end, null, TERM_ENDHOST));
    }

    private static void registerScheme(String scheme, int port) {
        String str = String.valueOf(port);
        schemeToPort.put(scheme, str);
        portToScheme.put(str, scheme);
    }
}
