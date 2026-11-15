package cn.pandazi.aviation_maintenance_assistant.mcp;

/**
 * MCP-facing citation: same fields as the HTTP citation plus the original manual excerpt
 * text as evidence.
 */
public record McpCitation(
        String excerptId,
        String documentId,
        String documentTitle,
        int chunkIndex,
        double score,
        String text) {
}
