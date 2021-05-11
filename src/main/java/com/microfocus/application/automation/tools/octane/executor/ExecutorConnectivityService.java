/*
 * Certain versions of software and/or documents ("Material") accessible here may contain branding from
 * Hewlett-Packard Company (now HP Inc.) and Hewlett Packard Enterprise Company.  As of September 1, 2017,
 * the Material is now offered by Micro Focus, a separately owned and operated company.  Any reference to the HP
 * and Hewlett Packard Enterprise/HPE marks is historical in nature, and the HP and Hewlett Packard Enterprise/HPE
 * marks are the property of their respective owners.
 * __________________________________________________________________
 * MIT License
 *
 * (c) Copyright 2012-2021 Micro Focus or one of its affiliates.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * ___________________________________________________________________
 */

package com.microfocus.application.automation.tools.octane.executor;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.hp.octane.integrations.dto.DTOFactory;
import com.hp.octane.integrations.dto.connectivity.OctaneResponse;
import com.hp.octane.integrations.dto.executor.CredentialsInfo;
import com.hp.octane.integrations.dto.executor.TestConnectivityInfo;
import com.microfocus.application.automation.tools.octane.configuration.SDKBasedLoggerProvider;
import com.microfocus.application.automation.tools.octane.executor.scmmanager.ScmPluginFactory;
import com.microfocus.application.automation.tools.octane.executor.scmmanager.ScmPluginHandler;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Utility for handling connectivity with scm repositories
 */
public class ExecutorConnectivityService {
	private static final Logger logger = SDKBasedLoggerProvider.getLogger(ExecutorConnectivityService.class);
	private static final Map<Permission, String> requirePremissions = initRequirePermissions();
	private static final Map<Permission, String> credentialsPremissions = initCredentialsPermissions();
	private static final String PLUGIN_NAME = "Application Automation Tools";

