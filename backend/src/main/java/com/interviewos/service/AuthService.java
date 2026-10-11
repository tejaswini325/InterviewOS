package com.interviewos.service;

import com.interviewos.domain.*;
import com.interviewos.dto.AuthDtos.*;
import com.interviewos.exception.ApiException;
import com.interviewos.repository.*;
import com.interviewos.security.JwtService;
import com.interviewos.security.TokenDenylist;
import io.jsonwebtoken.Claims;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final TokenDenylist denylist;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserRepository users, ProfileRepository profiles, PasswordEncoder encoder, JwtService jwt,
                       TokenDenylist denylist, Clock clock) {
        this.users = users; this.profiles = profiles; this.encoder = encoder; this.jwt = jwt; this.denylist = denylist; this.clock = clock;
        this.dummyHash = encoder.encode("timing-equalizer");
    }

    @Transactional
    public AuthResponse register(RegisterRequest r) {
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        User u = new User();
        u.setEmail(email); u.setPasswordHash(encoder.encode(r.password())); u.setDisplayName(r.displayName().trim());
        u.setRole(Role.USER); u.setCreatedAt(clock.instant());
        users.save(u);
        CandidateProfile p = new CandidateProfile();
        p.setUserId(u.getId()); p.setTargetRole(TargetRole.SOFTWARE_ENGINEER); p.setExperienceLevel(ExperienceLevel.FRESHER);
        p.setUpdatedAt(clock.instant());
        profiles.save(p);
        return respond(u);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest r) {
        User u = users.findByEmailIgnoreCase(r.email().trim()).orElse(null);
        boolean ok = encoder.matches(r.password(), u == null ? dummyHash : u.getPasswordHash());   // same cost either way
        if (u == null || !ok) throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        return respond(u);
    }

    public void logout(String token) { denylist.revoke(jwt.parse(token)); }

    @Transactional(readOnly = true)
    public ProfileView profile(Long userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        CandidateProfile p = profiles.findByUserId(userId).orElseThrow(() -> ApiException.notFound("Profile"));
        return view(u, p);
    }

    @Transactional
    public ProfileView updateProfile(Long userId, ProfileUpdate r) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        CandidateProfile p = profiles.findByUserId(userId).orElseThrow(() -> ApiException.notFound("Profile"));
        u.setDisplayName(r.displayName().trim());
        p.setTargetRole(r.targetRole()); p.setExperienceLevel(r.experienceLevel());
        p.setSkillPreferences(r.skillPreferences().stream().distinct().map(Enum::name).collect(Collectors.joining(",")));
        if (r.weeklyGoal() != null) p.setWeeklyGoal(r.weeklyGoal());
        p.setUpdatedAt(clock.instant());
        return view(u, p);
    }

    private ProfileView view(User u, CandidateProfile p) {
        List<Topic> prefs = p.getSkillPreferences().isBlank() ? List.of()
                : Arrays.stream(p.getSkillPreferences().split(",")).map(Topic::valueOf).toList();
        return new ProfileView(p.getTargetRole(), p.getExperienceLevel(), prefs, u.getDisplayName(), u.getEmail(), p.getWeeklyGoal());
    }

    private AuthResponse respond(User u) {
        JwtService.Issued t = jwt.issue(u.getId(), u.getEmail(), u.getRole());
        return new AuthResponse(t.token(), t.expiresAt(), new UserView(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole()));
    }
}
