package com.bank.auth;

import com.bank.auth.dto.AuthRequest;
import com.bank.auth.dto.AuthResponse;
import com.bank.auth.model.Customer;
import com.bank.auth.repository.CustomerRepository;
import com.bank.auth.security.JwtTokenProvider;
import com.bank.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for AuthService. DB + JWT mocked. No Spring context needed. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock private CustomerRepository customerRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private AuthService authService;
    private Customer sampleCustomer;

    @BeforeEach void setUp() {
        sampleCustomer = new Customer("jdelacruz", "$2a$10$hash", "Juan", "Dela Cruz", "juan@paypink.ph", "09171234567");
        try { var f2 = Customer.class.getDeclaredField("customerId"); f2.setAccessible(true); f2.set(sampleCustomer, 1L); } catch (Exception ignored) {}
    }

    @Test @DisplayName("authenticate: valid credentials return Bearer token")
    void authenticate_validCredentials_returnsToken() {
        when(customerRepository.findByUsername("jdelacruz")).thenReturn(Optional.of(sampleCustomer));
        when(passwordEncoder.matches("password123", sampleCustomer.getPasswordHash())).thenReturn(true);
        when(jwtTokenProvider.generateToken(eq(1L), eq("jdelacruz"), anyList())).thenReturn("mock.jwt.token");
        AuthResponse r = authService.authenticate(new AuthRequest("jdelacruz", "password123"));
        assertThat(r.getToken()).isEqualTo("mock.jwt.token");
        assertThat(r.getTokenType()).isEqualTo("Bearer");
        verify(jwtTokenProvider).generateToken(eq(1L), eq("jdelacruz"), anyList());
    }

    @Test @DisplayName("authenticate: response includes customerId and full name")
    void authenticate_returnsCustomerIdAndFullName() {
        when(customerRepository.findByUsername("jdelacruz")).thenReturn(Optional.of(sampleCustomer));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtTokenProvider.generateToken(anyLong(), anyString(), anyList())).thenReturn("t");
        AuthResponse r = authService.authenticate(new AuthRequest("jdelacruz", "p"));
        assertThat(r.getCustomerId()).isEqualTo(1L);
        assertThat(r.getFullName()).contains("Juan").contains("Dela Cruz");
    }

    @Test @DisplayName("authenticate: roles include ROLE_CUSTOMER and ROLE_RETAIL_USER")
    void authenticate_includesBothRoles() {
        when(customerRepository.findByUsername("jdelacruz")).thenReturn(Optional.of(sampleCustomer));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtTokenProvider.generateToken(anyLong(), anyString(), anyList())).thenReturn("t");
        AuthResponse r = authService.authenticate(new AuthRequest("jdelacruz", "p"));
        assertThat(r.getRoles()).containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_RETAIL_USER");
    }

    @Test @DisplayName("authenticate: unknown username throws RuntimeException")
    void authenticate_unknownUsername_throwsException() {
        when(customerRepository.findByUsername("nobody")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.authenticate(new AuthRequest("nobody", "p")))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("Invalid username or password");
    }

    @Test @DisplayName("authenticate: wrong password throws RuntimeException")
    void authenticate_wrongPassword_throwsException() {
        when(customerRepository.findByUsername("jdelacruz")).thenReturn(Optional.of(sampleCustomer));
        when(passwordEncoder.matches("wrongpass", sampleCustomer.getPasswordHash())).thenReturn(false);
        assertThatThrownBy(() -> authService.authenticate(new AuthRequest("jdelacruz", "wrongpass")))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("Invalid username or password");
    }

    @Test @DisplayName("JwtTokenProvider: generates compact 3-part JWT")
    void jwtTokenProvider_generatesCompactJwt() {
        JwtTokenProvider real = new JwtTokenProvider("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970", 86400000L);
        String token = real.generateToken(1L, "jdelacruz", List.of("ROLE_CUSTOMER"));
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test @DisplayName("JwtTokenProvider: different users produce different tokens")
    void jwtTokenProvider_differentUsers_differentTokens() {
        JwtTokenProvider p = new JwtTokenProvider("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970", 86400000L);
        String t1 = p.generateToken(1L, "userA", List.of("ROLE_CUSTOMER"));
        String t2 = p.generateToken(2L, "userB", List.of("ROLE_CUSTOMER"));
        assertThat(t1).isNotEqualTo(t2);
    }

    @Test @DisplayName("authenticate: authenticates valid user credentials")
    void authenticate_validUserCredentials() {
        Customer demo = new Customer("lviernes", "$2a$10$h", "Luis", "Viernes", "lv@paypink.ph", "09179999999");
        try { var f2 = Customer.class.getDeclaredField("customerId"); f2.setAccessible(true); f2.set(demo, 2L); } catch (Exception ignored) {}
        when(customerRepository.findByUsername("lviernes")).thenReturn(Optional.of(demo));
        when(passwordEncoder.matches("password123", demo.getPasswordHash())).thenReturn(true);
        when(jwtTokenProvider.generateToken(anyLong(), anyString(), anyList())).thenReturn("demo.token");
        AuthResponse r = authService.authenticate(new AuthRequest("lviernes", "password123"));
        assertThat(r.getToken()).isEqualTo("demo.token");
        verify(customerRepository).findByUsername("lviernes");
    }

    @Test @DisplayName("authenticate: admin credentials load ROLE_ADMIN from customer entity")
    void authenticate_admin_loadsRolesFromCustomer() {
        Customer admin = new Customer("admin", "$2a$10$hash", "PayPink", "Administrator", "admin@paypink.internal", "+630000000000", "ROLE_ADMIN,ROLE_CORE_ENGINEER");
        try { var f = Customer.class.getDeclaredField("customerId"); f.setAccessible(true); f.set(admin, 99L); } catch (Exception ignored) {}
        when(customerRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Admin@PayPink2026!", admin.getPasswordHash())).thenReturn(true);
        when(jwtTokenProvider.generateToken(eq(99L), eq("admin"), anyList())).thenReturn("admin.jwt.token");
        AuthResponse r = authService.authenticate(new AuthRequest("admin", "Admin@PayPink2026!"));
        assertThat(r.getToken()).isEqualTo("admin.jwt.token");
        assertThat(r.getCustomerId()).isEqualTo(99L);
        assertThat(r.getRoles()).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_CORE_ENGINEER");
    }
}
