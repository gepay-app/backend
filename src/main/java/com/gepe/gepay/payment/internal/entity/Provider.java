package com.gepe.gepay.payment.internal.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@NoArgsConstructor
@Getter
@Entity
@Table(name = "providers", schema = "payment")
public class Provider {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Size(max = 30)
    @NotNull
    @Column(name = "code", nullable = false, length = 30,unique = true)
    private String code;

    @Size(max = 100)
    @NotNull
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "supports_payin", nullable = false)
    private Boolean supportsPayin;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "supports_payout", nullable = false)
    private Boolean supportsPayout;

    @NotNull
    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


    public static Provider create(
            String code,
            String name,
            Boolean supportsPayin,
            Boolean supportsPayout,
            Boolean isActive
    ){
        Provider p = new Provider();
        p.code = code;
        p.name = name;
        p.supportsPayin = supportsPayin;
        p.supportsPayout = supportsPayout;
        p.isActive = isActive;
        p.createdAt = Instant.now();
        p.updatedAt = p.createdAt;
        return p;
    }

}