package com.chaptime.backend.security;

import com.chaptime.backend.model.User;
import com.chaptime.backend.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthFilter.class);

    /**
     * Constructs a JwtAuthFilter instance with the specified UserRepository.
     *
     * @param userRepository the repository used to retrieve user information
     *                       based on the Firebase UID extracted from the JWT.
     */
    public JwtAuthFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Processes incoming HTTP requests to determine if they contain a valid Firebase
     * ID token in the "Authorization" header and authenticates the user if the token
     * is valid.
     *
     * If the token is successfully verified, the user's details are fetched from
     * the database via the Firebase UID, and the security context is updated to
     * mark the user as authenticated.
     *
     * @param request the HTTP servlet request passed into the filter
     * @param response the HTTP servlet response passed into the filter
     * @param filterChain the filter chain used to pass the request/response to the next filter
     * @throws ServletException if an error occurs during the request processing
     * @throws IOException if an I/O error occurs during the request processing
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (request.getServletPath().equals("/api/v1/users/signup")) {
            filterChain.doFilter(request, response);
            return;
        }
        logger.info("--- AUTH FILTER --- Request path: {}", request.getServletPath());

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String idToken = authHeader.substring(7);
            try {
                FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
                String uidFromToken = decodedToken.getUid();
                logger.info("--- TOKEN VERIFIED --- UID from Token: {}", uidFromToken);

                Optional<User> userOptional = userRepository.findByFirebaseUid(uidFromToken);

                if (userOptional.isPresent()) {
                    User user = userOptional.get();
                    logger.info("--- DATABASE MATCH --- Found user '{}' in DB for UID {}", user.getUsername(), uidFromToken);

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            user, null, user.getAuthorities() // Holt sich die Rollen direkt vom User-Objekt
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    logger.error("--- DATABASE MISMATCH --- No user found in DB for UID: {}", uidFromToken);
                }

            } catch (Exception e) {
                logger.error("Token verification failed.", e);
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}