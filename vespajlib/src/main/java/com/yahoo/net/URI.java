// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.net;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.yahoo.text.Lowercase.toLowerCase;

/**
 * <p>An URI. This is a pure (immutable) value object.</p>
 *
 * <p>This does more normalization of hierarchical URIs (URLs) than
 * described in the RFC and allows hosts with underscores.</p>
 *
 * @author bratseth
 */
public class URI implements Cloneable, Comparable<URI> {

    /** The uri string */
    private String uri;

    /** The scheme of the uri */
    private String scheme = null;

    /** The host part of the uri */
    private String host = null;

    /** The port number of the uri, or -1 if no port is explicitly given */
    private int port = -1;

    /** The part of the uri following the host (host and port) */
    private String rest = null;

    private static final Pattern tokenizePattern = Pattern.compile("[^\\w\\-]");

    private boolean parsedDomain = false;
    private String domain = null;

    private boolean parsedMainTld = false;
    private String mainTld = null;

    private boolean parsedPath = false;
    private String path = null;

    private boolean parsedParams = false;
    private String params = null;

    private boolean parsedFilename = false;
    private String filename = null;

    private boolean parsedExtension = false;
    private String extension = null;

    private boolean parsedQuery = false;
    private String query = null;

    private boolean parsedFragment = false;
    private String fragment = null;


    /** The explanation of why this uri is invalid, or null if it is valid */
    private String invalidExplanation = null;

    /** True if this uri is opaque, false if it is hierarchical */
    private boolean opaque = true;

    /**
     * <p>Creates an URI without keeping the fragment (the part starting by #).
     * If the uri is hierarchical, it is normalized and incorrect hierarchical uris
     * which looks like urls are attempted repaired.</p>
     *
     * <p>Relative uris are not supported.</p>
     *
     * @param uriString the uri string
     * @throws NullPointerException if the given uriString is null
     */
    public URI(String uriString) {
        this(uriString, false);
    }

    /**
     * Creates an URI, optionaly keeping the fragment (the part starting by #).
     * If the uri is hierarchical, it is normalized and incorrect hierarchical uris
     * which looks like urls are attempted repaired.
     *
     * <p>Relative uris are not supported.</p>
     *
     * @param uriString the uri string
     * @param keepFragment true to keep the fragment
     * @throws NullPointerException if the given uriString is null
     */
    public URI(String uriString, boolean keepFragment) {
        this(uriString, keepFragment, false);
    }

