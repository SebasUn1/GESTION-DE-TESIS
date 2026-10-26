package com.tesistrack;

import org.junit.jupiter.api.Test;

import com.tesistrack.integracion.IntegracionTest;

/**
 * Arranca contra el PostgreSQL en Docker: sin una base local preparada, igual que en
 * el CI. Hereda la configuración de los tests de integración para reusar su contexto
 * y su contenedor en vez de levantar otros.
 */
class TesistrackApplicationTests extends IntegracionTest {

	@Test
	void contextLoads() {
	}

}
