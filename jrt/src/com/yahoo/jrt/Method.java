// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


/**
 * <p>A Method encapsulates the reflective information about a single RPC
 * method.</p>
 *
 * <p>Method parameters and return values are declared with type
 * strings. A <i>type string</i> denotes the concatenation of type
 * identifiers where a string is used to represent a sequence of
 * types. For example 'ii' is the type string for two 32-bit integers,
 * while 'iss' is the type string for a single 32-bit integer followed
 * by two strings. The complete list of type identifiers can be found
 * in the {@link Value} class.</p>
 *
 * <p>The type strings associated with actual method parameters and
 * return values may only contain valid type identifiers. However,
 * when you specify the parameters accepted by- or returned from a RPC
 * method via the Method constructor, '*' may be used as the last
 * character in the type string. Ending a type string specification
 * with '*' means that additional values are optional and may have any
 * type. This feature can also be used with the {@link
 * Request#checkReturnTypes Request.checkReturnTypes} method when
 * verifying return types.</p>
 *
 * @see Supervisor#addMethod
 **/
public class Method {

    private final MethodHandler            handler;

    private String name;
    private String paramTypes;
    private String returnTypes;

    private String   desc;
    private String[] paramName;
    private String[] paramDesc;
    private String[] returnName;
    private String[] returnDesc;

    private RequestAccessFilter filter = RequestAccessFilter.ALLOW_ALL;

    private static final String undocumented = "???";


    private void init(String name, String paramTypes, String returnTypes) {
        this.name = name;
        this.paramTypes = paramTypes;
        this.returnTypes = returnTypes;
        desc = undocumented;
        paramName = new String[this.paramTypes.length()];
        paramDesc = new String[this.paramTypes.length()];
        for (int i = 0; i < this.paramTypes.length(); i++) {
            paramName[i] = undocumented;
            paramDesc[i] = undocumented;
        }
        returnName = new String[this.returnTypes.length()];
        returnDesc = new String[this.returnTypes.length()];
        for (int i = 0; i < this.returnTypes.length(); i++) {
            returnName[i] = undocumented;
            returnDesc[i] = undocumented;
        }
    }

    /**
     * Create a new Method. The parameters define the name of the
     * method, the parameter types, the return value types and also
     * the handler for the method. Please refer to the {@link Method}
     * class description for an explanation of type strings.
     *
     * @param name method name
     * @param paramTypes a type string defining the parameter types
     * @param returnTypes a type string defining the return value types
     * @param handler the handler for this RPC method
     *
     * @throws MethodCreateException if the handler is <i>null</i>.
     **/
    public Method(String name, String paramTypes, String returnTypes,
                  MethodHandler handler) {

        this.handler = handler;
        if (this.handler == null) {
            throw new MethodCreateException("Handler is null");
        }
        init(name, paramTypes, returnTypes);
    }

    /**
     * Obtain the name of this method
     *
     * @return method name
     **/
    String name() {
        return name;
    }

    /**
     * Obtain the parameter types of this method
     *
     * @return parameter types
     **/
    String paramTypes() {
        return paramTypes;
    }

    /**
     * Obtain the return value types of this method
     *
     * @return return value types
     **/
    String returnTypes() {
        return returnTypes;
    }

    /**
     * Describe this method. This adds documentation that can be
     * obtained through remote reflection.
     *
     * @return this Method, to allow chaining
     **/
    public Method methodDesc(String desc) {
        this.desc = desc;
        return this;
    }

    /**
     * Obtain the method description
     *
     * @return method description
     **/
    String methodDesc() {
        return desc;
    }

    /**
     * Describe a parameter of this method. This adds documentation
     * that can be obtained through remote reflection.
     *
     * @return this Method, to allow chaining
     * @param index the parameter index
     * @param name the parameter name
     * @param desc the parameter description
     **/
    public Method paramDesc(int index, String name, String desc) {
        paramName[index] = name;
        paramDesc[index] = desc;
        return this;
    }

    public Method requestAccessFilter(RequestAccessFilter filter) { this.filter = filter; return this; }

    public RequestAccessFilter requestAccessFilter() { return filter; }

    /**
     * Obtain the name of a parameter
     *
     * @return parameter name
     * @param index parameter index
     **/
    String paramName(int index) {
        return paramName[index];
    }

    /**
     * Obtain the description of a parameter
     *
     * @return parameter description
     * @param index parameter index
     **/
    String paramDesc(int index) {
        return paramDesc[index];
    }

    /**
     * Describe a return value of this method. This adds documentation
     * that can be obtained through remote reflection.
     *
     * @return this Method, to allow chaining
     * @param index the return value index
     * @param name the return value name
     * @param desc the return value description
     **/
    public Method returnDesc(int index, String name, String desc) {
        returnName[index] = name;
        returnDesc[index] = desc;
        return this;
    }

    /**
     * Obtain the name of a return value
     *
     * @return return value name
     * @param index return value index
     **/
    String returnName(int index) {
        return returnName[index];
    }

    /**
     * Obtain the description of a return value
     *
     * @return return value description
     * @param index return value index
     **/
    String returnDesc(int index) {
        return returnDesc[index];
    }

    /**
     * Check whether the parameters of the given request satisfies the
     * parameters of this method.
     *
     * @return true if the parameters of the given request satisfies
     * the parameters of this method
     * @param req a request
     **/
    boolean checkParameters(Request req) {
        return req.parameters().satisfies(paramTypes);
    }

    /**
     * Check whether the return values of the given request satisfies
     * the return values of this method.
     *
     * @return true if the return values of the given request satisfies
     * the return values of this method
     * @param req a request
     **/
    boolean checkReturnValues(Request req) {
        return req.returnValues().satisfies(returnTypes);
    }

    /**
     * Invoke this method. The given request holds the parameters and
     * will be given as parameter to the handler method.
     *
     * @param req the request causing this invocation
     **/
    void invoke(Request req) {
        try {
            handler.invoke(req);
        } catch (Exception e) {
            req.setError(ErrorCode.METHOD_FAILED, e.toString());
        }
    }

    @Override
    public String toString() {
        return "method " + name + "(" + paramTypes + ")" + ( returnTypes.length()>0 ? ": " + returnTypes : "");
    }

}
