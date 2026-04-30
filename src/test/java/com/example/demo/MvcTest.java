package com.example.demo;

import static org.hamcrest.CoreMatchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;


// Tests sobre el comportamiento de la aplicación al acceder a diferentes urls
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ContextConfiguration
@WebAppConfiguration
public class MvcTest {

	private static final String SPRING_HOME_PAGE_TITLE = "Spring demo";

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc;

	@BeforeAll
	public  void setup() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	
	// comprobar que accediendo directamente a la url libros con un usuario conectado, redirige al formulario de libros 
	// correctamente, ademas de comprobar que tiene el string libros en el formulario
	@Test
	@WithMockUser
	public void librosTest2() throws Exception {
		mockMvc.perform(get("/libros")).andExpect(status().isOk())
		.andExpect(content().string(containsString("libros")));
	}

}
