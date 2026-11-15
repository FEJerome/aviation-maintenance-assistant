/**
 * English locale resources. Keys mirror the zh-CN resources one-to-one;
 * translations follow the terminology of the English project documentation
 * (e.g. "aircraft validation layer" for 机型验证层).
 */
export default {
  app: {
    title: 'Aviation Maintenance Knowledge Assistant',
    guideButton: '📘 About This Project',
    conversationPrefix: 'Session: ',
    incrementalOn: 'Incremental: On',
    incrementalOff: 'Incremental: Off',
    newChat: 'New Chat',
    suggestedTitle: 'How can I help?',
    loading: 'Generating...',
    dataNotice: 'When using a cloud model, your questions and retrieved excerpts are sent to the configured provider.',
    inputPlaceholder: 'Type your question...',
    send: 'Send',
    suggestedQuestions: {
      ctls: 'What is the standard oil pressure value for the Rotax 912 engine on the CTLS aircraft?',
      wt9: 'What are the daily inspection requirements for the Rotax 912 landing gear retraction system on the WT9 aircraft?',
      structure: 'What are the general specifications for riveted repairs in aircraft structural repair?',
      b737: 'How should I troubleshoot low engine oil pressure on a B737-800 CFM56-7B?'
    },
    errors: {
      parseEvent: 'The server returned event data that could not be parsed',
      requestFailedWithStatus: 'Request failed (HTTP {status})',
      requestFailed: 'Request failed',
      streamInterrupted: 'The response was interrupted before the completion event arrived'
    }
  },
  chat: {
    degradedNotice: 'Non-streaming response: streaming is disabled by configuration ({reason})',
    traceIdLabel: 'Trace ID: {traceId}',
    citationsTitle: 'Retrieved Sources',
    citationName: '{title} · Excerpt {index}',
    citationMetaPrefix: 'Similarity {score} · '
  },
  guide: {
    title: 'Aviation Maintenance Assistant · Project Guide',
    close: 'Close',
    start: 'Start Chat →',
    sections: {
      background: {
        title: 'Project Background',
        content: `**Why build this project?**

Aircraft maintenance relies on **official maintenance manuals (AMM)** and **regulatory documents (FAA)**; materials for a single aircraft model easily run to hundreds of thousands of pages. When technicians hit a fault, traditional keyword search often "finds nothing" or "finds the wrong thing" — because the manuals are in English while technicians describe faults in Chinese.

**What problem does this system solve?**

- Upload **AMM / FAA regulatory** PDFs → automatic chunking, vectorization, and storage in the ChromaDB vector database
- Technicians ask questions in natural Chinese → **the query translation layer automatically converts them to English** → relevant knowledge is retrieved → answers are generated in Chinese
- Answers cite specific manual section numbers, making them traceable and verifiable`
      },
      architecture: {
        title: 'Architecture',
        content: `| Layer | Technology |
|------|---------|
| Frontend | Vue 3 + self-built incremental rendering engine (SSE streaming + real-time Markdown parsing) |
| Backend | Spring Boot 3.5 + LangChain4j 1.14 + JDK 21 |
| Vector database | ChromaDB (local Docker deployment) |
| Embedding model | all-MiniLM-L6-v2 (local ONNX, lightweight 20MB) |
| LLM | DeepSeek Chat API |
| Deployment | Docker + docker-compose one-command startup |

**Query translation layer**: an implemented Query Transformation component that translates Chinese questions into English before searching the English vector store, bridging the cross-language semantic gap.`
      },
      challenges: {
        title: 'Core Challenges',
        content: `**Challenge 1: Query translation layer (cross-language Chinese-English retrieval)**

- A user asks in Chinese: "液压系统压力低" (hydraulic system pressure low); the manual says "Hydraulic System Pressure Low"
- Searching the English vector database directly with Chinese yields very low similarity and misses the right answer
- Solution: the DeepSeek translation layer performs Query Transformation, translating Chinese questions into English before retrieval

**Challenge 2: Incremental rendering (SSE streaming + real-time Markdown parsing)**

- LLM generation is streaming; waiting for the full text before displaying leaves users waiting 5-10 seconds
- Appending characters one by one breaks Markdown syntax (e.g. "**bold**" rendered before the closing markers arrive)
- Solution: a dual-buffer architecture — unconfirmed paragraphs stay in a plain-text buffer, while complete paragraphs are rendered into an HTML buffer

**Challenge 3: RAG chunking strategy tuning**

- Chunks too large → retrieval precision drops; chunks too small → context breaks apart
- A balance between chunk size and overlap is needed to keep manual semantic units intact`
      },
      role: {
        title: 'My Role',
        content: `**Full-stack independent development**

**Backend:**

- Built the Spring Boot project skeleton from scratch
- Designed and implemented the core RAG pipeline: Load → Split → Embed → Store → Retrieve → Generate
- Developed the query translation layer (Query Transformation)
- ChromaDB vector database integration and tuning
- Agent aircraft-model validation layer architecture (routing layer + validation service + session state)

**Frontend:**

- Vue 3 single-page application architecture
- Self-built incremental rendering engine (dual buffer + paragraph-level confirmation)
- Markdown renderer (code-block highlighting, tables, lists)
- SSE streaming data reception and state management

**AI engineering:**

- DeepSeek API integration and prompt engineering
- RAG chunking strategy tuning (chunk size / overlap)
- Query translation layer design and implementation`
      },
      comparison: {
        title: 'Difference from ChatGPT',
        content: `| Dimension | ChatGPT | This project |
|------|---------|--------|
| **Knowledge source** | Generic internet data (possibly outdated, untrusted) | **AMM maintenance manuals / FAA regulations** (authoritative, traceable) |
| **Hallucination control** | No domain constraints; may invent aircraft data | Aircraft-model validation layer architecture (hard-coded constraints, mismatch blocking) |
| **Answer format** | Free text without structured references | Citations of specific manual section numbers, traceable to the original source |
| **Language support** | Mixed Chinese-English answers with unstable quality | Query translation layer ensures cross-language retrieval precision |
| **Deployment** | Cloud SaaS with data-leak risk | Local Docker deployment with full data control |`
      }
    }
  }
}
