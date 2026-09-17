package com.portfolio.dbrefactor;

import jakarta.persistence.*;

import java.util.List;

/**
 * Partner has its own nested collection — this is what makes the full
 * object graph "deep." Loading one Reservation eagerly pulls Partner,
 * which eagerly pulls ITS OwnedProperties, and so on — the exact
 * multi-level, circular-adjacent association chain described in the
 * real-world problem this module is based on.
 */
@Entity
@Table(name = "partners")
public class Partner {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private String name;

    private String integrationConfig; // large JSON/config blob, expensive to fetch unnecessarily

    @OneToMany(mappedBy = "partner", fetch = FetchType.EAGER)
    private List<OwnedProperty> ownedProperties;

    protected Partner() {}

    public Partner(String name, String integrationConfig) {
        this.name = name;
        this.integrationConfig = integrationConfig;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getIntegrationConfig() { return integrationConfig; }
    public List<OwnedProperty> getOwnedProperties() { return ownedProperties; }
}
