package io.github.code2spec.llm;

import io.github.code2spec.core.model.BusinessSemantic;
import io.github.code2spec.core.model.Endpoint;
import io.github.code2spec.core.model.ErrorCode;

/**
 * LLM-based enhancement for endpoints and error codes.
 */
public interface LlmEnhancer {

    /**
     * Enhance endpoint with business semantic description.
     */
    BusinessSemantic enhanceEndpoint(EndpointContext ctx);

    /**
     * Enhance error code with root cause and handling suggestions.
     */
    void enhanceErrorCode(ErrorCode errorCode, ErrorCodeContext ctx);

    /**
     * Whether LLM enhancement is enabled (e.g. API configured).
     */
    boolean isEnabled();
}
