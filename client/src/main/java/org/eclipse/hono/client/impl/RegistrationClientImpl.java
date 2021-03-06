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

package org.eclipse.hono.client.impl;

import static org.eclipse.hono.util.RegistrationConstants.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;

/**
 * A Vertx-Proton based client for Hono's Registration API.
 *
 */
public final class RegistrationClientImpl extends AbstractRequestResponseClient<RegistrationResult> implements RegistrationClient {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationClientImpl.class);

    private RegistrationClientImpl(final Context context, final String tenantId) {

        super(context, tenantId);
    }

    /**
     * Gets the AMQP <em>target</em> address to use for sending requests to Hono's Device Registration API endpoint.
     * 
     * @param tenantId The tenant to upload data for.
     * @return The target address.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public static String getTargetAddress(final String tenantId) {
        return String.format("%s/%s", RegistrationConstants.REGISTRATION_ENDPOINT, Objects.requireNonNull(tenantId));
    }

    @Override
    protected String getName() {

        return RegistrationConstants.REGISTRATION_ENDPOINT;
    }

    @Override
    protected String createMessageId() {

        return String.format("reg-client-%s", UUID.randomUUID());
    }

    @Override
    protected RegistrationResult getResult(final int status, final String payload) {

        return RegistrationResult.from(status, payload);
    }

    /**
     * Creates a new registration client for a tenant.
     * 
     * @param context The vert.x context to run all interactions with the server on.
     * @param con The AMQP connection to the server.
     * @param tenantId The tenant to consumer events for.
     * @param receiverPrefetchCredits Number of credits, given initially from receiver to sender.
     * @param waitForInitialCredits Milliseconds to wait after link creation if there are no credits.*
     * @param senderCloseHook A handler to invoke if the peer closes the sender link unexpectedly.
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if receiverPrefetchCredits is {@code < 0}.
     * @throws IllegalArgumentException if waitForInitialCredits is {@code < 1}.
     */
    public static void create(
            final Context context,
            final ProtonConnection con,
            final String tenantId,
            final int receiverPrefetchCredits,
            final long waitForInitialCredits,
            final Handler<String> senderCloseHook,
            final Handler<String> receiverCloseHook,
            final Handler<AsyncResult<RegistrationClient>> creationHandler) {

        LOG.debug("creating new registration client for [{}]", tenantId);
        final RegistrationClientImpl client = new RegistrationClientImpl(context, tenantId);
        client.createLinks(con, receiverPrefetchCredits, waitForInitialCredits, senderCloseHook, receiverCloseHook).setHandler(s -> {
            if (s.succeeded()) {
                LOG.debug("successfully created registration client for [{}]", tenantId);
                creationHandler.handle(Future.succeededFuture(client));
            } else {
                LOG.debug("failed to create registration client for [{}]", tenantId, s.cause());
                creationHandler.handle(Future.failedFuture(s.cause()));
            }
        });
    }

    private Map<String, Object> createDeviceIdProperties(final String deviceId) {
        final Map<String, Object> properties = new HashMap<>();
        properties.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        return properties;
    }

    @Override
    public void register(final String deviceId, final JsonObject data, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        createAndSendRequest(ACTION_REGISTER, createDeviceIdProperties(deviceId), data, resultHandler);
    }

    @Override
    public void update(final String deviceId, final JsonObject data, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        createAndSendRequest(ACTION_UPDATE, createDeviceIdProperties(deviceId), data, resultHandler);
    }

    @Override
    public void deregister(final String deviceId, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        createAndSendRequest(ACTION_DEREGISTER, createDeviceIdProperties(deviceId), null, resultHandler);
    }

    @Override
    public void get(final String deviceId, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        createAndSendRequest(ACTION_GET, createDeviceIdProperties(deviceId), null, resultHandler);
    }

    @Override
    public void assertRegistration(final String deviceId, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        createAndSendRequest(ACTION_ASSERT, createDeviceIdProperties(deviceId), null, resultHandler);
    }
}
