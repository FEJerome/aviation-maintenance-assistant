package cn.pandazi.aviation_maintenance_assistant.chat.service;

import cn.pandazi.aviation_maintenance_assistant.chat.dto.ChatResponse;
import cn.pandazi.aviation_maintenance_assistant.chat.dto.Citation;
import cn.pandazi.aviation_maintenance_assistant.config.AmaProperties;
import cn.pandazi.aviation_maintenance_assistant.rag.RetrievedExcerpt;
import cn.pandazi.aviation_maintenance_assistant.rag.RetrievalService;
import cn.pandazi.aviation_maintenance_assistant.service.LlmCallExecutor;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GroundedChatService {

    public record GroundedRequest(String userMessage, String systemPrompt, String traceId) {
    }

    public record GroundedResult(
            String reply,
            ChatResponse.Outcome outcome,
            ChatResponse.GenerationMode generationMode,
            boolean degraded,
            String degradationReason,
            List<Citation> citations) {
        public GroundedResult {
            citations = citations == null ? List.of() : List.copyOf(citations);
        }
    }

    public interface StreamObserver {
        void onToken(String token);

        void onComplete(GroundedResult result);

        void onError(Throwable error);
    }

    private static final String NO_ANSWER = "未在手册中找到相关内容。";

    private final RetrievalService retrievalService;
    private final LlmCallExecutor callExecutor;
    private final AmaProperties properties;

    public GroundedChatService(
            RetrievalService retrievalService,
            LlmCallExecutor callExecutor,
            AmaProperties properties) {
        this.retrievalService = retrievalService;
        this.callExecutor = callExecutor;
        this.properties = properties;
    }

    public GroundedResult respond(GroundedRequest request) {
        List<RetrievedExcerpt> excerpts = retrievalService.retrieve(request.userMessage(), request.traceId());
        if (excerpts.isEmpty()) {
            return noAnswer();
        }
        return respondWithSources(request, excerpts, false);
    }

    public void respondStream(GroundedRequest request, StreamObserver observer) {
        List<RetrievedExcerpt> excerpts = retrievalService.retrieve(request.userMessage(), request.traceId());
        if (excerpts.isEmpty()) {
            GroundedResult result = noAnswer();
            observer.onToken(result.reply());
            observer.onComplete(result);
            return;
        }

        if (!properties.getLlm().isStreamingEnabled()) {
            GroundedResult result = respondWithSources(request, excerpts, true);
            observer.onToken(result.reply());
            observer.onComplete(result);
            return;
        }

        List<Citation> citations = citations(excerpts);
        ChatRequest chatRequest = chatRequest(request, excerpts);
        callExecutor.executeStreaming(chatRequest, request.traceId(), new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                observer.onToken(partialResponse);
            }

            @Override
            public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse response) {
                observer.onComplete(new GroundedResult(
                        response.aiMessage().text(),
                        ChatResponse.Outcome.ANSWERED,
                        ChatResponse.GenerationMode.STREAMING,
                        false,
                        null,
                        citations));
            }

            @Override
            public void onError(Throwable error) {
                observer.onError(error);
            }
        });
    }

    private GroundedResult respondWithSources(
            GroundedRequest request, List<RetrievedExcerpt> excerpts, boolean streamingFallback) {
        String reply = callExecutor.executeBlocking(chatRequest(request, excerpts), request.traceId())
                .aiMessage()
                .text();
        return new GroundedResult(
                reply,
                ChatResponse.Outcome.ANSWERED,
                ChatResponse.GenerationMode.NON_STREAMING,
                streamingFallback,
                streamingFallback ? "STREAMING_DISABLED" : null,
                citations(excerpts));
    }

    private ChatRequest chatRequest(GroundedRequest request, List<RetrievedExcerpt> excerpts) {
        StringBuilder sources = new StringBuilder();
        for (int index = 0; index < excerpts.size(); index++) {
            RetrievedExcerpt excerpt = excerpts.get(index);
            sources.append("\n\n[S").append(index + 1).append("] ")
                    .append("excerpt_id=").append(excerpt.excerptId())
                    .append(", document_id=").append(excerpt.documentId())
                    .append(", document_title=").append(excerpt.documentTitle())
                    .append(", chunk_index=").append(excerpt.chunkIndex())
                    .append("\n")
                    .append(excerpt.text());
        }
        return ChatRequest.builder()
                .messages(
                        SystemMessage.from(request.systemPrompt()),
                        UserMessage.from(request.userMessage() + sources))
                .build();
    }

    private List<Citation> citations(List<RetrievedExcerpt> excerpts) {
        return excerpts.stream().map(RetrievedExcerpt::toCitation).toList();
    }

    private GroundedResult noAnswer() {
        return new GroundedResult(
                NO_ANSWER,
                ChatResponse.Outcome.NO_ANSWER,
                ChatResponse.GenerationMode.NONE,
                false,
                null,
                List.of());
    }
}
