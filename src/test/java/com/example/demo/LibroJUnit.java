package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.example.demo.model.Libro;
import com.example.demo.model.Persona;

/**
 * Test basicos sobre la clase Libro
 * @author Cristian
 *
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LibroJUnit {
	
	public static Libro l;
	public static String nombre="Introducción a la Ingenieria del software";
	public static long isbn=111;
	public static Double precio=13.5;
	
	public static String nombrePersona="Introducción";
	public static String apellidoPersona="Software";
	public static String dniPersona="11111X";
	
	@BeforeAll
	public static void create_libro() {
		l=new Libro();
		l.setNombre(nombre);
		l.setIsbn13(isbn);
		l.setPrecio(precio);
		Persona persona=new Persona();
		persona.setNombre(nombrePersona);
		persona.setDni(dniPersona);
		persona.setApellido(apellidoPersona);
		l.setAutor(persona);
		l.setEditor(persona);
	}
	
	@Test
	public void libro() {
		assertEquals(nombre,l.getNombre(), "Comprobando el nombre del libro");
		assertEquals(isbn,l.getIsbn13(), "Comprobando el isbn del libro");
		assertEquals( precio, l.getPrecio(), "Comprobando el precio del libro");
		
	}
	
	@Test
	public void autor() {
		assertEquals(nombrePersona,l.getAutor().getNombre(), "Comprobando el nombre del autor");
		assertEquals(apellidoPersona,l.getAutor().getApellido(), "Comprobando el apellido del autor");
		assertEquals(dniPersona, l.getAutor().getDni(), "Comprobando el dni del autor" );
		
	}
	
	@Test
	public void editor() {
		assertEquals(nombrePersona,l.getEditor().getNombre(), "Comprobando el nombre del editor");
		assertEquals(apellidoPersona,l.getEditor().getApellido(), "Comprobando el apellido del editor");
		assertEquals(dniPersona, l.getEditor().getDni(), "Comprobando el dni del editor");
		
	}
}
