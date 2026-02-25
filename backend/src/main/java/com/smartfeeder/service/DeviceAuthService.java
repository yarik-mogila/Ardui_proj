package com.smartfeeder.service;

import com.smartfeeder.config.AppConfig;
import com.smartfeeder.security.HmacService;
import com.smartfeeder.security.SecretHashService;
import java.time.Instant;
import ru.tinkoff.kora.common.Component;

@Component
public final class DeviceAuthService {
    public interface NonceStore {
        void purgeOldNonce(long minEpochExclusive);
        boolean registerNonce(String deviceId, String nonce, long tsEpoch);
    }

    private final AppConfig appConfig;
    private final HmacService hmacService;
    private final SecretHashService secretHashService;

    public DeviceAuthService(AppConfig appConfig,
                             HmacService hmacService,
                             SecretHashService secretHashService) {
        this.appConfig = appConfig;
        this.hmacService = hmacService;
        this.secretHashService = secretHashService;
    }

    public boolean isSignatureEnabled() {
        return appConfig.deviceAuth().signatureEnabled();
    }

    public boolean isNonceEnabled() {
        return isSignatureEnabled();
    }

    public void validate(String deviceId,
                         String headerDeviceId,
                         String nonce,
                         String signature,
                         long requestTs,
                         byte[] canonicalBody,
                         String secretHash,
                         String decryptedSecret,
                         NonceStore nonceStore) {
        if (!isSignatureEnabled()) {
            return;
        }

        if (headerDeviceId == null || headerDeviceId.isBlank() || !deviceId.equals(headerDeviceId.trim())) {
            throw ApiException.unauthorized("invalid_device_header");
        }
        if (nonce == null || nonce.isBlank()) {
            throw ApiException.unauthorized("nonce_required");
        }
        if (signature == null || signature.isBlank()) {
            throw ApiException.unauthorized("signature_required");
        }

        long now = Instant.now().getEpochSecond();
        long delta = Math.abs(now - requestTs);
        if (delta > appConfig.deviceAuth().nonceWindowSec()) {
            throw ApiException.forbidden("timestamp_out_of_window");
        }

        long minTs = now - appConfig.deviceAuth().nonceWindowSec();
        nonceStore.purgeOldNonce(minTs);

        if (!nonceStore.registerNonce(deviceId, nonce, requestTs)) {
            throw ApiException.forbidden("replay_detected");
        }

        if (!secretHashService.sha256Hex(decryptedSecret).equals(secretHash)) {
            throw ApiException.forbidden("secret_integrity_check_failed");
        }

        if (!hmacService.verifyHex(canonicalBody, decryptedSecret, signature)) {
            throw ApiException.forbidden("invalid_signature");
        }
    }
}
