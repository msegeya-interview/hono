/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.adapter.rest;

import org.eclipse.hono.adapter.http.HttpProtocolAdapterProperties;
import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.service.AbstractAdapterConfig;
import org.eclipse.hono.service.auth.device.UsernamePasswordAuthProvider;
import org.springframework.beans.factory.config.ObjectFactoryCreatingFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Spring Boot configuration for the REST protocol adapter.
 */
@Configuration
public class Config extends AbstractAdapterConfig {

    private static final String CONTAINER_ID_HONO_REST_ADAPTER = "Hono REST Adapter";
    private static final String BEAN_NAME_VERTX_BASED_REST_PROTOCOL_ADAPTER = "vertxBasedRestProtocolAdapter";

    /**
     * Creates a new REST protocol adapter instance.
     * 
     * @return The new instance.
     */
    @Bean(name = BEAN_NAME_VERTX_BASED_REST_PROTOCOL_ADAPTER)
    @Scope("prototype")
    public VertxBasedRestProtocolAdapter vertxBasedRestProtocolAdapter(){
        return new VertxBasedRestProtocolAdapter();
    }

    @Override
    protected void customizeMessagingClientConfigProperties(final ClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_REST_ADAPTER);
        }
    }

    @Override
    protected void customizeRegistrationServiceClientConfigProperties(final ClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_REST_ADAPTER);
        }
    }

    @Override
    protected void customizeCredentialsServiceClientConfigProperties(final ClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_REST_ADAPTER);
        }
    }

    /**
     * Exposes properties for configuring the application properties as a Spring bean.
     *
     * @return The application configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.app")
    public ApplicationConfigProperties applicationConfigProperties(){
        return new ApplicationConfigProperties();
    }

    /**
     * Exposes the REST adapter's configuration properties as a Spring bean.
     *
     * @return The configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.http")
    public HttpProtocolAdapterProperties adapterProperties() {
        return new HttpProtocolAdapterProperties();
    }

    /**
     * Exposes an authentication provider for verifying username/password credentials provided by a device
     * as a Spring bean.
     * 
     * @return The authentication provider.
     */
    @Bean
    @Scope("prototype")
    public UsernamePasswordAuthProvider usernamePasswordAuthProvider() {
        UsernamePasswordAuthProvider provider = new UsernamePasswordAuthProvider(vertx(), adapterProperties());
        provider.setCredentialsServiceClient(credentialsServiceClient());
        return provider;
    }

    /**
     * Exposes a factory for creating REST adapter instances.
     *
     * @return The factory bean.
     */
    @Bean
    public ObjectFactoryCreatingFactoryBean serviceFactory() {
        ObjectFactoryCreatingFactoryBean factory = new ObjectFactoryCreatingFactoryBean();
        factory.setTargetBeanName(BEAN_NAME_VERTX_BASED_REST_PROTOCOL_ADAPTER);
        return factory;
    }
}
