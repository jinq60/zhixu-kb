package com.zhixu.kb.common.utils;

import com.zhixu.kb.system.entity.SysUser;
import com.zhixu.kb.system.model.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setLoginUser(LoginUser user) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private LoginUser buildUser(Long id, String username, List<String> roles) {
        SysUser sysUser = new SysUser();
        sysUser.setId(id);
        sysUser.setUsername(username);
        sysUser.setStatus(1);
        return new LoginUser(sysUser, roles);
    }

    @Test
    void getLoginUser_noAuthentication_shouldReturnNull() {
        assertNull(SecurityUtils.getLoginUser());
        assertNull(SecurityUtils.getUserId());
        assertNull(SecurityUtils.getUsername());
        assertTrue(SecurityUtils.getRoles().isEmpty());
        assertFalse(SecurityUtils.isAdmin());
    }

    @Test
    void getUserId_withAuthentication_shouldReturnId() {
        setLoginUser(buildUser(42L, "alice", Collections.singletonList("USER")));

        assertEquals(42L, SecurityUtils.getUserId());
        assertEquals("42", SecurityUtils.currentUserId());
        assertEquals("alice", SecurityUtils.getUsername());
    }

    @Test
    void isAdmin_withAdminRole_shouldReturnTrue() {
        setLoginUser(buildUser(1L, "admin", Arrays.asList("ADMIN", "USER")));

        assertTrue(SecurityUtils.isAdmin());
        assertEquals(2, SecurityUtils.getRoles().size());
    }

    @Test
    void isAdmin_withUserRole_shouldReturnFalse() {
        setLoginUser(buildUser(2L, "bob", Collections.singletonList("USER")));

        assertFalse(SecurityUtils.isAdmin());
    }
}
