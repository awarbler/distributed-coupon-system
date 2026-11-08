package coupons.repository;

import coupons.rewards.Reward;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Explicit non-receipt movement, never a new receipt earning; signed values may be independently unknown. */
public record AccountAdjustment(UUID eventId, LocalDate date, String provider, BigDecimal unitChange,
        BigDecimal usdChange, String reason, String recordedBy, Instant recordedAt, LocalDate expirationDate, LocalTime time, Map<String,String> details) implements Serializable {
    public AccountAdjustment(UUID id,LocalDate date,String provider,BigDecimal units,BigDecimal usd,String reason,String actor,Instant at,LocalDate expiration,LocalTime time){this(id,date,provider,units,usd,reason,actor,at,expiration,time,Map.of());}
    public static AccountAdjustment currentMember(AccountAdjustment original,List<AccountAdjustment> history){
        if(!original.reason().startsWith("Member reward:"))return original;
        var dollars=original.usdChange();var units=original.unitChange();var details=new LinkedHashMap<>(original.details());
        for(var correction:history.stream().filter(a->a.reason().equals("Member reward correction: "+original.eventId())).sorted(Comparator.comparing(AccountAdjustment::recordedAt).thenComparing(AccountAdjustment::eventId)).toList()){
            if(correction.usdChange()!=null)dollars=dollars==null?correction.usdChange():dollars.add(correction.usdChange());
            if(correction.unitChange()!=null)units=units==null?correction.unitChange():units.add(correction.unitChange());details.putAll(correction.details());
        }
        String expiry=details.get("expirationDate"),time=details.get("time");
        return new AccountAdjustment(original.eventId(),details.containsKey("date")?LocalDate.parse(details.get("date")):original.date(),details.getOrDefault("provider",original.provider()),units,dollars,details.containsKey("description")?"Member reward: "+details.get("description"):original.reason(),original.recordedBy(),original.recordedAt(),expiry==null?original.expirationDate():expiry.isBlank()?null:LocalDate.parse(expiry),time==null?original.time():time.isBlank()?null:LocalTime.parse(time),details);
    }
    public AccountAdjustment(UUID eventId, LocalDate date, String provider, BigDecimal unitChange, BigDecimal usdChange, String reason, String recordedBy, Instant recordedAt, LocalDate expirationDate) {
        this(eventId,date,provider,unitChange,usdChange,reason,recordedBy,recordedAt,expirationDate,null);
    }
    public AccountAdjustment(UUID eventId, LocalDate date, String provider, BigDecimal unitChange, BigDecimal usdChange, String reason, String recordedBy, Instant recordedAt) {
        this(eventId,date,provider,unitChange,usdChange,reason,recordedBy,recordedAt,null);
    }
    public boolean unavailable(){return reason.startsWith("Reward unavailable: ");}
    public UUID unavailableSource(){if(!unavailable())return null;String[] parts=reason.split(" \\| ",3);return parts.length>1&&!parts[1].equals("ACCOUNT")?UUID.fromString(parts[1]):null;}
    public AccountAdjustment {
        details=details==null?Map.of():Map.copyOf(details);
        if(expirationDate!=null && expirationDate.isBefore(date))throw new IllegalArgumentException("Expiration must be on or after the reward date.");
        Objects.requireNonNull(eventId); Objects.requireNonNull(date); Objects.requireNonNull(provider);
        Objects.requireNonNull(reason); Objects.requireNonNull(recordedBy); Objects.requireNonNull(recordedAt);
        if (provider.isBlank() || reason.isBlank() || recordedBy.isBlank() || unitChange == null && usdChange == null)
            throw new IllegalArgumentException("An adjustment needs an account, amount, reason, and entrant.");
    }
}
