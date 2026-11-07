package com.shopsmart.realtime;

import com.shopsmart.order.OrderRepository;
import com.shopsmart.security.AuthUser;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Authenticates STOMP sessions with the access token sent in the CONNECT frame's
 * {@code Authorization} header and authorises every SUBSCRIBE. Clients may not SEND.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);
    private static final Pattern ORDER_TOPIC = Pattern.compile("^/topic/orders/(\\d+)$");

    private final JwtDecoder jwtDecoder;
    private final Converter<Jwt, AbstractAuthenticationToken> authenticationConverter;
    private final OrderRepository orderRepository;

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder,
                                       Converter<Jwt, AbstractAuthenticationToken> authenticationConverter,
                                       OrderRepository orderRepository) {
        this.jwtDecoder = jwtDecoder;
        this.authenticationConverter = authenticationConverter;
        this.orderRepository = orderRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT, STOMP -> accessor.setUser(authenticate(accessor));
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> throw new MessageDeliveryException("Clients cannot publish messages");
            default -> {
                // DISCONNECT, UNSUBSCRIBE, ACK... need no checks.
            }
        }
        return message;
    }

    private JwtAuthenticationToken authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new MessageDeliveryException("Missing bearer token");
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring(7).trim());
            return (JwtAuthenticationToken) authenticationConverter.convert(jwt);
        } catch (JwtException e) {
            throw new MessageDeliveryException("Invalid or expired token");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        AuthUser user = Optional.ofNullable(accessor.getUser())
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(principal -> AuthUser.fromJwt(((JwtAuthenticationToken) principal).getToken()))
                .orElseThrow(() -> new MessageDeliveryException("Not authenticated"));
        String destination = accessor.getDestination() == null ? "" : accessor.getDestination();
        if (destination.startsWith("/user/queue/")) {
            return;
        }
        if (destination.startsWith("/topic/admin/")) {
            if (!user.isAdmin()) {
                deny(user, destination);
            }
            return;
        }
        Matcher order = ORDER_TOPIC.matcher(destination);
        if (order.matches()) {
            Long orderId = Long.valueOf(order.group(1));
            boolean owner = orderRepository.findOwnerId(orderId).map(user.id()::equals).orElse(false);
            if (!owner && !user.isAdmin()) {
                deny(user, destination);
            }
            return;
        }
        deny(user, destination);
    }

    private static void deny(AuthUser user, String destination) {
        log.warn("User {} denied subscription to {}", user.id(), destination);
        throw new MessageDeliveryException("Access denied to " + destination);
    }
}
