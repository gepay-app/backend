package com.gepe.starter.platform.security;

import com.gepe.starter.platform.exception.ErrorCode;
import com.gepe.starter.platform.i18n.MessageHelper;
import com.gepe.starter.platform.web.response.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Menulis {@link ErrorResponse} ter-i18n langsung ke response servlet. Dipakai
 * di layer Spring Security/filter, tempat exception tidak lewat
 * {@code GlobalExceptionHandler} (yang cuma menangani exception dari
 * controller). Menjaga supaya {@code RestAuthenticationEntryPoint},
 * {@code RestAccessDeniedHandler}, dan filter modul lain menulis envelope
 * dengan format yang sama persis.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper; // Jackson 3: tools.jackson.databind.ObjectMapper
    private final MessageHelper messageHelper;

    public void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String key = code.getMessageKey();
        objectMapper.writeValue(response.getWriter(), ErrorResponse.simple(key, messageHelper.get(key)));
    }
}
