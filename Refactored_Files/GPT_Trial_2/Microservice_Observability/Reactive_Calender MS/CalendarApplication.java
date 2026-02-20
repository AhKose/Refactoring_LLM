package com.microservices.calendar.api;

import com.microservices.calendar.api.adapter.MultiMapExtractAdapter;
import com.microservices.calendar.api.adapter.ReactiveMultiMapExtractAdapter;
import com.microservices.calendar.api.configuration.InitConfiguration;
import io.opentracing.Scope;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.reactivex.Single;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Launcher;
import io.vertx.core.json.JsonObject;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import io.vertx.reactivex.ext.web.handler.CorsHandler;
import io.vertx.reactivex.ext.web.handler.StaticHandler;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.uber.jaeger.Configuration;
import com.uber.jaeger.micrometer.MicrometerMetricsFactory;
import com.uber.jaeger.samplers.ConstSampler;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.opentracing.Span;
import io.opentracing.Tracer;
import static com.microservices.calendar.api.controller.Errors.error;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static io.vertx.core.http.HttpMethod.*;

public class CalendarApplication extends AbstractVerticle {
    private static Logger logger = LoggerFactory.getLogger(CalendarApplication.class);

    private final String API_KEY = "Reactive-Calendar-API-User-Key";

    public Tracer appTracer;
    public SpanContext parentSpanContext;

    public static void main(final String[] args) {
        Launcher.executeCommand("run", CalendarApplication.class.getName());
    }

    @Override
    public void start() {
        Router router = Router.router(vertx);

        configureCors(router);
        appTracer = createTracer();

        bindRootRoute(router);

        PrometheusMeterRegistry registry = registerPrometheus();
        startPrometheusHttpServer(registry);

        configureRoutes(router);

        InitConfiguration.init(vertx)
            .andThen(startHttpServer(router))
            .subscribe(
                http -> System.out.println("Server ready on port " + http.actualPort()),
                Throwable::printStackTrace
            );
    }

    private void configureCors(Router router) {
        CorsHandler cors = CorsHandler.create("*");
        cors.allowedHeader(CONTENT_TYPE.toString());
        cors.allowedMethod(POST);
        router.route().handler(cors);
    }

    private Tracer createTracer() {
        JaegerAgent agent = resolveJaegerAgent();
        MicrometerMetricsFactory metricsFactory = new MicrometerMetricsFactory();

        Configuration configuration = new Configuration("jaeger-vertx-calendar");
        return configuration
            .withReporter(
                new Configuration.ReporterConfiguration()
                    .withLogSpans(true)
                    .withFlushInterval(1000)
                    .withMaxQueueSize(10000)
                    .withSender(
                        new Configuration.SenderConfiguration()
                            .withAgentHost(agent.host)
                            .withAgentPort(agent.port)
                    )
            )
            .withSampler(
                new Configuration.SamplerConfiguration()
                    .withType(ConstSampler.TYPE)
                    .withParam(1)
            )
            .getTracerBuilder()
            .withMetricsFactory(metricsFactory)
            .build();
    }

    private JaegerAgent resolveJaegerAgent() {
        String host = getEnv("JAEGER_HOST", "localhost");
        int port = getEnvInt("JAEGER_PORT", 6831);
        return new JaegerAgent(host, port);
    }

