/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.mqtt.bridge.util;

import com.aws.greengrass.config.ChildChanged;
import com.aws.greengrass.config.Node;
import com.aws.greengrass.config.Subscriber;
import com.aws.greengrass.config.Topic;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.config.WhatHappened;
import lombok.NonNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

/**
 * NOTE: In order to maintain backwards compatibility with nucleus and prevent bumping its version,
 * the decision has been made to simply duplicate BatchedSubscriber here.  If we bump the nucleus version
 * in the future then we can likely remove this class.
 *
 * {@link BatchedSubscriber} is a subscriber that fires once for a <i>batch</i> of changes
 * (and on subscription initialization).
 *
 * <br><br><p>A <i>batch</i> is defined as all the elements in a {@link Topic} or {@link Topics}' publish queue,
 * with the last <i>batch</i> element being the most recent topic change.
 *
 * <br><br><p>By default, commonly ignored changes, like {@link WhatHappened#timestampUpdated} and
 * {@link WhatHappened#interiorAdded}, will NOT be added to a <i>batch</i>
 * (see {@link BatchedSubscriber#BASE_EXCLUSION}).
 *
 * <br><br><p>To be precise, a {@link BatchedSubscriber} will trigger its {@link BatchedSubscriber#callback}
 * after the following events:
 * <ul>
 *     <li>when {@link WhatHappened#initialized} is fired on initial subscription</li>
 *     <li>when the last <i>batch</i> element is popped from the topic's publish queue</li>
 * </ul>
 */
public final class BatchedSubscriber implements ChildChanged, Subscriber {

    public static final BiPredicate<WhatHappened, Node> BASE_EXCLUSION = (what, child) ->
            what == WhatHappened.timestampUpdated || what == WhatHappened.interiorAdded;

    private final AtomicInteger numRequestedChanges = new AtomicInteger();
    private final Set<Node> whatChanged = new HashSet<>();

    private final Node node;
    private final BiPredicate<WhatHappened, Node> exclusions;
    private final BiConsumer<WhatHappened, Set<Node>> callback;

    /**
     * Constructs a new BatchedSubscriber.
     *
     * <p>Defaults to using {@link BatchedSubscriber#BASE_EXCLUSION} for excluding changes from a <i>batch</i>.
     *
     * @param topic    topic to subscribe to
     * @param callback action to perform after a <i>batch</i> of changes and on initialization
     */
    public BatchedSubscriber(Topic topic, BiConsumer<WhatHappened, Set<Node>> callback) {
        this(topic, BASE_EXCLUSION, callback);
    }

    /**
     * Constructs a new BatchedSubscriber.
     *
     * @param topic      topic to subscribe to
     * @param exclusions predicate for ignoring a subset topic changes
     * @param callback   action to perform after a <i>batch</i> of changes and on initialization
     */
    public BatchedSubscriber(Topic topic,
                             BiPredicate<WhatHappened, Node> exclusions,
                             BiConsumer<WhatHappened, Set<Node>> callback) {
        this((Node) topic, exclusions, callback);
    }

    /**
     * Constructs a new BatchedSubscriber.
     *
     * <p>Defaults to using {@link BatchedSubscriber#BASE_EXCLUSION} for excluding changes from a <i>batch</i>.
     *
     * @param topics   topics to subscribe to
     * @param callback action to perform after a <i>batch</i> of changes and on initialization
     */
    public BatchedSubscriber(Topics topics, BiConsumer<WhatHappened, Set<Node>> callback) {
        this(topics, BASE_EXCLUSION, callback);
    }

    /**
     * Constructs a new BatchedSubscriber.
     *
     * @param topics     topics to subscribe to
     * @param exclusions predicate for ignoring a subset topics changes
     * @param callback   action to perform after a <i>batch</i> of changes and on initialization
     */
    public BatchedSubscriber(Topics topics,
                             BiPredicate<WhatHappened, Node> exclusions,
                             BiConsumer<WhatHappened, Set<Node>> callback) {
        this((Node) topics, exclusions, callback);
    }

    /**
     * Constructs a new BatchedSubscriber.
     *
     * @param node       topic or topics to subscribe to
     * @param exclusions predicate for ignoring a subset topic(s) changes
     * @param callback   action to perform after a <i>batch</i> of changes and on initialization
     */
    private BatchedSubscriber(@NonNull Node node,
                              BiPredicate<WhatHappened, Node> exclusions,
                              @NonNull BiConsumer<WhatHappened, Set<Node>> callback) {
        this.node = node;
        this.exclusions = exclusions;
        this.callback = callback;
    }

    /**
     * Subscribe to the topic(s).
     */
    public void subscribe() {
        if (node instanceof Topic) {
            ((Topic) node).subscribe(this);
        }
        if (node instanceof Topics) {
            ((Topics) node).subscribe(this);
        }
    }

    /**
     * Unsubscribe from the topic(s).
     */
    public void unsubscribe() {
        node.remove(this);
    }

    @Override
    public void childChanged(WhatHappened what, Node child) {
        onChange(what, child);
    }

    @Override
    public void published(WhatHappened what, Topic t) {
        onChange(what, t);
    }

    private void onChange(WhatHappened what, Node child) {
        if (exclusions != null && exclusions.test(what, child)) {
            return;
        }

        if (what == WhatHappened.initialized) {
            callback.accept(what, Collections.emptySet());
            return;
        }

        synchronized (whatChanged) {
            whatChanged.add(child);
        }

        numRequestedChanges.incrementAndGet();
        child.context.runOnPublishQueue(() -> {
            if (numRequestedChanges.decrementAndGet() == 0) {
                synchronized (whatChanged) {
                    callback.accept(what, whatChanged);
                    whatChanged.clear();
                }
            }
        });
    }
}
