/*
 * Certain versions of software and/or documents ("Material") accessible here may contain branding from
 * Hewlett-Packard Company (now HP Inc.) and Hewlett Packard Enterprise Company.  As of September 1, 2017,
 * the Material is now offered by Micro Focus, a separately owned and operated company.  Any reference to the HP
 * and Hewlett Packard Enterprise/HPE marks is historical in nature, and the HP and Hewlett Packard Enterprise/HPE
 * marks are the property of their respective owners.
 * __________________________________________________________________
 * MIT License
 *
 * (c) Copyright 2012-2018 Micro Focus or one of its affiliates.
 *
 * The only warranties for products and services of Micro Focus and its affiliates
 * and licensors ("Micro Focus") are set forth in the express warranty statements
 * accompanying such products and services. Nothing herein should be construed as
 * constituting an additional warranty. Micro Focus shall not be liable for technical
 * or editorial errors or omissions contained herein.
 * The information contained herein is subject to change without notice.
 * ___________________________________________________________________
 */

package com.microfocus.application.automation.tools.common;

import hudson.AbortException;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;

public class HealthAnalyzerCommonTest {
    private final static String DUMMY_PRODUCT_NAME = "productName";
    private final static String NON_EXISTING_REGISTRY = "non\\existing\\registry\\value";
    private static HealthAnalyzerCommon healthAnalyzerCommon;

    @BeforeClass
    public static void setup() {
        healthAnalyzerCommon = new HealthAnalyzerCommon(DUMMY_PRODUCT_NAME);
    }

    @Test(expected = AbortException.class)
    public void isCheckedPerformWindowsInstallationCheck_throwsException_ifValueDoesNotExistsAndToCheckIsTrue()
            throws IOException, InterruptedException {
        healthAnalyzerCommon.ifCheckedPerformWindowsInstallationCheck(NON_EXISTING_REGISTRY, true);
    }

    @Test(expected = AbortException.class)
    public void ifCheckedIsUrlExist_throwsException_ifUrlDoesNotExistsAndToCheckIsTrue() throws IOException {
        String url = "https://non-exisiting-url-for-checking.com";
        healthAnalyzerCommon.ifCheckedDoesUrlExist(url, true);
    }

    @Test
    public void ifCheckedIsUrlExists_shouldNotThrowException_ifUrlExistAndToCheckIsTrue() throws IOException {
        String url = "https://www.microfocus.com/";
        try {
            healthAnalyzerCommon.ifCheckedDoesUrlExist(url, true);
        } catch (AbortException e) {
            fail("Should not have thrown AbortException");
        }
    }

    @Test
    public void isCheckedPerformWindowsInstallationCheck_throwsCorrectExceptionValue()
            throws IOException, InterruptedException {
        try {
            healthAnalyzerCommon.ifCheckedPerformWindowsInstallationCheck(NON_EXISTING_REGISTRY, true);
        } catch (AbortException e) {
            assertEquals(e.getMessage(), DUMMY_PRODUCT_NAME + " is not installed, please install it first.");
        }
    }


    // TODO: is checking the folder and and not specific key is enough?
    @Test
    public void isRegistryExists_shouldReturnTrue_ifValueExists() throws IOException, InterruptedException {
        String existingRegistryValue = "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\";
        try {
            healthAnalyzerCommon.ifCheckedPerformWindowsInstallationCheck(existingRegistryValue, true);
        } catch (AbortException e) {
            fail("Should not have thrown AbortException");
        }
    }

    @Test(expected = AbortException.class)
    public void ifCheckedIsFileExist_throwsException_ifFileDoesNotExist() {
        String path = "non//existing//path";
        //  healthAnalyzerCommon.ifCheckedIsFileExist(path, true);
    }
}