    private void bindRootRoute(Router router) {
        router.route("/").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            response
                .putHeader("content-type", "text/html")
                .end("<h1>Reactive Calendar API Vert.x 3.6.3.redhat-00009 application</h1>");
        });
    }

    private PrometheusMeterRegistry registerPrometheus() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Metrics.addRegistry(registry);
        return registry;
    }

    private void configureRoutes(Router router) {
        router.route().handler(BodyHandler.create());
        router.post("/api/v1/event").handler(this::createEvent);
        router.get("/health").handler(rc -> rc.response().end("OK"));
        router.get().handler(StaticHandler.create());
    }

    private void startPrometheusHttpServer(PrometheusMeterRegistry registry) {
        System.out.println("VertX Prometheus listening on port: 8081");

        vertx.createHttpServer()
            .requestHandler(req -> req.response().end(registry.scrape()))
            .listen(8081);
    }

    private Single<HttpServer> startHttpServer(Router router) {
        int vertxPort = getEnvInt("HTTP_PORT", 8070);

        System.out.println("VertX app listening on port:" + vertxPort);

        return vertx
            .createHttpServer()
            .requestHandler(router)
            .rxListen(vertxPort);
    }

    private void createEvent(RoutingContext ctx) {
        parentSpanContext = extractParentSpanContext(ctx);

        printRequestHeaders(ctx);

        try (Scope scope = appTracer
            .buildSpan(ctx.request().method().toString())
            .asChildOf(parentSpanContext)
            .startActive(true)) {

            logger.info("VertX - checking request header");
            String userApiKey = requireUserApiKey(ctx);
            if (userApiKey == null) {
                return;
            }

            logger.info("VertX - checking request body");
            JsonObject item = parseJsonBodyOrRespondError(ctx);
            if (item == null) {
                return;
            }

            logEventPayload(item);

            item.put("synced", true);
            sendCreated(ctx, item);

            System.out.println("received new reactive-calendar event from user api-key=" + userApiKey);
        }
    }

    private SpanContext extractParentSpanContext(RoutingContext ctx) {
        return appTracer.extract(
            Format.Builtin.HTTP_HEADERS,
            new ReactiveMultiMapExtractAdapter(ctx.request().headers())
        );
    }

    private void printRequestHeaders(RoutingContext ctx) {
        System.out.println("\n\n\n");
        System.out.println("REQUEST HEADERS");
        ctx.request().headers().entries().forEach(e -> System.out.println(e.getKey() + ":" + e));
    }

    private String requireUserApiKey(RoutingContext ctx) {
        String userApiKey = ctx.request().getHeader(API_KEY);
        if (userApiKey == null) {
            logger.info("invalid header: " + API_KEY + " not found");
            error(ctx, 400, "invalid header: " + API_KEY + " not found");
            return null;
        }
        return userApiKey;
    }

    private JsonObject parseJsonBodyOrRespondError(RoutingContext ctx) {
        try {
            JsonObject item = ctx.getBodyAsJson();
            if (item == null) {
                logger.info("invalid payload: expecting json");
                error(ctx, 415, "invalid payload: expecting json");
                return null;
            }
            return item;
        } catch (RuntimeException e) {
            logger.info("invalid payload: expecting json");
            error(ctx, 415, "invalid payload: expecting json");
            return null;
        }
    }

    private void logEventPayload(JsonObject item) {
        logger.info("VertX - post new event");
        logger.info("start: " + item.getValue("startDate"));
        logger.info("end: " + item.getValue("endDate"));
        logger.info("calories: " + item.getInteger("calories"));
    }

    private void sendCreated(RoutingContext ctx, JsonObject item) {
        ctx.response()
            .putHeader("Location", "/api/v1/event/" + item.getLong("id"))
            .putHeader("Content-Type", "application/json")
            .setStatusCode(201)
            .end(item.encodePrettily());
    }

    private int getEnvInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(getEnv(key, String.valueOf(defaultValue)));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String getEnv(String key, String defaultValue) {
        String s = System.getenv(key);
        if (s == null) {
            return defaultValue;
        }
        return s;
    }

    private static final class JaegerAgent {
        private final String host;
        private final int port;

        private JaegerAgent(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    private void writeError(RoutingContext ctx, Throwable err) {
        if (err instanceof NoSuchElementException) {
            error(ctx, 404, err);
        } else if (err instanceof IllegalArgumentException) {
            error(ctx, 422, err);
        } else {
            error(ctx, 409, err);
        }
    }

    private String getEnv(String key, String defaultValue) {
        String s = System.getenv(key);
        if (s == null) {
            return defaultValue;
        }
        return s;
    }
}
