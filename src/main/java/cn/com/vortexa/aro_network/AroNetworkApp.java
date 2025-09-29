package cn.com.vortexa.aro_network;

import cn.com.vortexa.bot_template.BotTemplateAutoConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hello world!
 *
 */
@SpringBootApplication
@ImportAutoConfiguration(BotTemplateAutoConfig.class)
public class AroNetworkApp {
    public static void main( String[] args ) {
        SpringApplication.run(AroNetworkApp.class, args);
    }
}
