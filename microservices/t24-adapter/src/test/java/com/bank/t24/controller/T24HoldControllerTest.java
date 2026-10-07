package com.bank.t24.controller;

import com.bank.t24.dto.T24HoldRequest;
import com.bank.t24.dto.T24HoldResponse;
import com.bank.t24.dto.T24ReleaseRequest;
import com.bank.t24.service.T24HoldService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(T24HoldController.class)
class T24HoldControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private T24HoldService holdService;

    @Test
    @DisplayName("POST /api/v1/t24/holds/lock returns 200 OK on successful hold")
    void testPlaceHoldEndpointSuccess() throws Exception {
        T24HoldResponse res = new T24HoldResponse(
                1L, 10L, "001181233469", new BigDecimal("1000.00"), "PHP",
                "REF-LOCK-1", "ACTIVE", new BigDecimal("9000.00"), LocalDateTime.now(), "Hold placed"
        );
        when(holdService.placeHold(any(T24HoldRequest.class))).thenReturn(res);

        T24HoldRequest req = new T24HoldRequest(10L, null, new BigDecimal("1000.00"), "PHP", "REF-LOCK-1");

        mockMvc.perform(post("/api/v1/t24/holds/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.referenceNo").value("REF-LOCK-1"))
                .andExpect(jsonPath("$.amount").value(1000.00));
    }

    @Test
    @DisplayName("POST /api/v1/t24/holds/lock returns 422 Unprocessable Entity when funds insufficient")
    void testPlaceHoldEndpointInsufficientFunds() throws Exception {
        when(holdService.placeHold(any(T24HoldRequest.class)))
                .thenThrow(new IllegalStateException("Insufficient funds"));

        T24HoldRequest req = new T24HoldRequest(10L, null, new BigDecimal("50000.00"), "PHP", "REF-LOCK-2");

        mockMvc.perform(post("/api/v1/t24/holds/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Insufficient funds"));
    }

    @Test
    @DisplayName("POST /api/v1/t24/holds/release returns 200 OK")
    void testReleaseHoldEndpointSuccess() throws Exception {
        T24HoldResponse res = new T24HoldResponse(
                1L, 10L, "001181233469", new BigDecimal("1000.00"), "PHP",
                "REF-LOCK-1", "RELEASED", new BigDecimal("10000.00"), LocalDateTime.now(), "Hold released"
        );
        when(holdService.releaseHold(any(T24ReleaseRequest.class))).thenReturn(res);

        T24ReleaseRequest req = new T24ReleaseRequest("REF-LOCK-1");

        mockMvc.perform(post("/api/v1/t24/holds/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
    }

    @Test
    @DisplayName("GET /api/v1/t24/holds/{referenceNo} returns 200 OK when exists")
    void testGetHoldEndpointFound() throws Exception {
        T24HoldResponse res = new T24HoldResponse(
                1L, 10L, "001181233469", new BigDecimal("1000.00"), "PHP",
                "REF-LOCK-1", "ACTIVE", new BigDecimal("9000.00"), LocalDateTime.now(), "Retrieved"
        );
        when(holdService.getHold("REF-LOCK-1")).thenReturn(Optional.of(res));

        mockMvc.perform(get("/api/v1/t24/holds/REF-LOCK-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceNo").value("REF-LOCK-1"));
    }

    @Test
    @DisplayName("GET /api/v1/t24/holds/{referenceNo} returns 404 when not found")
    void testGetHoldEndpointNotFound() throws Exception {
        when(holdService.getHold("REF-UNKNOWN")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/t24/holds/REF-UNKNOWN"))
                .andExpect(status().isNotFound());
    }
}
