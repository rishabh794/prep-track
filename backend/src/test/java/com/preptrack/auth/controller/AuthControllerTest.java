package com.preptrack.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.auth.dto.LoginRequest;
import com.preptrack.auth.dto.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should successfully register a new user and return JWT token")
    void registerSuccess() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("alex@example.com")
                .password("password123")
                .displayName("Alex Rivers")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.email", is("alex@example.com")))
                .andExpect(jsonPath("$.displayName", is("Alex Rivers")))
                .andExpect(jsonPath("$.onboardingComplete", is(false)));
    }

    @Test
    @DisplayName("Should reject registration with already registered email")
    void registerDuplicateEmailFails() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("dup@example.com")
                .password("password123")
                .displayName("Dup User")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already registered")));
    }

    @Test
    @DisplayName("Should fail validation on invalid registration payload")
    void registerValidationErrors() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("invalid-email")
                .password("123") // too short
                .displayName("")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.displayName").exists());
    }

    @Test
    @DisplayName("Should authenticate user and return token on valid login")
    void loginSuccess() throws Exception {
        RegisterRequest registerReq = RegisterRequest.builder()
                .email("loginuser@example.com")
                .password("secretPass456")
                .displayName("Login User")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest loginReq = LoginRequest.builder()
                .email("loginuser@example.com")
                .password("secretPass456")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.email", is("loginuser@example.com")));
    }

    @Test
    @DisplayName("Should reject login with wrong password")
    void loginWrongPasswordFails() throws Exception {
        RegisterRequest registerReq = RegisterRequest.builder()
                .email("wrongpw@example.com")
                .password("correctPassword")
                .displayName("PW Test")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest loginReq = LoginRequest.builder()
                .email("wrongpw@example.com")
                .password("incorrectPassword")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return user profile with GET /api/auth/me when authenticated")
    void getMeSuccess() throws Exception {
        RegisterRequest registerReq = RegisterRequest.builder()
                .email("me@example.com")
                .password("password123")
                .displayName("Me User")
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseJson).get("token").asText();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("me@example.com")))
                .andExpect(jsonPath("$.displayName", is("Me User")))
                .andExpect(jsonPath("$.settings.dailyTimeBudgetMin", is(90)))
                .andExpect(jsonPath("$.settings.onboardingComplete", is(false)));
    }

    @Test
    @DisplayName("Should reject GET /api/auth/me without authentication")
    void getMeUnauthenticatedFails() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
