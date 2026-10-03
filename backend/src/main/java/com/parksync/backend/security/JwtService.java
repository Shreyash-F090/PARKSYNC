package com.parksync.backend.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parksync.backend.model.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Component
public class JwtService {
    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private final String secret;
    private final Duration lifetime;
    private final ObjectMapper mapper;
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder decoder = Base64.getUrlDecoder();

    public JwtService(@Value("${parksync.jwt.secret:}") String secret,
                      @Value("${parksync.jwt.lifetime:PT2H}") Duration lifetime,
                      ObjectMapper mapper) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Set APP_JWT_SECRET (at least 32 characters) or SESSION_SECRET before starting PARKSYNC.");
        }
        this.secret = secret;
        this.lifetime = lifetime;
        this.mapper = mapper;
    }

    public String issue(AppUser user) {
        try {
            Instant now = Instant.now();
            String header = encoder.encodeToString(HEADER.getBytes(StandardCharsets.UTF_8));
            String payloadJson = mapper.createObjectNode()
                    .put("sub", user.getId())
                    .put("email", user.getEmail())
                    .put("role", user.getRole().name())
                    .put("ver", user.getTokenVersion())
                    .put("iat", now.getEpochSecond())
                    .put("exp", now.plus(lifetime).getEpochSecond())
                    .toString();
            String payload = encoder.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signed = header + "." + payload;
            return signed + "." + encoder.encodeToString(sign(signed));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create authentication token.", ex);
        }
    }

    public Optional<Claims> parse(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) return Optional.empty();
            String expectedHeader = encoder.encodeToString(HEADER.getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(expectedHeader.getBytes(StandardCharsets.US_ASCII),
                    parts[0].getBytes(StandardCharsets.US_ASCII))) return Optional.empty();
            byte[] expectedSignature = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expectedSignature, decoder.decode(parts[2]))) return Optional.empty();
            JsonNode payload = mapper.readTree(decoder.decode(parts[1]));
            long expiresAt = payload.path("exp").asLong(0);
            long userId = payload.path("sub").asLong(0);
            if (expiresAt <= Instant.now().getEpochSecond() || userId <= 0) return Optional.empty();
            return Optional.of(new Claims(userId, payload.path("role").asText(""),
                    payload.path("ver").asInt(-1)));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private byte[] sign(String input) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(input.getBytes(StandardCharsets.US_ASCII));
    }

    public record Claims(long userId, String role, int tokenVersion) { }
}