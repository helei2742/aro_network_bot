package cn.com.vortexa.aro_network.websocket;

import cn.com.vortexa.base.util.log.AppendLogger;
import cn.com.vortexa.common.interfaces.SystemProxy;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class AROClient extends SocketIOClient {
    private static final int PING_INTERVAL_SECONDS = 180;

    private final String userId;
    private final String nodeId;
    private final int retryLimit;
    private final int reconnectDelay;
    private final AppendLogger logger;
    private final ExecutorService executorService;
    private PingTask pingTask;

    private final AtomicInteger pingCounter = new AtomicInteger(0);
    private final AtomicInteger reconnectCounter = new AtomicInteger(0);

    public AROClient(
            String wsUrl,
            SystemProxy proxy,
            String userId,
            String nodeId,
            int retry,
            int reconnectDelay,
            ExecutorService executorService,
            AppendLogger logger
    ) {
        super(wsUrl, proxy);
        this.userId = userId;
        this.nodeId = nodeId;
        if (this.userId == null || this.nodeId == null) {
            throw new IllegalArgumentException("user id[%s] or node is[%s] is empty".formatted(
                    this.userId, this.nodeId
            ));
        }
        this.retryLimit = retry;
        this.reconnectDelay = reconnectDelay;
        this.executorService = executorService;
        this.logger = logger;
    }

    @Override
    public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
        super.onClosing(webSocket, code, reason);
    }

    @NotNull
    @Override
    protected Object handlerOpen(String content) {
        logger.debug("channel open. -> " + content);

        return JSONObject.toJSONString(Map.of("token", Map.of(
                "nodeId", nodeId,
                "userId", userId
        )));
    }

    @Override
    public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, Response response) {
        closeAndTryReconnect();
    }


    @Override
    public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
        closeAndTryReconnect();
    }

    @NotNull
    @Override
    protected MessageResponse handlerConnect(String content) {
        logger.debug("channel connected. -> " + content);
        return MessageResponse.noResponse();
    }

    @NotNull
    @Override
    protected MessageResponse handlerEvent(String event, Object data) {
        logger.debug("channel event -> %s[%s]".formatted(event, data));
        return switch (event) {
            case "auth" -> {
                logger.debug("start send pint task...");
                pingTask = new PingTask(
                        getWebSocket(),
                        PING_INTERVAL_SECONDS,
                        JSONObject.toJSONString(Map.of(
                                "nodeId", nodeId,
                                "userId", userId
                        )),
                        pingCounter,
                        logger
                );
                executorService.execute(pingTask);
                yield MessageResponse.noResponse();
            }
            case "ping" -> {
                MessageResponse messageResponse = MessageResponse.eventResponse("pong", data);
                logger.debug("send pong <- " + messageResponse.toSendMsg());
                yield messageResponse;
            }
            default -> MessageResponse.noResponse();
        };
    }

    public Integer getPintCount() {
        return pingCounter.get();
    }

    private void closeAndTryReconnect() {
        if (pingTask != null) {
            pingTask.stop();
            pingTask = null;
        }
        super.close();

        if (reconnectCounter.get() <= retryLimit) {
            this.executorService.submit(() -> {
                int count = reconnectCounter.incrementAndGet();
                if (count <= retryLimit) {
                    logger.warn("reconnect after: %s(min)".formatted(reconnectDelay));
                    try {
                        TimeUnit.MINUTES.sleep(reconnectDelay);
                        if (!super.isRunning()) {
                            super.start();
                            logger.info("reconnected");
                        } else {
                            logger.warn("reconnect canceled, already running");
                        }
                    } catch (InterruptedException e) {
                        reconnectCounter.set(retryLimit + 1);   //  不让继续重试
                        super.close();
                        logger.error("reconnect interrupted.", e);
                    }
                }
            });
        }
    }

    private static class PingTask implements Runnable {
        private final WebSocket webSocket;
        private final int intervalSeconds;
        private final String pingMessage;
        private final AppendLogger logger;
        private final AtomicInteger counter;

        private volatile boolean running = true;

        private PingTask(
                WebSocket webSocket,
                int intervalSeconds,
                String pingMessage,
                AtomicInteger pingCounter,
                AppendLogger logger
        ) {
            this.webSocket = webSocket;
            this.intervalSeconds = intervalSeconds;
            this.pingMessage = pingMessage;
            this.counter = pingCounter;
            this.logger = logger;
        }

        @Override
        public void run() {
            while (running) {
                try {
                    logger.debug("send ping: " + counter.incrementAndGet());
                    webSocket.send(MessageResponse.eventResponse("ping", pingMessage).toSendMsg());
                    TimeUnit.SECONDS.sleep(intervalSeconds);
                } catch (InterruptedException e) {
                    running = false;
                    logger.warn("send ping task interrupted.");
                } catch (Exception e) {
                    logger.error("send ping error", e);
                }
            }
        }

        public void stop() {
            running = false;
        }
    }
}
