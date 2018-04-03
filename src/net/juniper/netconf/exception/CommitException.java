/**
 * Copyright (c) 2013 Juniper Networks, Inc.
 * All Rights Reserved
 * <p>
 * Use is subject to license terms.
 */

package net.juniper.netconf.exception;

import java.io.IOException;

/**
 * Describes exceptions related to commit operation
 */
public class CommitException extends IOException {

    private final String commitErrorMsg;

    public CommitException(String msg) {
        super(msg);
        commitErrorMsg = msg;
    }

    public String getCommitErrorMessage() {
        return commitErrorMsg;
    }
}
