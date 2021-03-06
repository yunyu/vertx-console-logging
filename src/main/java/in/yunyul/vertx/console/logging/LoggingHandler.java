package in.yunyul.vertx.console.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

class LoggingHandler {
    private final EventBus eventBus;
    private static final String PREFIX = "vertx.console.logger.";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String ROOT_LOGGER_NAME = ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME;

    LoggingHandler(Router router, Vertx vertx, String basePath) {
        this.eventBus = vertx.eventBus();

        // Set up streaming
        SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
        // Allow log broadcasts
        PermittedOptions tweetPermitted = new PermittedOptions().setAddressRegex("vertx\\.console\\.logger\\..+");
        BridgeOptions options = new BridgeOptions()
                // No incoming messages permitted
                .addOutboundPermitted(tweetPermitted);
        sockJSHandler.bridge(options);
        router.route(basePath + "/loggerproxy/*").handler(sockJSHandler);

        // Set up appender
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        Appender<ILoggingEvent> eventBusAppender = new EventBusAppender();
        eventBusAppender.setContext(lc);
        eventBusAppender.start();
        lc.getLogger(ROOT_LOGGER_NAME).addAppender(eventBusAppender);

        // Set up routes
        router.route(basePath + "/loggers*").handler(BodyHandler.create());

        router.route(HttpMethod.POST, basePath + "/loggers/:logger/update")
                .consumes(JSON_CONTENT_TYPE).produces(JSON_CONTENT_TYPE)
                .handler(ctx -> {
                    String loggerName = ctx.request().getParam("logger");
                    ch.qos.logback.classic.Logger logger = lc.exists(loggerName);
                    if (logger == null) {
                        sendError(ctx.response(), 404, "logger_not_found");
                        return;
                    }

                    JsonObject body = ctx.getBodyAsJson();
                    Level level = Level.toLevel(body.getString("level"), null);
                    if (level == null) {
                        sendError(ctx.response(), 400, "invalid_level");
                        return;
                    }
                    logger.setLevel(level);

                    String include = body.getString("include");
                    if (include != null && include.equals("all")) {
                        JsonArray loggers = new JsonArray();
                        for (ch.qos.logback.classic.Logger log : lc.getLoggerList()) {
                            loggers.add(getLoggerInfo(log));
                        }
                        ctx.response().putHeader("content-type", JSON_CONTENT_TYPE).end(loggers.encode());
                    } else {
                        ctx.response().putHeader("content-type", JSON_CONTENT_TYPE).end(getLoggerInfo(logger).encode());
                    }
                });

        router.route(HttpMethod.GET, basePath + "/loggers/:logger")
                .produces(JSON_CONTENT_TYPE)
                .handler(ctx -> {
                    String loggerName = ctx.request().getParam("logger");
                    ch.qos.logback.classic.Logger logger = lc.exists(loggerName);
                    if (logger == null) {
                        sendError(ctx.response(), 404, "logger_not_found");
                        return;
                    }

                    ctx.response().putHeader("content-type", JSON_CONTENT_TYPE).end(getLoggerInfo(logger).encode());
                });

        router.route(HttpMethod.GET, basePath + "/loggers").produces(JSON_CONTENT_TYPE).handler(ctx -> {
            JsonArray loggers = new JsonArray();
            for (ch.qos.logback.classic.Logger log : lc.getLoggerList()) {
                loggers.add(getLoggerInfo(log));
            }
            ctx.response().putHeader("content-type", JSON_CONTENT_TYPE).end(loggers.encode());
        });
    }

    private static void sendError(HttpServerResponse res, int status, String error) {
        JsonObject result = new JsonObject();
        result.put("status", status);
        result.put("error", error);
        res.setStatusCode(status).putHeader("content-type", JSON_CONTENT_TYPE).end(result.encode());
    }

    private static JsonObject getLoggerInfo(ch.qos.logback.classic.Logger logger) {
        JsonObject loggerInfo = new JsonObject();
        loggerInfo.put("name", logger.getName());
        loggerInfo.put("effectiveLevel", logger.getEffectiveLevel().toString());
        return loggerInfo;
    }

    public class EventBusAppender extends AppenderBase<ILoggingEvent> {
        // Prevent broadcast feedback loop
        private final List<String> debugBroadcastBlacklist = Arrays.asList(
                "io.netty.handler.codec.http.websocketx.WebSocket08FrameEncoder",
                "io.vertx.ext.web.handler.sockjs.impl.WebSocketTransport",
                "io.vertx.ext.web.handler.sockjs.impl.XhrTransport"
        );

        @Override
        protected void append(ILoggingEvent event) {
            if (!event.getLevel().isGreaterOrEqual(Level.INFO) &&
                    debugBroadcastBlacklist.contains(event.getLoggerName())) {
                return;
            }

            JsonObject eventJson = new JsonObject();
            eventJson.put("date", event.getTimeStamp());
            eventJson.put("level", event.getLevel().toString());
            eventJson.put("message", event.getMessage());
            eventJson.put("thread", event.getThreadName());
            eventJson.put("logger", event.getLoggerName());

            eventBus.publish(PREFIX + event.getLoggerContextVO().getName(), eventJson.toString());
        }
    }
}