	/**
	 * Validate that scm repository is valid
	 *
	 * @param testConnectivityInfo contains values to check
	 * @return OctaneResponse return status code and error to show for client
	 */
	public static OctaneResponse checkRepositoryConnectivity(TestConnectivityInfo testConnectivityInfo) {
		logger.info("checkRepositoryConnectivity started to " + testConnectivityInfo.getScmRepository().getUrl());
		long start = System.currentTimeMillis();
		OctaneResponse result = DTOFactory.getInstance().newDTO(OctaneResponse.class);
		if (testConnectivityInfo.getScmRepository() != null && StringUtils.isNotEmpty(testConnectivityInfo.getScmRepository().getUrl())) {

			boolean needCredentialsPermission = false;
			BaseStandardCredentials credentials = null;
			if (StringUtils.isNotEmpty(testConnectivityInfo.getUsername()) && testConnectivityInfo.getPassword() != null) {
				credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, null, null, testConnectivityInfo.getUsername(), testConnectivityInfo.getPassword());
				needCredentialsPermission = true;
			} else if (StringUtils.isNotEmpty(testConnectivityInfo.getCredentialsId())) {
				credentials = getCredentialsById(testConnectivityInfo.getCredentialsId());
			}

			long permValidationStarted = System.currentTimeMillis();
			List<String> permissionResult = checkCIPermissions(Jenkins.getInstanceOrNull(), needCredentialsPermission);
			long permValidationEnded = System.currentTimeMillis();
			logger.info(String.format("checkRepositoryConnectivity : permission check is done in %s ms ", (permValidationEnded - permValidationStarted)));
			if (!permissionResult.isEmpty()) {
				String user = User.current() != null ? User.current().getId() : Jenkins.ANONYMOUS.getPrincipal().toString();
				String error = String.format("Failed : User \'%s\' is missing permissions \'%s\' on CI server", user, permissionResult);
				logger.error(error);
				result.setStatus(HttpStatus.SC_FORBIDDEN);
				result.setBody(error);
				return result;
			}

			long scmValidationStarted = System.currentTimeMillis();
			boolean requiredPluginInstalled = ScmPluginFactory.isPluginInstalled(testConnectivityInfo.getScmRepository().getType());
			if (!requiredPluginInstalled) {
				result.setStatus(HttpStatus.SC_BAD_REQUEST);
				result.setBody(String.format("%s plugin is not installed.", testConnectivityInfo.getScmRepository().getType().value().toUpperCase()));
			} else {

				long pluginInstalledValidationStarted = System.currentTimeMillis();
				ScmPluginHandler handler = ScmPluginFactory.getScmHandler(testConnectivityInfo.getScmRepository().getType());
				long pluginInstalledValidationEnded = System.currentTimeMillis();
				logger.info(String.format("checkRepositoryConnectivity : plugin installed check is done in %s ms ", (pluginInstalledValidationEnded - pluginInstalledValidationStarted)));

				handler.checkRepositoryConnectivity(testConnectivityInfo, credentials, result);
				long scmValidationEnded = System.currentTimeMillis();
				logger.info(String.format("checkRepositoryConnectivity : scm check is done in %s ms ", (scmValidationEnded - scmValidationStarted)));
			}

		} else {
			result.setStatus(HttpStatus.SC_BAD_REQUEST);
			result.setBody("Missing input for testing");
		}
		long end = System.currentTimeMillis();
		if (result.getStatus() != HttpStatus.SC_OK) {
			logger.info("checkRepositoryConnectivity failed: " + result.getBody());
		} else {
			logger.info(String.format("checkRepositoryConnectivity ok, done in %s ms ", (end - start)));
		}
		return result;
	}

	/**
	 * Insert of update(if already exist) of credentials in Jenkins.
	 * If credentialsInfo contains credentialsId - we update existing credentials with new user/password, otherwise - create new credentials
	 *
	 * @param credentialsInfo contains values to insert / update - exist credentials will be updated or recreate if deleted in jenkins
	 * @return OctaneResponse created/updated credentials with filled credentials id as body
	 */
	public static OctaneResponse upsertRepositoryCredentials(final CredentialsInfo credentialsInfo) {

		OctaneResponse result = DTOFactory.getInstance().newDTO(OctaneResponse.class);
		result.setStatus(HttpStatus.SC_OK);
		BaseStandardCredentials jenkinsCredentials = null;

		if (StringUtils.isNotEmpty(credentialsInfo.getUsername()) && credentialsInfo.getPassword() != null) {
			jenkinsCredentials = tryGetCredentialsByUsernamePassword(credentialsInfo.getUsername(), credentialsInfo.getPassword());
			if (jenkinsCredentials == null) {
				SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
				String desc = String.format("Created by the Microfocus %s plugin on %s", PLUGIN_NAME, formatter.format(new Date()));
				BaseStandardCredentials c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, credentialsInfo.getCredentialsId(), desc, credentialsInfo.getUsername(), credentialsInfo.getPassword());
				CredentialsStore store = new SystemCredentialsProvider.StoreImpl();
				try {
					if(store.addCredentials(Domain.global(), c)){
						jenkinsCredentials = c;
                        result.setStatus(HttpStatus.SC_CREATED);
					}
				} catch (IOException e) {
					logger.error("Failed to add credentials " + e.getMessage());
					result.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
					result.setBody("Failed to add credentials " + e.getMessage());
				}
			}
		}

		if (jenkinsCredentials != null) {
			result.setBody(jenkinsCredentials.getId());
		}

		return result;
	}

	private static BaseStandardCredentials getCredentialsById(String credentialsId) {
		List<BaseStandardCredentials> list = CredentialsProvider.lookupCredentials(BaseStandardCredentials.class, (Item) null, null, (DomainRequirement) null);
		for (BaseStandardCredentials cred : list) {
			if (cred.getId().equals(credentialsId))
				return cred;
		}
		return null;
	}

	private static UsernamePasswordCredentialsImpl tryGetCredentialsByUsernamePassword(String username, String password) {
		List<UsernamePasswordCredentialsImpl> list = CredentialsProvider.lookupCredentials(UsernamePasswordCredentialsImpl.class, (Item) null, null, (DomainRequirement) null);
		for (UsernamePasswordCredentialsImpl cred : list) {
			if (StringUtils.equalsIgnoreCase(cred.getUsername(), username)
					&& StringUtils.equals(cred.getPassword().getPlainText(), password)
					&& cred.getDescription() != null && cred.getDescription().contains(PLUGIN_NAME)) {
				return cred;
			}
		}
		return null;
	}

	private static List<String> checkCIPermissions(final Jenkins jenkins, boolean checkCredentialsPermissions) {
		List<String> result = new ArrayList<>();
		checkPermissions(jenkins, result, requirePremissions);
		if (checkCredentialsPermissions) {
			checkPermissions(jenkins, result, credentialsPremissions);
		}
		return result;
	}

	private static void checkPermissions(Jenkins jenkins, List<String> result, Map<Permission, String> permissions) {
		for (Permission permission : permissions.keySet()) {
			if (!jenkins.hasPermission(permission)) {
				result.add(permissions.get(permission));
			}
		}
	}

	private static Map<Permission, String> initRequirePermissions() {
		Map<Permission, String> result = new HashMap<>();
		result.put(Item.CREATE, "Job.CREATE");
		result.put(Item.READ, "Job.READ");
		return result;
	}

	private static Map<Permission, String> initCredentialsPermissions() {
		Map<Permission, String> result = new HashMap<>();
		result.put(CredentialsProvider.CREATE, "Credentials.CREATE");
		return result;

	}
}
