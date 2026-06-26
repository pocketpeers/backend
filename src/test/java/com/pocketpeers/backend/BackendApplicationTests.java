package com.pocketpeers.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class BackendApplicationTests {

    @Test
    void applicationClassIsSpringBootApplication() {
        assertThat(BackendApplication.class).hasAnnotation(SpringBootApplication.class);
    }

}
