package cn.com.vortexa.aro_network.service.impl;


import cn.com.vortexa.aro_network.AroNetworkBot;
import cn.com.vortexa.aro_network.service.AroNetworkApi;
import cn.com.vortexa.aro_network.websocket.AROClient;
import cn.com.vortexa.base.util.log.AppendLogger;
import cn.com.vortexa.bot_template.bot.dto.FullAccountContext;
import cn.com.vortexa.bot_template.exception.BotInvokeException;
import cn.com.vortexa.common.util.CastUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
                            fullAccountContext, nodeId, userId, retry,reconnectDelay, threadPool, logger
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
