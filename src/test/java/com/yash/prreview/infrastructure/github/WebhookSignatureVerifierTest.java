package com.yash.prreview.infrastructure.github;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {

    private WebhookSignatureVerifier verifier;
    private static final String TEST_SECRET = "test-secret-key";

    @BeforeEach
    void setUp() {
        verifier = new WebhookSignatureVerifier();
        ReflectionTestUtils.setField(verifier, "webhookSecret", TEST_SECRET);
    }

    @Test
    void verifyValidSignature() throws Exception {
        String payload = "{\"action\":\"opened\"}";
        String signature = computeHmacSha256(payload, TEST_SECRET);
        assertThat(verifier.verify(payload, "sha256=" + signature)).isTrue();
    }

    @Test
    void rejectMissingSignatureHeader() {
        assertThat(verifier.verify("payload", null)).isFalse();
    }

    @Test
    void rejectMalformedSignatureHeader() {
        assertThat(verifier.verify("payload", "md5=invalidprefix")).isFalse();
    }

    @Test
    void rejectTamperedPayload() {
        String originalPayload = "{\"action\":\"opened\"}";
        String tamperedPayload = "{\"action\":\"opened\",\"extra\":\"injected\"}";
        String fakeSignature = "sha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        assertThat(verifier.verify(tamperedPayload, fakeSignature)).isFalse();
    }

    @Test
    void rejectWrongSignature() throws Exception {
        String payload = "{\"action\":\"opened\"}";
        String wrongSecret = "wrong-secret";
        String wrongSignature = computeHmacSha256(payload, wrongSecret);
        assertThat(verifier.verify(payload, "sha256=" + wrongSignature)).isFalse();
    }

    private String computeHmacSha256(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
