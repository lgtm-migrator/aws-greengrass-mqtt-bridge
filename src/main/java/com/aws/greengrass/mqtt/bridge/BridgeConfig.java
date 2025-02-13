/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.mqtt.bridge;

import com.aws.greengrass.componentmanager.KernelConfigResolver;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.Utils;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Utilities for accessing MQTT Bridge configuration.
 */
public final class BridgeConfig {

    static final String KEY_BROKER_SERVER_URI = "brokerServerUri"; // for backwards compatibility only
    public static final String KEY_BROKER_URI = "brokerUri";
    public static final String KEY_CLIENT_ID = "clientId";
    static final String KEY_MQTT_TOPIC_MAPPING = "mqttTopicMapping";

    static final String[] PATH_BROKER_SERVER_URI =
            {KernelConfigResolver.CONFIGURATION_CONFIG_KEY, KEY_BROKER_SERVER_URI};
    static final String[] PATH_BROKER_URI =
            {KernelConfigResolver.CONFIGURATION_CONFIG_KEY, KEY_BROKER_URI};
    static final String[] PATH_CLIENT_ID =
            {KernelConfigResolver.CONFIGURATION_CONFIG_KEY, KEY_CLIENT_ID};
    static final String[] PATH_MQTT_TOPIC_MAPPING =
            {KernelConfigResolver.CONFIGURATION_CONFIG_KEY, KEY_MQTT_TOPIC_MAPPING};

    private static final String DEFAULT_BROKER_URI = "ssl://localhost:8883";
    private static final String DEFAULT_CLIENT_ID = "mqtt-bridge-" + Utils.generateRandomString(11);

    private BridgeConfig(){
    }

    /**
     * Get brokerUri configuration.
     *
     * @param topics config topics
     * @return brokerUri value
     * @throws URISyntaxException for invalid brokerUri format
     */
    public static URI getBrokerUri(Topics topics) throws URISyntaxException {
        // brokerUri takes precedence since brokerServerUri is deprecated
        String brokerServerUri = Coerce.toString(
                topics.findOrDefault(DEFAULT_BROKER_URI, PATH_BROKER_SERVER_URI));
        String brokerUri = Coerce.toString(
                topics.findOrDefault(brokerServerUri, PATH_BROKER_URI));
        return new URI(brokerUri);
    }

    /**
     * Get clientId configuration.
     *
     * @param topics config topics
     * @return clientId value
     */
    public static String getClientId(Topics topics) {
        return Coerce.toString(topics.findOrDefault(DEFAULT_CLIENT_ID, PATH_CLIENT_ID));
    }
}
