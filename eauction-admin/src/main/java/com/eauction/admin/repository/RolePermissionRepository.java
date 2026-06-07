package com.eauction.admin.repository;

import com.eauction.admin.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Integer> {

    List<RolePermission> findByRoleRoleId(Integer roleId);

    @Query("SELECT rp.permission.permissionCode FROM RolePermission rp WHERE rp.role.roleId IN :roleIds AND rp.role.isActive = true")
    Set<String> findPermissionCodesByRoleIds(List<Integer> roleIds);

    void deleteByRoleRoleIdAndPermissionPermissionId(Integer roleId, Integer permissionId);
}
