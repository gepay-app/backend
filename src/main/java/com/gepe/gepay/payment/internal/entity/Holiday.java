package com.gepe.gepay.payment.internal.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.time.LocalDate;

@NoArgsConstructor
@Getter

@Entity
@Table(name = "holidays", schema = "payment")
public class Holiday {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Size(max = 120)
    @NotNull
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Size(max = 2)
    @NotNull
    @ColumnDefault("'ID'")
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Holiday create(LocalDate date, String name, String countryCode) {
        Holiday h = new Holiday();
        h.date = date;
        h.name = name;
        h.countryCode = countryCode;
        h.createdAt = Instant.now();
        return h;
    }

}
