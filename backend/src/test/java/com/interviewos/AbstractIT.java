package com.interviewos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIT {
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper om;

    protected String register(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\",\"displayName\":\"Test User\"}";
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return om.readTree(res).get("token").asText();
    }

    protected String newUser() throws Exception { return register(UUID.randomUUID().toString().substring(0, 8) + "@test.dev"); }

    protected ResultActions doPost(String url, String token, Object body) throws Exception {
        var b = post(url).contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token);
        return mvc.perform(body == null ? b : b.content(om.writeValueAsString(body)));
    }

    protected ResultActions doGet(String url, String token) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + token));
    }

    protected JsonNode json(ResultActions r) throws Exception { return om.readTree(r.andReturn().getResponse().getContentAsString()); }
}
