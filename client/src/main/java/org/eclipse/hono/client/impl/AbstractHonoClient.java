/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
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

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * A base class for implementing Hono API clients.
 * <p>
 * Holds a sender and a receiver to an AMQP 1.0 server and provides
 * support for closing these links gracefully.
 */
public abstract class AbstractHonoClient {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractHonoClient.class);

    /**
     * The vertx-proton object used for sending messages to the server.
     */
    protected ProtonSender   sender;
    /**
     * The vertx-proton object used for receiving messages from the server.
     */
    protected ProtonReceiver receiver;
    /**
     * The vertx context to run all interactions with the server on.
     */
    protected Context        context;

    /**
     * Creates a client for a vert.x context.
     * 
     * @param context The context to run all interactions with the server on.
     */
    protected AbstractHonoClient(final Context context) {
        this.context = Objects.requireNonNull(context);
    }

    /**
     * Closes this client's sender and receiver links to Hono.
     * 
     * @param closeHandler the handler to be notified about the outcome.
     * @throws NullPointerException if the given handler is {@code null}.
     */
    protected void closeLinks(final Handler<AsyncResult<Void>> closeHandler) {

        Objects.requireNonNull(closeHandler);

        final Future<ProtonSender> senderCloseHandler = Future.future();
        final Future<ProtonReceiver> receiverCloseHandler = Future.future();
        receiverCloseHandler.setHandler(closeAttempt -> {
            if (closeAttempt.succeeded()) {
                closeHandler.handle(Future.succeededFuture());
            } else {
                closeHandler.handle(Future.failedFuture(closeAttempt.cause()));
            }
        });

        senderCloseHandler.compose(closedSender -> {
            if (receiver != null && receiver.isOpen()) {
                receiver.closeHandler(closeAttempt -> {
                    LOG.debug("closed message consumer for [{}]", receiver.getSource().getAddress());
                    receiverCloseHandler.complete(receiver);
                }).close();
            } else {
                receiverCloseHandler.complete();
            }
        }, receiverCloseHandler);

        context.runOnContext(close -> {

            if (sender != null && sender.isOpen()) {
                sender.closeHandler(closeAttempt -> {
                    LOG.debug("closed message sender for [{}]", sender.getTarget().getAddress());
                    senderCloseHandler.complete(sender);
                }).close();
            } else {
                senderCloseHandler.complete();
            }
        });
    }

    /**
     * Set the application properties for a Proton Message but do a check for all properties first if they only contain
     * values that the AMQP 1.0 spec allows.
     *
     * @param msg The Proton message. Must not be null.
     * @param properties The map containing application properties.
     * @throws NullPointerException if the message passed in is null.
     * @throws IllegalArgumentException if the properties contain any value that AMQP 1.0 disallows.
     */
    protected static final void setApplicationProperties(final Message msg, final Map<String, ?> properties) {
        if (properties != null) {

            // check the three types not allowed by AMQP 1.0 spec for application properties (list, map and array)
            for (final Map.Entry<String, ?> entry: properties.entrySet()) {
                if (entry.getValue() instanceof List) {
                    throw new IllegalArgumentException(String.format("Application property %s can't be a List", entry.getKey()));
                } else if (entry.getValue() instanceof Map) {
                    throw new IllegalArgumentException(String.format("Application property %s can't be a Map", entry.getKey()));
                } else if (entry.getValue().getClass().isArray()) {
                    throw new IllegalArgumentException(String.format("Application property %s can't be an Array", entry.getKey()));
                }
            }

            final ApplicationProperties applicationProperties = new ApplicationProperties(properties);
            msg.setApplicationProperties(applicationProperties);
        }
    }

    /**
     * Creates a sender link.
     * 
     * @param ctx The vert.x context to use for establishing the link.
     * @param con The connection to create the link for.
     * @param targetAddress The target address of the link.
     * @param qos The quality of service to use for the link.
     * @param waitForInitialCredits Milliseconds to wait after link creation if there are no credits.
     * @param closeHook The handler to invoke when the link is closed by the peer.
     * @return A future for the created link. The future will be completed once the link is open.
     *         The future will fail if the link cannot be opened.
     * @throws IllegalArgumentException if waitForInitialCredits is {@code < 1}.
     */
    protected static final Future<ProtonSender> createSender(
            final Context ctx,
            final ProtonConnection con,
            final String targetAddress,
            final ProtonQoS qos,
            final long waitForInitialCredits,
            final Handler<String> closeHook) {

        final Future<ProtonSender> result = Future.future();

        ctx.runOnContext(create -> {
            final ProtonSender sender = con.createSender(targetAddress);
            sender.setQoS(qos);
            sender.openHandler(senderOpen -> {
                if (senderOpen.succeeded()) {
                    LOG.debug("sender open [{}] sendQueueFull [{}]", sender.getRemoteTarget(), sender.sendQueueFull());
                    // wait on credits a little time, if not already given
                    if (sender.sendQueueFull()) {
                        ctx.owner().setTimer(waitForInitialCredits, timerID -> {
                            LOG.debug("waited [{}] ms on credits [{}]", waitForInitialCredits, sender.getCredit());
                            result.complete(sender);
                        });
                    } else {
                        result.complete(sender);
                    }
                } else {
                    LOG.debug("opening sender [{}] failed: {}", targetAddress, senderOpen.cause().getMessage());
                    result.fail(senderOpen.cause());
                }
            });
            sender.closeHandler(senderClosed -> {
                if (senderClosed.succeeded()) {
                    LOG.debug("sender [{}] closed by peer", targetAddress);
                } else {
                    LOG.debug("sender [{}] closed by peer: {}", targetAddress, senderClosed.cause().getMessage());
                }
                sender.close();
                if (closeHook != null) {
                    closeHook.handle(targetAddress);
                }
            });
            sender.open();
        });

        return result;
    }

    /**
     * Creates a receiver link.
     * <p>
     * The receiver will be created with its <em>autoAccept</em> property set to {@code true}.
     *
     * @param ctx The vert.x context to use for establishing the link.
     * @param con The connection to create the link for.
     * @param sourceAddress The address to receive messages from.
     * @param qos The quality of service to use for the link.
     * @param prefetchCredits Number of credits, given initially from receiver to sender.
     * @param messageHandler The handler to invoke with every message received.
     * @param closeHook The handler to invoke when the link is closed by the peer.
     * @return A future for the created link. The future will be completed once the link is open.
     *         The future will fail if the link cannot be opened.
     * @throws IllegalArgumentException if prefetchCredits is {@code < 0}.
     */
    protected static final Future<ProtonReceiver> createReceiver(
            final Context ctx,
            final ProtonConnection con,
            final String sourceAddress,
            final ProtonQoS qos,
            final int prefetchCredits,
            final ProtonMessageHandler messageHandler,
            final Handler<String> closeHook) {

        Future<ProtonReceiver> result = Future.future();
        ctx.runOnContext(go -> {
            final ProtonReceiver receiver = con.createReceiver(sourceAddress);
            receiver.setAutoAccept(true);
            receiver.setQoS(qos);
            receiver.setPrefetch(prefetchCredits);
            receiver.handler(messageHandler);
            receiver.openHandler(result.completer());
            receiver.closeHandler(remoteClosed -> {
                if (remoteClosed.succeeded()) {
                    LOG.debug("receiver [{}] closed by peer [{}]", sourceAddress, con.getRemoteContainer());
                } else {
                    LOG.debug("receiver [{}] closed by peer [{}]: {}", sourceAddress, con.getRemoteContainer(), remoteClosed.cause().getMessage());
                }
                receiver.close();
                if (closeHook != null) {
                    closeHook.handle(sourceAddress);
                }
            });
            receiver.open();
        });
        return result;

    }
}
