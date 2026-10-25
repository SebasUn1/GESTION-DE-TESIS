package com.tesistrack;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.tesistrack.integracion.ContenedorPostgres;

/** Arranca contra el PostgreSQL en Docker: sin una base local preparada, igual que en el CI. */
@SpringBootTest
@ActiveProfiles("test")
@Import(ContenedorPostgres.class)
class TesistrackApplicationTests {

	@Test
	void contextLoads() {
	}

}
