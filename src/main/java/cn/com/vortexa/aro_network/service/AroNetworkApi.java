package cn.com.vortexa.aro_network.service;

import cn.com.vortexa.base.util.log.AppendLogger;
import cn.com.vortexa.bot_template.bot.dto.FullAccountContext;
import cn.com.vortexa.bot_template.exception.BotInvokeException;

import java.util.Map;
import java.util.concurrent.ExecutionException;

public interface AroNetworkApi {

    Map<String, Object> startEarnPoint(FullAccountContext fullAccountContext, int retry, int reconnectDelay, AppendLogger logger) throws BotInvokeException;

    Double pointQuery(FullAccountContext fullAccountContext, AppendLogger logger) throws ExecutionException, InterruptedException;
}
