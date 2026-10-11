package com.interviewos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuthSecurityIT extends AbstractIT {

    @Test void registerThenLoginReturnsToken() throws Exception {
        register("login@test.dev");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"LOGIN@test.dev\",\"password\":\"correct-horse-battery\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test void duplicateEmailIsRejected() throws Exception {
        register("dup@test.dev");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dup@test.dev\",\"password\":\"correct-horse-battery\",\"displayName\":\"x\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test void weakPasswordFailsValidationWithFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"weak@test.dev\",\"password\":\"short\",\"displayName\":\"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test void wrongPasswordGivesGenericUnauthorized() throws Exception {
        register("wrong@test.dev");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"wrong@test.dev\",\"password\":\"not-the-password\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test void protectedEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/interviews")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/profile").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
    }

    @Test void logoutRevokesTheToken() throws Exception {
        String token = newUser();
        doGet("/api/profile", token).andExpect(status().isOk());
        doPost("/api/auth/logout", token, null).andExpect(status().isNoContent());
        doGet("/api/profile", token).andExpect(status().isUnauthorized());
    }

    @Test void errorsNeverLeakStackTraces() throws Exception {
        String token = newUser();
        String body = doGet("/api/interviews/999999", token).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("Exception").doesNotContain("at com.");
    }
}
