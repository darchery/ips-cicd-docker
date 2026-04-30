package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.example.demo.model.User;
import com.example.demo.model.UserRepository;
import com.example.demo.model.UserRepositoryDetailsService;

@Configuration
@EnableWebSecurity
public class WebSecurityC {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserRepositoryDetailsService userDetailsService,
                                                            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    
	@Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/console/**", "/css/**", "/js/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            )
            .csrf(csrf -> csrf.disable()) // for H2 console
            .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }
    
    @Bean
    public CommandLineRunner createDefaultUser(UserRepository userRepository, PasswordEncoder passwordEncoder) {
                return args -> {
                    String email = "test@test.com";
                    if (userRepository.findByEmail(email) == null) {
                        User user = new User();
                        user.setEmail(email);
                        // password "test" encrypted
                        user.setPassword(passwordEncoder.encode("test"));
                        user.setFirstName("Cristian");
                        user.setLastName("Martin");
                        userRepository.saveAndFlush(user);
                    }
                };
            }

	/*
	 * @Bean
	 * 
	 * @Override public UserDetailsService userDetailsService() { UserBuilder users
	 * = User.withDefaultPasswordEncoder(); UserDetails user =
	 * users.username("user").password("user").roles("USER") .build();
	 * 
	 * return new InMemoryUserDetailsManager(user); }
	 */

}