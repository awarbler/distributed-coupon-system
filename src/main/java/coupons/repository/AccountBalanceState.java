package coupons.repository;

import java.io.Serializable;
import java.util.List;

/** Append-only opening observations and external movement sources. Receipt movements remain revision-derived. */
public record AccountBalanceState(List<AccountOpening> openings, List<AccountAdjustment> adjustments, List<ProviderRedemption> redemptionHistory) implements Serializable {
    public AccountBalanceState(List<AccountOpening> openings,List<AccountAdjustment> adjustments) { this(openings,adjustments,List.of()); }
    public AccountBalanceState { redemptionHistory=redemptionHistory==null?List.of():List.copyOf(redemptionHistory); openings = List.copyOf(openings); adjustments = List.copyOf(adjustments); }
    public static AccountBalanceState empty() { return new AccountBalanceState(List.of(), List.of()); }
}
