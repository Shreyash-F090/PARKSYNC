package com.parksync.backend;

import com.parksync.backend.dto.ApiDtos.RegistrationInput;
import com.parksync.backend.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:parksync;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "parksync.jwt.secret=park-sync-test-key-with-more-than-32-characters",
        "parksync.admin.email=",
        "parksync.admin.password="
})
@ActiveProfiles("test")
class BackendContextTest {
    @Autowired
    private AccountService accounts;

    @Test
    void customerCanRegisterAndReceivesAWorkingToken() {
        var result = accounts.register(new RegistrationInput(
                "Test Driver", "parksync-test@example.test", "+91 90000 11111",
                "SecurePass123!", true));

        assertNotNull(result.token());
        assertEquals("CUSTOMER", result.user().role());
        assertEquals("parksync-test@example.test", result.user().email());
    }
}