package com.eauction.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "master_permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permissionid")
    private Integer permissionId;

    @Column(name = "module", nullable = false, length = 50)
    private String module;

    @Column(name = "resource", nullable = false, length = 50)
    private String resource;

    @Column(name = "action", nullable = false, length = 30)
    private String action;

    @Column(name = "permission_code", nullable = false, unique = true, length = 120)
    private String permissionCode;

    @Column(name = "label", nullable = false, length = 150)
    private String label;

    @Column(name = "description")
    private String description;

    @Column(name = "applicable_to", nullable = false, length = 10)
    @Builder.Default
    private String applicableTo = "ALL";

    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private Boolean isSystem = true;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Short displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
