package com.portfolio.dbrefactor;

import jakarta.persistence.*;

@Entity
@Table(name = "owned_properties")
public class OwnedProperty {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id")
    private Partner partner;

    private String propertyName;

    protected OwnedProperty() {}

    public OwnedProperty(Partner partner, String propertyName) {
        this.partner = partner;
        this.propertyName = propertyName;
    }

    public Long getId() { return id; }
    public String getPropertyName() { return propertyName; }
}
