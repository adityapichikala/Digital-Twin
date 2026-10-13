package com.twindigital.auth.service;

import com.twindigital.auth.repository.RevokedTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * Periodically cleans up expired JWT entries from the revocation table
 * to keep it from growing unbounded.
 */
@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
public class TokenCleanupService {

    private final RevokedTokenRepository revokedTokenRepository;

    // Run every hour
    @Scheduled(fixedRateString = "PT1H")
    public void purgeExpiredTokens() {
        int deleted = revokedTokenRepository.deleteExpiredBefore(OffsetDateTime.now());
        log.info("Purged {} expired revoked token entries", deleted);
    }
}
