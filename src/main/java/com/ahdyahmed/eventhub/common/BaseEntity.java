package com.ahdyahmed.eventhub.common;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Shared identity + audit columns for every entity in the domain.
 *
 * <p>Uses {@code @SuperBuilder}, not plain {@code @Builder}. Lombok's
 * {@code @Builder} only builds fields declared directly on the annotated
 * class — it silently ignores anything inherited, which here would mean
 * every entity's builder is missing {@code id}, {@code createdAt}, and
 * {@code updatedAt}. {@code @SuperBuilder} walks the class hierarchy and
 * needs to be applied consistently on both this class and every subclass.</p>
 *
 * <p>The explicit protected no-args constructor matters too: once
 * {@code @SuperBuilder} generates its own (builder-accepting) constructor,
 * Java stops providing an implicit no-arg one — and subclasses' own
 * {@code @NoArgsConstructor} (required by JPA) needs a no-arg {@code super()}
 * to call.</p>
 *
 * <p>equals/hashCode deliberately do NOT use Lombok's field-based defaults.
 * Using every field (including lazy associations) breaks under Hibernate
 * proxies and causes stack overflows on bidirectional relationships. The
 * pattern here — identity based purely on a non-null id, constant hashCode —
 * is the standard safe approach for JPA entities (see Vlad Mihalcea's write-up
 * on the subject if you want the long version).</p>
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BaseEntity that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
