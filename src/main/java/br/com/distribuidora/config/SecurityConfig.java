package br.com.distribuidora.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.http.HttpStatus;

@Configuration
public class SecurityConfig {
    @Bean
    UserDetailsService usuarios(@Value("${estoque.senha-administrador}") String senha) {
        return new InMemoryUserDetailsManager(User.withUsername("Administrador")
            .password("{bcrypt}" + new BCryptPasswordEncoder().encode(senha)).roles("ADMIN").build());
    }
    @Bean
    SecurityFilterChain seguranca(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a
                .requestMatchers("/login.html", "/js/login.js", "/css/**", "/csrf", "/error").permitAll()
                .anyRequest().hasRole("ADMIN"))
            .formLogin(f -> f.loginPage("/login.html").loginProcessingUrl("/login")
                .defaultSuccessUrl("/", true).failureUrl("/login.html?error").permitAll())
            .logout(l -> l.logoutUrl("/logout").logoutSuccessUrl("/login.html?logout"))
            .exceptionHandling(e -> e.authenticationEntryPoint((request, response, exception) -> {
                if (new AntPathRequestMatcher("/api/**").matches(request))
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED).commence(request, response, exception);
                else new LoginUrlAuthenticationEntryPoint("/login.html").commence(request, response, exception);
            }));
        return http.build();
    }
}
