// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty.servlet;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.ServletPathsConfig;
import com.yahoo.jdisc.http.ServletPathsConfig.Servlets.Builder;
import com.yahoo.jdisc.http.server.jetty.SimpleHttpClient.RequestExecutor;
import com.yahoo.jdisc.http.server.jetty.TestDriver;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * @author Tony Vaagenes
 * @author bakksjo
 */
public class ServletTestBase {

    private static class ServletInstance {
        final ComponentId componentId; final String path; final HttpServlet instance;

        ServletInstance(ComponentId componentId, String path, HttpServlet instance) {
            this.componentId = componentId;
            this.path = path;
            this.instance = instance;
        }
    }

    private final List<ServletInstance> servlets = List.of(
            new ServletInstance(TestServlet.ID, TestServlet.PATH, new TestServlet()),
            new ServletInstance(NoContentTestServlet.ID, NoContentTestServlet.PATH, new NoContentTestServlet()));

    protected RequestExecutor httpGet(TestDriver testDriver, String path) {
        return testDriver.client().newGet("/" + path);
    }

    protected ServletPathsConfig createServletPathConfig() {
        ServletPathsConfig.Builder configBuilder = new ServletPathsConfig.Builder();

        servlets.forEach(servlet ->
                configBuilder.servlets(
                        servlet.componentId.stringValue(),
                        new Builder().path(servlet.path)));

        return new ServletPathsConfig(configBuilder);
    }

    protected ComponentRegistry<ServletHolder> servlets() {
        ComponentRegistry<ServletHolder> result = new ComponentRegistry<>();

        servlets.forEach(servlet ->
                result.register(servlet.componentId, new ServletHolder(servlet.instance)));

        result.freeze();
        return result;
    }

    protected Module guiceModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(new TypeLiteral<ComponentRegistry<ServletHolder>>(){}).toInstance(servlets());
                bind(ServletPathsConfig.class).toInstance(createServletPathConfig());
            }
        };
    }

    protected static class TestServlet extends HttpServlet {
        static final String PATH = "servlet/test-servlet";
        static final ComponentId ID = ComponentId.fromString("test-servlet");
        static final String RESPONSE_CONTENT = "Response from " + TestServlet.class.getSimpleName();

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            response.setContentType("text/plain");
            PrintWriter writer = response.getWriter();
            writer.write(RESPONSE_CONTENT);
            writer.close();
        }
    }

    @WebServlet(asyncSupported = true)
    protected static class NoContentTestServlet extends HttpServlet {
        static final String HEADER_ASYNC = "HEADER_ASYNC";

        static final String PATH = "servlet/no-content-test-servlet";
        static final ComponentId ID = ComponentId.fromString("no-content-test-servlet");

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            if (request.getHeader(HEADER_ASYNC) != null) {
                asyncGet(request);
            }
        }

        private void asyncGet(HttpServletRequest request) {
            request.startAsync().start(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    log("Interrupted", e);
                } finally {
                    request.getAsyncContext().complete();
                }
            });
        }
    }


    protected static final RequestHandler dummyRequestHandler = new AbstractRequestHandler() {
        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            throw new UnsupportedOperationException();
        }
    };
}
