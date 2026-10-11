package com.interviewos.security;

import com.interviewos.domain.Role;

public record AuthUser(Long id, String email, Role role) {}
