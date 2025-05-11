package xyz.zzk.deepseek.config;

import io.github.pigmesh.ai.deepseek.core.DeepSeekClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zzk
 * @version 1.0
 * @description TODO
 * @since 2025/5/10
 */
@Configuration
public class DeepSeekConfig {
    @Value("${deepseek.api-key}")
    String apiKey;

    @Value("${deepseek.base-url}")
    String baseUrl;

    @Bean
    DeepSeekClient deepSeekClient() {
        return DeepSeekClient.builder()
                .openAiApiKey(apiKey) // 替换你的 API Key
                .baseUrl(baseUrl)
                .build();
    }

}




