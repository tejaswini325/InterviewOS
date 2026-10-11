package com.interviewos.dto;

import com.interviewos.domain.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(@NotBlank @Email @Size(max = 255) String email,
                                  @NotBlank @Size(min = 10, max = 72, message = "must be 10-72 characters") String password,
                                  @NotBlank @Size(max = 100) String displayName) {}
    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
    public record UserView(Long id, String email, String displayName, Role role) {}
    public record AuthResponse(String token, Instant expiresAt, UserView user) {}

    public record ProfileView(TargetRole targetRole, ExperienceLevel experienceLevel, List<Topic> skillPreferences, String displayName, String email, int weeklyGoal) {}
    public record ProfileUpdate(@NotNull TargetRole targetRole, @NotNull ExperienceLevel experienceLevel,
                                @NotNull @Size(max = 9) List<Topic> skillPreferences, @NotBlank @Size(max = 100) String displayName,
                                @Min(1) @Max(21) Integer weeklyGoal) {}
}
