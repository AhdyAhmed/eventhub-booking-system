package com.ahdyahmed.eventhub.venue;

import com.ahdyahmed.eventhub.common.BaseEntity;
import com.ahdyahmed.eventhub.event.Event;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A physical location that hosts events (a stadium, theater, hall, etc.).
 */
@Entity
@Table(name = "venues")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(callSuper = true)
public class Venue extends BaseEntity {

    @ToString.Include
    @Column(nullable = false, length = 150)
    private String name;

    @ToString.Include
    @Column(nullable = false, length = 100)
    private String city;

    @Column(length = 255)
    private String address;

    @Column(nullable = false)
    private Integer capacity;

    @OneToMany(mappedBy = "venue", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    private List<Event> events = new ArrayList<>();

}
