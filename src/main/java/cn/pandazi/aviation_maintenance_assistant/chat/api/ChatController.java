package cn.pandazi.aviation_maintenance_assistant.chat.api;

import cn.pandazi.aviation_maintenance_assistant.chat.dto.ChatResponse;
import cn.pandazi.aviation_maintenance_assistant.chat.service.Assistant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final Assistant assistant;

    public ChatController(Assistant assistant) {
        this.assistant = assistant;
    }

    @GetMapping
    public ChatResponse chat(@RequestParam String message) {
        String reply = assistant.chat(message);
        return new ChatResponse(reply);
    }
}
