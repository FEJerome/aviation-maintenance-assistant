package cn.pandazi.aviation_maintenance_assistant.chat.service;

import cn.pandazi.aviation_maintenance_assistant.chat.dto.ChatResponse;
import cn.pandazi.aviation_maintenance_assistant.chat.session.ChatSessionStore;
import cn.pandazi.aviation_maintenance_assistant.rag.RetrievedExcerpt;
import cn.pandazi.aviation_maintenance_assistant.validation.service.AircraftValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MaintenanceChatServiceTest {

    private GroundedChatService grounded;
    private ChatSessionStore sessions;
    private MaintenanceChatService service;

    @BeforeEach
    void setUp() {
        grounded = mock(GroundedChatService.class);
        sessions = new ChatSessionStore();
        service = new MaintenanceChatService(new AircraftValidationService(), sessions, grounded);
        when(grounded.respond(any())).thenReturn(result());
    }

    @Test
    void missingEngineRequiresClarificationWithoutGeneration() {
        ChatResponse response = service.process(null, "CTLS engine oil pressure");

        assertThat(response.outcome()).isEqualTo(ChatResponse.Outcome.CLARIFICATION_REQUIRED);
        assertThat(response.generationMode()).isEqualTo(ChatResponse.GenerationMode.NONE);
        verifyNoInteractions(grounded);
    }

    @Test
    void validatedFollowUpReusesContextButEveryTurnIsRouted() {
        service.process("conversation", "CTLS Rotax 912 engine oil pressure");
        service.process("conversation", "oil temperature");

        ArgumentCaptor<GroundedChatService.GroundedRequest> request =
                ArgumentCaptor.forClass(GroundedChatService.GroundedRequest.class);
        verify(grounded, org.mockito.Mockito.times(2)).respond(request.capture());
        assertThat(request.getAllValues().get(1).userMessage())
                .contains("CTLS")
                .contains("ROTAX 912")
                .contains("oil temperature");
        assertThat(sessions.get("conversation").confirmedModel()).isEqualTo("CTLS");
    }

    @Test
    void newMismatchIsRevalidatedAndDoesNotOverwriteTheValidatedSession() {
        service.process("conversation", "CTLS Rotax 912 engine oil pressure");

        ChatResponse blocked = service.process(
                "conversation", "B737-800 PW1100G engine oil pressure");

        assertThat(blocked.outcome()).isEqualTo(ChatResponse.Outcome.VALIDATION_BLOCKED);
        assertThat(sessions.get("conversation").confirmedModel()).isEqualTo("CTLS");
        assertThat(sessions.get("conversation").confirmedEngine()).isEqualTo("ROTAX 912");
        verify(grounded).respond(any());
    }

    @Test
    void generalQuestionDoesNotReuseAircraftContextOrSkipGrounding() {
        service.process("conversation", "CTLS Rotax 912 engine oil pressure");
        service.process("conversation", "What does AMM mean?");

        ArgumentCaptor<GroundedChatService.GroundedRequest> request =
                ArgumentCaptor.forClass(GroundedChatService.GroundedRequest.class);
        verify(grounded, org.mockito.Mockito.times(2)).respond(request.capture());
        assertThat(request.getAllValues().get(1).userMessage()).isEqualTo("What does AMM mean?");
    }

    @Test
    void blockingAndStreamingShareTheSamePreparedRequest() {
        service.process("blocking", "CTLS Rotax 912 engine oil pressure");
        doNothing().when(grounded).respondStream(any(), any());

        service.processStream("streaming", "CTLS Rotax 912 engine oil pressure", new SseEmitter());

        ArgumentCaptor<GroundedChatService.GroundedRequest> blocking =
                ArgumentCaptor.forClass(GroundedChatService.GroundedRequest.class);
        ArgumentCaptor<GroundedChatService.GroundedRequest> streaming =
                ArgumentCaptor.forClass(GroundedChatService.GroundedRequest.class);
        verify(grounded).respond(blocking.capture());
        verify(grounded).respondStream(streaming.capture(), any());
        assertThat(streaming.getValue().userMessage()).isEqualTo(blocking.getValue().userMessage());
        assertThat(streaming.getValue().systemPrompt()).isEqualTo(blocking.getValue().systemPrompt());
    }

    @Test
    void processWithExcerptsExposesRetrievedEvidenceAndProcessDelegates() {
        RetrievedExcerpt first = new RetrievedExcerpt(
                "first excerpt", "excerpt-1", "doc-1", "Manual", 0, 0.9);
        RetrievedExcerpt second = new RetrievedExcerpt(
                "second excerpt", "excerpt-2", "doc-1", "Manual", 1, 0.8);
        when(grounded.respond(any())).thenReturn(new GroundedChatService.GroundedResult(
                "Synthetic answer",
                ChatResponse.Outcome.ANSWERED,
                ChatResponse.GenerationMode.NON_STREAMING,
                false,
                null,
                List.of(),
                List.of(first, second)));

        MaintenanceChatService.ProcessedAnswer answer =
                service.processWithExcerpts("conversation", "CTLS Rotax 912 engine oil pressure");
        ChatResponse response = service.process("conversation", "CTLS Rotax 912 engine oil pressure");

        assertThat(answer.excerpts()).containsExactly(first, second);
        assertThat(answer.response().outcome()).isEqualTo(ChatResponse.Outcome.ANSWERED);
        assertThat(response.outcome()).isEqualTo(answer.response().outcome());
        assertThat(response.reply()).isEqualTo(answer.response().reply());
    }

    @Test
    void processWithExcerptsShortcutCarriesNoEvidence() {
        MaintenanceChatService.ProcessedAnswer answer =
                service.processWithExcerpts("conversation", "CTLS engine oil pressure");

        assertThat(answer.response().outcome()).isEqualTo(ChatResponse.Outcome.CLARIFICATION_REQUIRED);
        assertThat(answer.excerpts()).isEmpty();
    }

    private GroundedChatService.GroundedResult result() {
        return new GroundedChatService.GroundedResult(
                "Synthetic answer",
                ChatResponse.Outcome.ANSWERED,
                ChatResponse.GenerationMode.NON_STREAMING,
                false,
                null,
                List.of(),
                List.of());
    }
}
