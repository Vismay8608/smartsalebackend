package com.eauction.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "client_branch_levels",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_client_level_code",  columnNames = {"clientid", "level_code"}),
                @UniqueConstraint(name = "uq_client_level_order", columnNames = {"clientid", "level_order"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientBranchLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "level_id")
    private Integer levelId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clientid", nullable = false)
    private ClientRegister client;

    @Column(name = "level_code", nullable = false, length = 20)
    private String levelCode;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "level_order", nullable = false)
    private Short levelOrder;

    @Column(name = "is_admin_branch", nullable = false)
    @Builder.Default
    private Boolean isAdminBranch = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_on", nullable = false, updatable = false)
    private OffsetDateTime createdOn;

    @Column(name = "created_by")
    private Integer createdBy;
}
