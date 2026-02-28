package io.github.code2spec.llm;

import io.github.code2spec.core.model.BusinessSemantic;
import io.github.code2spec.core.model.ErrorCode;

/**
 * No-op LLM enhancer when API is not configured.
 */
public class NoOpLlmEnhancer implements LlmEnhancer {
    @Override
    public BusinessSemantic enhanceEndpoint(EndpointContext ctx) {
        return null;
    }

    @Override
    public void enhanceErrorCode(ErrorCode errorCode, ErrorCodeContext ctx) {
        // no-op
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