    /**
     * Creates an URI, optionaly keeping the fragment (the part starting by #).
     * If the uri is hierarchical, it is normalized and incorrect hierarchical uris
     * which looks like urls are attempted repaired.
     *
     * <p>Relative uris are not supported.</p>
     *
     * @param uriString the uri string
     * @param keepFragment true to keep the fragment
     * @param hierarchicalOnly will force any uri string given to be parsed as
     *        a hierarchical one, causing the uri to be invalid if it isn't
     * @throws NullPointerException if the given uriString is null
     */
    public URI(String uriString, boolean keepFragment, boolean hierarchicalOnly) {
        if (uriString == null) {
            throw new NullPointerException("Can not create an uri from null");
        }

        if (!keepFragment) {
            int fragmentIndex = uriString.indexOf("#");

            if (fragmentIndex >= 0) {
                uriString = uriString.substring(0, fragmentIndex);
            }
        }

        try {
            this.uri = uriString.trim();
            opaque = isOpaque(uri);

            // No further parsing of opaque uris
            if (isOpaque() && !hierarchicalOnly) {
                return;
            }
            opaque = false;
            normalizeHierarchical();
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null) {
                invalidExplanation = e.getMessage();
            } else {
                Throwable t = e.getCause();
                if (t != null && t.getMessage() != null) {
                    invalidExplanation = t.getMessage();
                } else {
                    invalidExplanation = "Invalid uri: " + e;
                }
            }
        }
    }

    /** Creates an url type uri */
    public URI(String scheme, String host, int port, String rest) {
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.rest = rest;
        recombine();
        normalizeHierarchical();
        opaque = false;
    }

    /** Returns whether an url is opaque or hierarchical */
    private boolean isOpaque(String uri) {
        int colonIndex = uri.indexOf(":");

        if (colonIndex < 0) {
            return true;
        } else {
            return !(uri.length() > colonIndex + 1
                    && uri.charAt(colonIndex + 1) == '/');
        }
    }

    /**
     * Returns whether this is a valid URI (after normalizing).
     * All non-hierarchical uri's containing a scheme is valid.
     */
    public boolean isValid() {
        return invalidExplanation == null;
    }

    /**
     * Normalizes this hierarchical uri according to FRC 2396 and the Overture
     * standard. Before normalizing, some simple heuristics are use to make
     * the uri complete if needed. After normalizing, the scheme,
     * host, port and rest of this uri is set if defined.
     *
     * @throws IllegalArgumentException if this uri can not be normalized into a legal uri
     */
    private void normalizeHierarchical() {
        complete();
        escapeNonAscii();
        unescapeHtmlEntities();
        decompose();
        lowCaseHost();
        removeDefaultPortNumber();
        removeTrailingHostDot();
        makeDoubleSlashesSingle();
        recombine();
    }

    /** Applies simple heuristics to complete this uri if needed */
    private void complete() {
        if (uri.startsWith("www.")) {
            uri = "http://" + uri;
        } else if (uri.startsWith("WWW")) {
            uri = "http://" + uri;
        } else if (uri.startsWith("/http:")) {
            uri = uri.substring(1);
        } else if (isFileURIShortHand(uri)) {
            uri = "file://" + uri;
        }
    }

    private boolean isFileURIShortHand(String uri) {
        if (uri.indexOf(":\\") == 1) {
            return true;
        }
        if (uri.indexOf("c:/") == 0) {
            return true;
        }
        if (uri.indexOf("d:/") == 0) {
            return true;
        }
        return false;
    }

    /**
     * Decomposes this uri into scheme, host, port and rest.
     */
    private void decompose() {
        java.net.URI neturi = java.net.URI.create(uri).normalize();

        scheme = neturi.getScheme();

        host = neturi.getHost();
        boolean portAlreadyParsed = false;

        // No host if the host contains underscores
        if (host == null) {
            host = neturi.getAuthority();
            if (host != null) {
                int colonPos = host.lastIndexOf(":");
                if (!scheme.equals("file") && colonPos > -1) {
                    //we probably have an (illegal) URI of type http://under_score.com:5000/
                    try {
                        port = Integer.parseInt(host.substring(colonPos + 1));
                        host = host.substring(0, colonPos);
                        portAlreadyParsed = true;
                    } catch (NumberFormatException nfe) {
                        //empty
                    }
                }
            }
        }

        if ("file".equalsIgnoreCase(scheme)) {
            if (host == null) {
                host = "localhost";
            } else {
                host = repairWindowsDrive(host, uri);
            }
        }
        if (host == null) {
            throw new IllegalArgumentException(
                    "A complete uri must specify a host");
        }
        if (!portAlreadyParsed) {
            port = neturi.getPort();
        }
        rest = (neturi.getRawPath() != null ? neturi.getRawPath() : "")
                + (neturi.getRawQuery() != null
                        ? ("?" + neturi.getRawQuery())
                        : "")
                        + (neturi.getRawFragment() != null
                                ? ("#" + neturi.getRawFragment())
                                : "");
    }

    /** c: turns to c when interpreted by URI. Repair it */
    private String repairWindowsDrive(String host, String uri) {
        if (host.length() != 1) {
            return host;
        }
        int driveIndex = uri.indexOf(host + ":");

        if (driveIndex == 5 || driveIndex == 7) { // file:<drive> or file://<drive>
            return host + ":";
        } else {
            return host;
        }
    }

    /** "http://a/\u00E6" → "http://a/%E6;" */
    private void escapeNonAscii() {
        char[] uriChars = uri.toCharArray();
        StringBuilder result = new StringBuilder(uri.length());

        for (char uriChar : uriChars) {
            if (uriChar >= 0x80 || uriChar == 0x22) {
                result.append("%");
                result.append(Integer.toHexString(uriChar));
                result.append(";");
            } else {
                result.append(uriChar);
            }
        }
        uri = result.toString();
    }

    /** "http://a/&amp;amp;" → "http://a/&amp;" Currently ampersand only */
    private void unescapeHtmlEntities() {
        int ampIndex = uri.indexOf("&amp;");

        if (ampIndex < 0) {
            return;
        }

        StringBuilder result = new StringBuilder(uri.substring(0, ampIndex));

        while (ampIndex >= 0) {
            result.append("&");
            int nextAmpIndex = uri.indexOf("&amp;", ampIndex + 5);

            result.append(
                    uri.substring(ampIndex + 5,
                    nextAmpIndex > 0 ? nextAmpIndex : uri.length()));
            ampIndex = nextAmpIndex;
        }
        uri = result.toString();
    }

    /** "HTTP://a" → "http://a" */
    private void lowCaseHost() {
        host = toLowerCase(host);
    }

    /** "http://a:80" → "http://a" and "https://a:443" → https//a */
    private void removeDefaultPortNumber() {
        if (port == 80 && scheme.equals("http")) {
            port = -1;
        } else if (port == 443 && scheme.equals("https")) {
            port = -1;
        }
    }

    /** "http://a./b" → "http://a/b" */
    private void removeTrailingHostDot() {
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
    }

    /** "http://a//b" → "http://a/b" */
    private void makeDoubleSlashesSingle() {
        StringBuilder result = new StringBuilder(rest.length());
        char[] restChars = rest.toCharArray();

        for (int i = 0; i < restChars.length; i++) {
            if (!(i + 1 < restChars.length && restChars[i] == '/'
                    && restChars[i + 1] == '/')) {
                result.append(restChars[i]);
            }
        }
        rest = result.toString();
    }

    /** Recombines the uri from the scheme, host, port and rest */
    private void recombine() {
        StringBuilder recombined = new StringBuilder(100);

        recombined.append(scheme);
        recombined.append("://");
        recombined.append(host);
        if (port > -1) {
            recombined.append(":").append(port);
        }
        if (rest != null) {
            if (!rest.startsWith("/")) {
                recombined.append("/");
            }
            recombined.append(rest);
        } else {
            recombined.append("/"); // RFC 2396 violation, as required by search
        }
        uri = recombined.toString();
    }

    /**
     * Returns the normalized scheme of this URI.
     *
     * @return the normalized scheme (protocol), or null if there is none,
     *         which may only be the case with non-hierarchical URIs
     */
    public String getScheme() {
        return scheme;
    }

    /**
     * Returns whether this URI is hierarchical or opaque.
     * A typical example of an hierarchical URI is an URL,
     * while URI's are mailto, news and such.
     *
     * @return true if the url is opaque, false if it is hierarchical
     */
    public boolean isOpaque() {
        return opaque;
    }

    /**
     * Returns the normalized host of this URI.
     *
     * @return the normalized host, or null if there is none, which may
     *         only be the case if this is a non-hierarchical uri
     */
    public String getHost() {
        return host;
    }

    /** Returns the port number of this scheme if set explicitly, or -1 otherwise */
    public int getPort() {
        return port;
    }

    /**
     * Returns the <i>rest</i> of this uri, that is what is following the host or port.
     * This is path, query and fragment as defined in RFC 2396. Returns an empty string
     * if this uri has no rest.
     */
    public String getRest() {
        if (rest == null) {
            return null;
        } else if (rest.equals("/")) {
            return "";
        } else {
            return rest;
        }
    }

    public String getDomain() {
        if (parsedDomain) {
            return domain;
        }
        String host = getHost();
        if (host == null) return null;

        int firstDotPos = host.indexOf(".");
        int lastDotPos = host.lastIndexOf(".");

        String domain;
        if (firstDotPos < 0) {
            // "." was not found at all
            domain = host;
        } else if (firstDotPos == lastDotPos) {
            //there is only one "." in the host
            domain = host;
        } else {
            //for www.host.com return host.com
            //TODO: Must be corrected when implementing tldlist
            domain = host.substring(firstDotPos + 1, host.length());
        }

        this.parsedDomain = true;
        this.domain = domain;
        return domain;
    }

    public String getMainTld() {
        if (parsedMainTld) {
            return mainTld;
        }
        String host = getHost();
        if (host == null) return null;

        int lastDotPos = host.lastIndexOf(".");

        String mainTld;
        if (lastDotPos < 0) {
            //no ".", no TLD
            mainTld = null;
        } else if (lastDotPos == host.length() - 1) {
            //the "." is the last character
            mainTld = null;
        } else {
            //for www.yahoo.co.uk return uk
            //TODO: Implement list of TLDs from config?
            mainTld = host.substring(lastDotPos + 1);
        }
        this.parsedMainTld = true;
        this.mainTld = mainTld;
        return mainTld;
    }

    public String getPath() {
        if (parsedPath) {
            return path;
        }
        String rest = this.rest;
        if (rest == null) return null;

        rest = removeFragment(rest);

        int queryPos = rest.lastIndexOf("?");
        if (queryPos > -1) {
            rest = rest.substring(0, queryPos);
        }
        this.parsedPath = true;
        this.path = rest;
        return this.path;
    }

    private String removeFragment(String path) {
        int fragmentPos = path.lastIndexOf("#");
        return (fragmentPos > -1) ? path.substring(0, fragmentPos) : path;
    }

    public String getFilename() {
        if (parsedFilename) {
            return filename;
        }
        String path = getPath();
        if (path == null) return null;

        path = removeParams(path);

        int lastSlash = path.lastIndexOf("/");

        String filename;
        if (lastSlash < 0) {
            //there is no slash, return the path, excluding params
            filename = path;
        } else if (lastSlash == path.length() - 1) {
            //the slash is the last character, there is no filename here
            filename = "";
        } else {
            filename = path.substring(lastSlash + 1);
        }
        this.parsedFilename = true;
        this.filename = filename;
        return filename;
    }

    private String removeParams(String filename) {
        int firstSemicolon = filename.indexOf(";");

        if (firstSemicolon < 0) {
            //there are no params
            return filename;
        }
        return filename.substring(0, firstSemicolon);
    }

    public String getExtension() {
        if (parsedExtension) {
            return extension;
        }
        String filename = getFilename();
        if (filename == null) return null;

        int lastDotPos = filename.lastIndexOf(".");

        String extension;
        if (lastDotPos < 0) {
            //there is no ".", there is no extension
            extension = null;
        } else if (lastDotPos == filename.length() - 1) {
            //the "." is the last character, there is no extension
            extension = null;
        } else {
            extension = filename.substring(lastDotPos + 1);
        }
        this.parsedExtension = true;
        this.extension = extension;
        return extension;
    }

    public String getQuery() {
        if (parsedQuery) {
            return query;
        }
        String rest = this.rest;
        if (rest == null) return null;

        rest = removeFragment(rest);

        int queryPos = rest.lastIndexOf("?");
        String query = null;
        if (queryPos > -1) {
            //we have a query
            query = rest.substring(queryPos+1);
        }
        this.parsedQuery = true;
        this.query = query;
        return query;
    }

    public String getFragment() {
        if (parsedFragment) {
            return fragment;
        }
        String path = this.rest;
        if (path == null) return null;

        int fragmentPos = path.lastIndexOf("#");
        String fragment = null;
        if (fragmentPos > -1) {
            //we have a fragment
            fragment = path.substring(fragmentPos+1);
        }
        this.parsedFragment = true;
        this.fragment = fragment;
        return fragment;
    }

    public String getParams() {
        if (parsedParams) {
            return params;
        }
        String path = getPath();
        if (path == null) return null;

        int semicolonPos = path.indexOf(";");
        String params;
        if (semicolonPos < 0) {
            //there is no semicolon, there are no params here
            params = null;
        } else if (semicolonPos == path.length() - 1) {
            //the semicolon is the last character, there are no params here
            params = null;
        } else {
            params = path.substring(semicolonPos + 1);
        }
        this.parsedParams = true;
        this.params = params;
        return params;
    }

    public static String[] tokenize(String item) {
        return tokenizePattern.split(item);
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();

        tokens.addAll(tokenize(URLContext.URL_SCHEME, getScheme()));
        tokens.addAll(tokenize(URLContext.URL_HOST, getHost()));
        tokens.addAll(tokenize(URLContext.URL_PORT, getPort() > -1 ? "" + getPort() : null));
        tokens.addAll(tokenize(URLContext.URL_PATH, getPath()));
        tokens.addAll(tokenize(URLContext.URL_QUERY, getQuery()));
        tokens.addAll(tokenize(URLContext.URL_FRAGMENT, getFragment()));

        return tokens;
    }

    private List<Token> tokenize(URLContext context, String item) {
        if (item == null) {
            return new ArrayList<>(0);
        }
        String[] tokenStrings = tokenize(item);
        List<Token> tokens = new ArrayList<>(tokenStrings.length);
        for (String tokenString : tokenStrings) {
            if (tokenString.length() > 0) {
                tokens.add(new Token(context, tokenString));
            }
        }
        return tokens;
    }

    /** Returns an explanation of why this uri is invalid, or null if it is valid */
    public String getInvalidExplanation() {
        return invalidExplanation;
    }

    public int hashCode() {
        return uri.hashCode();
    }

    public boolean equals(Object object) {
        if (!(object instanceof URI)) {
            return false;
        }
        return (toString().equals(object.toString()));
    }

    public int compareTo(URI object) {
        return toString().compareTo(object.toString());
    }

    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Someone made me unclonable!", e);
        }
    }

    /** Returns a new URI with a changed scheme */
    public URI setScheme(String scheme) {
        return new URI(scheme, host, port, rest);
    }

    /** Returns a new URI with a changed host (or authority) */
    public URI setHost(String host) {
        return new URI(scheme, host, port, rest);
    }

    /** Returns a new URI with a changed port */
    public URI setPort(int port) {
        return new URI(scheme, host, port, rest);
    }

    /** Returns a new URI with a changed rest */
    public URI setRest(String rest) {
        return new URI(scheme, host, port, rest);
    }

    /** Returns a new uri with the an additional parameter */
    public URI addParameter(String name, String value) {
        String newRest = rest;

        if (newRest == null) {
            newRest = "";
        }
        if ( newRest.contains("?")) {
            newRest += "&";
        } else {
            newRest += "?";
        }
        newRest += name + "=" + value;
        return new URI(scheme, host, port, newRest);
    }

    /** Returns this uri as a string */
    public String stringValue() {
        return uri;
    }

    /** Returns this URI as a string */
    public String toString() {
        return uri;
    }

    /**
     * Returns the depth of this uri.
     * The depth of an hierarchical uri equals the number of slashes
     * which are not separating the protocol and the host, and not at the end.
     *
     * @return the depth of this uri if it is hierarchical, or 0 if it is opaque
     */
    public int getDepth() {
        int colonIndex = uri.indexOf(':');

        // count number of slashes in the Uri
        int currentIndex = colonIndex;
        int depth = 0;

        while (currentIndex != -1) {
            currentIndex = uri.indexOf('/', currentIndex);
            if (currentIndex != -1) {
                depth++;
                currentIndex++;
            }
        }

        if (uri.charAt(colonIndex + 1) == '/') {
            depth--;
        }
        if (uri.charAt(colonIndex + 2) == '/') {
            depth--;
        }
        if ((uri.charAt(uri.length() - 1) == '/')
                && ((uri.length() - 1) > (colonIndex + 2))) {
            depth--;
        }
        return depth;
    }


    public static class Token {
        private final URLContext context;
        private final String token;

        private Token(URLContext context, String token) {
            this.context = context;
            this.token = token;
        }

        public URLContext getContext() {
            return context;
        }

        public String getToken() {
            return token;
        }
    }

    public enum URLContext {
        URL_SCHEME(0, "scheme"),
        URL_HOST(1, "host"),
        URL_DOMAIN(2, "domain"),
        URL_MAINTLD(3, "maintld"),
        URL_PORT(4, "port"),
        URL_PATH(5, "path"),
        URL_FILENAME(6, "filename"),
        URL_EXTENSION(7, "extension"),
        URL_PARAMS(8, "params"),
        URL_QUERY(9, "query"),
        URL_FRAGMENT(10, "fragment");

        public final int id;
        public final String name;

        URLContext(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

}
