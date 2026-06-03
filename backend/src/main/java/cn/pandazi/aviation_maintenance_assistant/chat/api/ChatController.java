package cn.pandazi.aviation_maintenance_assistant.chat.api;

import cn.pandazi.aviation_maintenance_assistant.chat.dto.ChatRequest;
import cn.pandazi.aviation_maintenance_assistant.chat.dto.ChatResponse;
import cn.pandazi.aviation_maintenance_assistant.chat.service.MaintenanceChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final MaintenanceChatService chatService;

    public ChatController(MaintenanceChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return chatService.process(request.conversationId(), request.message());
    }
}
