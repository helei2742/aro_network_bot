package cn.com.vortexa.aro_network.service.impl;


import cn.com.vortexa.aro_network.AroNetworkBot;
import cn.com.vortexa.aro_network.service.AroNetworkApi;
import cn.com.vortexa.aro_network.websocket.AROClient;
import cn.com.vortexa.base.constants.HeaderKey;
import cn.com.vortexa.base.util.log.AppendLogger;
import cn.com.vortexa.bot_template.bot.dto.FullAccountContext;
import cn.com.vortexa.bot_template.exception.BotInvokeException;
import cn.com.vortexa.common.constants.HttpMethod;
import cn.com.vortexa.common.util.CastUtil;
import cn.com.vortexa.common.util.http.RestApiClientFactory;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * @author helei
 * @since 2025-09-29
 */
public class AroNetworkApiImpl implements AroNetworkApi {
    private static final String BASE_URL = "https://testnet-api.aro.network/api";
    private static final String WS_URL = "wss://testnet-ws.aro.network/socket.io/?EIO=4&transport=websocket";
    public static final String NODE_ID_PATTERN = "node_id_";
    public static final String USER_ID_KEY = "user_id";

    private final AroNetworkBot aroNetworkBot;

    public AroNetworkApiImpl(AroNetworkBot aroNetworkBot) {
        this.aroNetworkBot = aroNetworkBot;
    }

    @Override
    public Map<String, Object> startEarnPoint(
            FullAccountContext fullAccountContext, int retry, int reconnectDelay, AppendLogger logger
    ) throws BotInvokeException {
        try {
            String userId = CastUtil.autoCast(fullAccountContext.getParam(USER_ID_KEY));
            if (StrUtil.isBlank(userId)) {
                throw new IllegalArgumentException("user id is empty");
            }
            List<String> nodeIdList = fullAccountContext.getParams().entrySet().stream()
                    .filter(e -> e.getKey().startsWith(NODE_ID_PATTERN))
                    .filter(e -> e.getValue() != null)
                    .map(e -> (String) e.getValue())
                    .toList();
            if (CollUtil.isEmpty(nodeIdList)) {
                throw new IllegalArgumentException("node id is empty");
            }

            ExecutorService threadPool = aroNetworkBot.getVortexaBotContext().getThreadPool();
            List<CompletableFuture<Integer>> futures = nodeIdList.stream()
                    .map(nodeId -> createEarnPointFuture(
                            fullAccountContext, nodeId, userId, retry, reconnectDelay, threadPool, logger
                    ))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            Map<String, Object> result = new HashMap<>();
            for (int i = 0; i < futures.size(); i++) {
                String nodeId = nodeIdList.get(i);
                try {
                    Integer pingCount = futures.get(i).get();
                    result.put(nodeId, pingCount);
                } catch (Exception e) {
                    logger.error("node[%s] earn error".formatted(nodeId), e.getCause() == null ? e : e.getCause());
                    result.put(nodeId, e.getMessage());
                }
            }
            return result;
        } catch (Exception e) {
            throw new BotInvokeException("start earn point error", e);
        }
    }

    @Override
    public Double pointQuery(FullAccountContext fullAccountContext, AppendLogger logger) throws ExecutionException, InterruptedException {
        logger.info("start query point...");
        return request(
                fullAccountContext,
                "/edgeNode/node/rewards",
                HttpMethod.GET,
                null,
                null,
                true
        ).thenApply(data -> {
            Double total = data.getDouble("total");
            fullAccountContext.getRewordInfo().setPoint(total);
            return total;
        }).get();
    }

    private CompletableFuture<JSONObject> request(
            FullAccountContext fullAccountContext,
            String path,
            HttpMethod httpMethod,
            Map<String, Object> params,
            Map<String, Object> body,
            boolean auth
    ) {
        return RestApiClientFactory.getClient(fullAccountContext.getProxy()).jsonRequest(
                BASE_URL + path,
                httpMethod,
                auth ? buildAuthHeader(fullAccountContext) : buildHeader(fullAccountContext),
                params == null ? null : new JSONObject(params),
                body == null ? null : new JSONObject(body)
        ).thenApply(result -> {
            if (result.getInteger("code") != 200) {
                throw new RuntimeException("request error, " + result.get("message"));
            }
            return result.getJSONObject("data");
        });
    }

    private Map<String, String> buildAuthHeader(FullAccountContext fullAccountContext) {
        Map<String, String> headers = buildHeader(fullAccountContext);
        String token = fullAccountContext.getTokenInfo().getToken();
        if (StrUtil.isBlank(token)) {
            throw new IllegalArgumentException("token is empty");
        }
        headers.put(HeaderKey.AUTHORIZATION, token);
        return headers;
    }

    private Map<String, String> buildHeader(FullAccountContext fullAccountContext) {
        Map<String, String> headers = fullAccountContext.buildHeader();
        headers.put(HeaderKey.REFERER, "https://dashboard.aro.network/");
        headers.put(HeaderKey.ORIGIN, "https://dashboard.aro.network");
        headers.put(HeaderKey.CONTENT_TYPE, "application/json");
        return headers;
    }

    @NotNull
    private static CompletableFuture<Integer> createEarnPointFuture(
            FullAccountContext fullAccountContext,
            String nodeId,
            String userId,
            int retry,
            int reconnectDelay,
            ExecutorService threadPool,
            AppendLogger logger
    ) {
        AppendLogger appendLogger = logger.newOne();
        appendLogger.append("node[%s]".formatted(nodeId));
        return CompletableFuture.supplyAsync(() -> {
            AROClient aroClient = new AROClient(
                    WS_URL,
                    fullAccountContext.getProxy(),
                    userId,
                    nodeId,
                    retry,
                    reconnectDelay,
                    threadPool,
                    appendLogger
            );
            try {
                aroClient.start();
                return aroClient.getPintCount();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, threadPool);
    }
}
