/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.integration.duradmin;

/**
 * @author Andrew Woods
 *         Date: Apr 19, 2010
 */
public class DuradminTestBase {
    
    private static String baseUrl;

    private String getHost() {
        return "";
    }

    private String getPort() {
        return "";
    }

    protected String getBaseUrl() {
        if (null == baseUrl) {
            baseUrl = "http://" + getHost() + ":" + getPort() + "/duradmin";
        }
        return baseUrl;
    }
}