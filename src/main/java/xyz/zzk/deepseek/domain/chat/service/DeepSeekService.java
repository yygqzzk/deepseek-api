package xyz.zzk.deepseek.domain.chat.service;

import xyz.zzk.deepseek.application.IDeepSeekService;
import io.github.pigmesh.ai.deepseek.core.DeepSeekClient;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionRequest;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionResponse;
import io.github.pigmesh.ai.deepseek.core.chat.UserMessage;
import org.springframework.stereotype.Service;

/**
 * @author zzk
 * @version 1.0
 * @description TODO
 * @since 2025/5/10
 */
@Service
public class DeepSeekService implements IDeepSeekService {

    private final DeepSeekClient deepSeekClient;

    public DeepSeekService(DeepSeekClient deepSeekClient) {
        this.deepSeekClient = deepSeekClient;
    }

    public String chat(String prompt) {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("deepseek-chat")
                .messages(UserMessage.from(prompt+ "，请你以100字以内简短回答"))
                .stream(false) // 非流式
                .build();

        ChatCompletionResponse response = deepSeekClient.chatCompletion(request).execute();
        return response.choices().get(0).message().content();
    }
}



