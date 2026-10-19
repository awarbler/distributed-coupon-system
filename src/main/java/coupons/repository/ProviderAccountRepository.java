package coupons.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Durable boundary for provider-account observations and external movements. */
public interface ProviderAccountRepository {
    void saveOpening(AccountOpening opening);
    void saveAdjustment(AccountAdjustment adjustment);
    void saveRedemption(ProviderRedemption redemption);

    Optional<AccountOpening> findOpening(UUID observationId);
    Optional<AccountAdjustment> findAdjustment(UUID eventId);
    Optional<ProviderRedemption> findRedemption(UUID redemptionId);
    Optional<ProviderRedemption> findRedemptionRevision(UUID redemptionId, long revision);

    List<AccountOpening> findOpenings(String provider);
    List<AccountAdjustment> findAdjustments(String provider);
    List<ProviderRedemption> findRedemptionHistory(String provider);
    AccountBalanceState balanceState(String provider);

    /**
     * Appends a redemption revision while atomically checking the caller's
     * expected revision. The previous status is never overwritten in history.
     */
    ProviderRedemption updateRedemption(UUID redemptionId, long expectedRevision,
            ProviderRedemption next);
}
