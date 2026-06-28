package com.upi.IBS.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ReadListener;
import java.io.ByteArrayInputStream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

@Component
public class HmacAuthFilter extends OncePerRequestFilter {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_KEY = "hmac_signature";

    private final ObjectMapper objectMapper;
    private final String secretKey;

    public HmacAuthFilter(ObjectMapper objectMapper, @Value("${hmac.secret}") String secretKey) {
        this.objectMapper = objectMapper;
        this.secretKey = secretKey;
    }

   @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        if ("/bank/sign".equals(uri) || "/bank/test/add-account".equals(uri) || "/bank/reversal".equals(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Use custom wrapper to read request body without consuming the stream permanently
        CachedBodyHttpServletRequest requestWrapper = new CachedBodyHttpServletRequest(request);
        String bodyString = new String(requestWrapper.getCachedBody(), StandardCharsets.UTF_8);

        // For GET requests or empty bodies — skip HMAC check
        if (bodyString.isEmpty()) {
            filterChain.doFilter(requestWrapper, response);
            return;
        }

        try {
            Map<String, Object> bodyMap = objectMapper.readValue(bodyString, new TypeReference<>() {});

            if (!bodyMap.containsKey(SIGNATURE_KEY)) {
                sendError(response, "Request is missing 'hmac_signature' field.");
                return;
            }

            String receivedSignature = (String) bodyMap.remove(SIGNATURE_KEY);
            if (receivedSignature == null || receivedSignature.isBlank()) {
                sendError(response, "'hmac_signature' field cannot be blank.");
                return;
            }

            Map<String, Object> sortedBodyMap = new TreeMap<>(bodyMap);
            String payloadToSign = objectMapper.writeValueAsString(sortedBodyMap);
            String calculatedSignature = calculateHmac(payloadToSign);

            if (!calculatedSignature.equals(receivedSignature)) {
                sendError(response, "Invalid HMAC signature.");
                return;
            }

        } catch (Exception e) {
            logger.error("Error during HMAC validation", e);
            sendError(response, "Error processing request signature.");
            return;
        }

        filterChain.doFilter(requestWrapper, response);
    }

    private String calculateHmac(String data) throws Exception {
        Mac sha256Hmac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        sha256Hmac.init(secretKeySpec);
        byte[] hmacBytes = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }

    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }

    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = request.getInputStream().readAllBytes();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return byteArrayInputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                }

                @Override
                public int read() {
                    return byteArrayInputStream.read();
                }
            };
        }

        public byte[] getCachedBody() {
            return this.cachedBody;
        }
    }
}