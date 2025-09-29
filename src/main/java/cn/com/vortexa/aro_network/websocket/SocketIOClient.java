package cn.com.vortexa.aro_network.websocket;

import cn.com.vortexa.common.interfaces.SystemProxy;
import cn.com.vortexa.common.util.http.RestApiClientFactory;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONArray;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author helei
 * @since 2025-09-29
 */
@Slf4j
public abstract class SocketIOClient extends WebSocketListener {
    private static final int CLOSE_CODE = 1000;
    public static final int OPEN_CODE = 0;
    public static final int CONNECT_CODE = 40;
    public static final int EVENT_CODE = 42;
    public static final int PING_CODE = 2;
    public static final int PONG_CODE = 3;

    private final String wsUrl;
    private final OkHttpClient client;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    @Getter
    private WebSocket webSocket;
    @Getter
    private volatile boolean running = false; // 阻塞状态

    public SocketIOClient(
            String wsUrl,
            SystemProxy proxy
    ) {
        this.wsUrl = wsUrl;
        this.client = RestApiClientFactory.getClient(proxy).getOkHttpClient();
    }

    public void start() throws InterruptedException {
        lock.lock();
        try {
            if (webSocket != null) {
                webSocket.close(CLOSE_CODE, "restart close");
            }

            Request request = new Request.Builder().url(wsUrl).build();
            webSocket = client.newWebSocket(request, this);

            running = true;
            while (running) {
                condition.await(); // 阻塞
            }
        } finally {
            lock.unlock();
        }
    }


    public void close() {
        lock.lock();
        try {
            if (!running) return;
            running = false;
            if (webSocket != null) {
                webSocket.close(CLOSE_CODE, "client close");
            }
            condition.signalAll(); // 解除 start 的阻塞
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {}

    @Override
    public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
        try {
            Pair<Integer, String> codeAndContent = getMessageCode(text);
            int code = codeAndContent.getKey();
            String content = codeAndContent.getValue();
            MessageResponse response = switch (code) {
                case OPEN_CODE -> MessageResponse.connectResponse(handlerOpen(content));
                case CONNECT_CODE -> handlerConnect(content);
                case EVENT_CODE -> {
                    JSONArray arr = JSONArray.parseArray(content);
                    yield handlerEvent(arr.getString(0), arr.get(1));
                }
                case PING_CODE -> handlerPing(content);
                case PONG_CODE -> handlerPong(content);
                default -> throw new IllegalStateException("Unexpected value: " + code);
            };

            if (response.isResponse()) {
                webSocket.send(response.toSendMsg());
            }
        } catch (Exception e) {
            log.error("handle message[%s] error".formatted(text), e);
        }
    }

    @Override
    public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
        log.warn("Closing: {} {}", code, reason);
        webSocket.close(1000, null);
    }

    @Override
    public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, Response response) {
        log.error("WebSocket failure,  response: {}", response, t);
    }

    protected abstract @NotNull Object handlerOpen(String content);

    protected abstract @NotNull MessageResponse handlerConnect(String content);

    protected abstract @NotNull MessageResponse handlerEvent(String event, Object data);

    protected @NotNull MessageResponse handlerPing(String content) {
        return MessageResponse.pongResponse(null);
    }

    protected @NotNull MessageResponse handlerPong(String content) {
        return MessageResponse.noResponse();
    }


    @Data
    @Builder
    public static final class MessageResponse {
        private boolean response;
        private Object data;
        private int code;

        public static MessageResponse connectResponse(Object data) {
            return MessageResponse.builder().response(true).code(CONNECT_CODE).data(data).build();
        }

        public static MessageResponse pongResponse(Object data) {
            return MessageResponse.builder().response(true).code(PONG_CODE).data(data).build();
        }

        public static MessageResponse noResponse() {
            return MessageResponse.builder().response(false).build();
        }

        public static MessageResponse eventResponse(String event, Object data) {
            return MessageResponse.builder().response(true).code(EVENT_CODE)
                    .data(JSONArray.toJSONString(List.of(event, data))).build();
        }

        public String toSendMsg() {
            if (data == null) {
                return "" + code;
            } else {
                return "" + code + data;
            }
        }
    }

    private Pair<Integer, String> getMessageCode(String text) {
        if (StrUtil.isEmpty(text)) {
            throw new IllegalArgumentException("text is empty");
        }
        char[] charArray = text.toCharArray();
        StringBuilder numberSB = new StringBuilder();
        for (char c : charArray) {
            if (Character.isDigit(c)) {
                numberSB.append(c);
            } else {
                break;
            }
        }
        return Pair.of(Integer.parseInt(numberSB.toString()), text.substring(numberSB.length()));
    }
}
