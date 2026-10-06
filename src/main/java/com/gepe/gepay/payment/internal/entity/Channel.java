package com.gepe.gepay.payment.internal.entity;

import com.gepe.gepay.payment.api.enums.ChannelDirection;
import com.gepe.gepay.payment.api.enums.ChannelType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@Getter
@Entity
@Table(name = "channels", schema = "payment")
public class Channel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Size(max = 40)
    @NotNull
    @Column(name = "code", nullable = false, length = 40,unique = true)
    private String code;

    @Size(max = 120)
    @NotNull
    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "type", nullable = false, length = 20)
    private ChannelType type;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "direction", nullable = false, length = 10)
    private ChannelDirection direction;

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

    /**
     * Channel = tabel referensi (tidak tumbuh seiring waktu), jadi OneToMany aman.
     * Tetap LAZY supaya tidak memicu query saat channel dibaca.
     */
    @OneToMany(mappedBy = "channel", fetch = FetchType.LAZY)
    private List<ChannelRoute> channelRoutes = new ArrayList<>();

    public static Channel create(
            String code,
            String displayName,
            ChannelType type,
            ChannelDirection direction
    ){
        Channel c = new Channel();
        c.code = code;
        c.displayName = displayName;
        c.type = type;
        c.direction = direction;
        c.isActive = true;
        c.createdAt = Instant.now();
        c.updatedAt = c.createdAt;
        return c;
    }

}