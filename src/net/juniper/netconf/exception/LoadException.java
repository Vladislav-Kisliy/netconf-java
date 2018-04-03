/**
 * Copyright (c) 2013 Juniper Networks, Inc.
 * All Rights Reserved
 * <p>
 * Use is subject to license terms.
 */

package net.juniper.netconf.exception;

import java.io.IOException;

/**
 * Describes exceptions related to load operation
 */
public class LoadException extends IOException {

    private final String loadErrorMsg;

    public LoadException(String msg) {
        super(msg);
        loadErrorMsg = msg;
    }

    public String getLoadErrorMessage() {
        return loadErrorMsg;
    }
}
