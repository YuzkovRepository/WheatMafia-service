package com.example.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${spring.messaging.stomp.broker.relay.host}")
    private String relayHost;

    @Value("${spring.messaging.stomp.broker.relay.port}")
    private int relayPort;

    @Value("${spring.messaging.stomp.broker.relay.login}")
    private String relayLogin;

    @Value("${spring.messaging.stomp.broker.relay.password}")
    private String relayPassword;

    @Value("${spring.messaging.stomp.broker.relay.virtual-host}")
    private String relayVirtualHost;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");

        registry.enableStompBrokerRelay("/topic")
                .setRelayHost(relayHost)
                .setRelayPort(relayPort)
                .setClientLogin(relayLogin)
                .setClientPasscode(relayPassword)
                .setSystemLogin(relayLogin)
                .setSystemPasscode(relayPassword)
                .setVirtualHost(relayVirtualHost);
    }
}