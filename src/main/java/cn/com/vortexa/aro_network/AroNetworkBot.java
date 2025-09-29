package cn.com.vortexa.aro_network;


import cn.com.vortexa.aro_network.service.AroNetworkApi;
import cn.com.vortexa.aro_network.service.impl.AroNetworkApiImpl;
import cn.com.vortexa.base.util.log.AppendLogger;
import cn.com.vortexa.bot_template.bot.AbstractVortexaBot;
import cn.com.vortexa.bot_template.bot.VortexaBotContext;
import cn.com.vortexa.bot_template.bot.anno.VortexaBot;
import cn.com.vortexa.bot_template.bot.anno.VortexaBotAPI;
import cn.com.vortexa.bot_template.bot.anno.VortexaBotCatalogueGroup;
import cn.com.vortexa.bot_template.bot.dto.FullAccountContext;
import cn.com.vortexa.bot_template.bot.handler.FullAccountContextScanner;
import cn.com.vortexa.bot_template.constants.VortexaBotApiSchedulerType;
import cn.com.vortexa.bot_template.entity.AccountContext;
import cn.com.vortexa.common.dto.PageResult;

/**
 * @author helei
 * @since 2025-09-29
 */
@VortexaBot(
        namespace = "Aro Network",
        websiteUrl = "https://aro.network/",
        catalogueGroup = {
                @VortexaBotCatalogueGroup(name = AroNetworkBot.GROUP_DEPIN),
        }
)
public class AroNetworkBot extends AbstractVortexaBot {
    public static final String GROUP_DEPIN = "Depin";
    public static final String GROUP_QUERY = "Query";

    private final AroNetworkApi aroNetworkApi;

    public AroNetworkBot(VortexaBotContext vortexaBotContext) {
        super(vortexaBotContext);
        this.aroNetworkApi = new AroNetworkApiImpl(this);
    }

    @VortexaBotAPI(
            name = "Point query",
            catalogueName = GROUP_DEPIN,
            catalogueOrder = 1,
            schedulerType = VortexaBotApiSchedulerType.NONE
    )
    public void pointQuery() {
        forEachAccountContext(new FullAccountContextScanner() {
            @Override
            public void scan(PageResult<AccountContext> pageResult, int i, FullAccountContext fullAccountContext) throws Exception {

            }

            @Override
            public Object scanWithResult(PageResult<AccountContext> page, int batchIdx, FullAccountContext fullAccountContext) throws Exception {
                AppendLogger logger = getBotMethodInvokeContext().getLogger();
                return aroNetworkApi.pointQuery(fullAccountContext, logger);
            }
        });
    }

    @VortexaBotAPI(
            name = "Start earn point",
            catalogueName = GROUP_DEPIN,
            catalogueOrder = 1,
            schedulerType = VortexaBotApiSchedulerType.NONE
    )
    public void startEarnPoint(int retry, int reconnectDelay) {
        forEachAccountContext(new FullAccountContextScanner() {
            @Override
            public void scan(PageResult<AccountContext> pageResult, int i, FullAccountContext fullAccountContext) throws Exception {

            }

            @Override
            public Object scanWithResult(PageResult<AccountContext> page, int batchIdx, FullAccountContext fullAccountContext) throws Exception {
                AppendLogger logger = getBotMethodInvokeContext().getLogger();
                return aroNetworkApi.startEarnPoint(fullAccountContext, retry, reconnectDelay, logger);
            }
        });
    }
}
