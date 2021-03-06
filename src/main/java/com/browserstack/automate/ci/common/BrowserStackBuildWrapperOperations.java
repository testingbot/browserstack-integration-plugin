package com.browserstack.automate.ci.common;

import static com.browserstack.automate.ci.common.logger.PluginLogger.log;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.FileScanner;
import org.apache.tools.ant.types.FileSet;
import com.browserstack.automate.ci.jenkins.BrowserStackCredentials;
import com.browserstack.automate.ci.jenkins.local.JenkinsBrowserStackLocal;
import com.browserstack.automate.ci.jenkins.local.LocalConfig;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class BrowserStackBuildWrapperOperations {
  private BrowserStackCredentials credentials;
  private boolean isTearDownPhase;
  private PrintStream logger;
  private static final String ENV_JENKINS_BUILD_TAG = "BUILD_TAG";
  private LocalConfig localConfig;
  private JenkinsBrowserStackLocal browserstackLocal;

  public BrowserStackBuildWrapperOperations(BrowserStackCredentials credentials,
      boolean isTearDownPhase, PrintStream logger, LocalConfig localConfig,
      JenkinsBrowserStackLocal browserStackLocal) {
    super();
    this.credentials = credentials;
    this.isTearDownPhase = isTearDownPhase;
    this.logger = logger;
    this.localConfig = localConfig;
    this.browserstackLocal = browserStackLocal;
  }

  public void buildEnvVars(Map<String, String> env) {
    if (credentials != null) {
      if (credentials.hasUsername()) {
        String username = credentials.getUsername();

        env.put(BrowserStackEnvVars.BROWSERSTACK_USER, username + "-jenkins");
        env.put(BrowserStackEnvVars.BROWSERSTACK_USERNAME, username + "-jenkins");
        logEnvVar(BrowserStackEnvVars.BROWSERSTACK_USERNAME, username);
      }

      if (credentials.hasAccesskey()) {
        String accesskey = credentials.getDecryptedAccesskey();
        env.put(BrowserStackEnvVars.BROWSERSTACK_ACCESSKEY, accesskey);
        env.put(BrowserStackEnvVars.BROWSERSTACK_ACCESS_KEY, accesskey);
        logEnvVar(BrowserStackEnvVars.BROWSERSTACK_ACCESS_KEY, Tools.maskString(accesskey));
      }
    }

    String buildTag = env.get(ENV_JENKINS_BUILD_TAG);
    if (buildTag != null) {
      env.put(BrowserStackEnvVars.BROWSERSTACK_BUILD, buildTag);
      logEnvVar(BrowserStackEnvVars.BROWSERSTACK_BUILD, buildTag);
    }

    String isLocalEnabled = localConfig != null ? "true" : "false";
    env.put(BrowserStackEnvVars.BROWSERSTACK_LOCAL, "" + isLocalEnabled);
    logEnvVar(BrowserStackEnvVars.BROWSERSTACK_LOCAL, isLocalEnabled);

    String localIdentifier =
        (browserstackLocal != null) ? browserstackLocal.getLocalIdentifier() : "";

    if (StringUtils.isNotBlank(localIdentifier)) {
      env.put(BrowserStackEnvVars.BROWSERSTACK_LOCAL_IDENTIFIER, localIdentifier);
      logEnvVar(BrowserStackEnvVars.BROWSERSTACK_LOCAL_IDENTIFIER, localIdentifier);
    }
  }

  public void logEnvVar(String key, String value) {
    if (!isTearDownPhase) {
      log(logger, key + "=" + value);
    }
  }

  public static ListBoxModel doFillCredentialsIdItems(Item context) {
    if (context != null && !context.hasPermission(Item.CONFIGURE)) {
      return new StandardListBoxModel();
    }

    return new StandardListBoxModel().withMatching(
        CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(BrowserStackCredentials.class)),
        CredentialsProvider.lookupCredentials(BrowserStackCredentials.class, context, ACL.SYSTEM,
            new ArrayList<DomainRequirement>()));
  }

  public static FormValidation doCheckLocalPath(final AbstractProject project,
      final String localPath) {
    final String path = Util.fixEmptyAndTrim(localPath);
    if (StringUtils.isBlank(path)) {
      return FormValidation.ok();
    }

    try {
      File f = resolvePath(project, localPath);
      if (f != null) {
        return FormValidation.ok();
      }
    } catch (Exception e) {
      return FormValidation.error(e.getMessage());
    }

    return FormValidation.error("Invalid path.");
  }

  private static File resolvePath(final AbstractProject project, final String path)
      throws IOException, InterruptedException {
    File f = new File(path);
    if (f.isAbsolute() && (!f.isFile() || !f.canExecute())) {
      return null;
    }

    // For absolute paths
    FormValidation validateExec = FormValidation.validateExecutable(path);
    if (validateExec.kind == FormValidation.Kind.OK) {
      return f;
    }

    // Ant style path definitions
    FilePath workspace = project.getSomeWorkspace();
    if (workspace != null) {
      File workspaceRoot = new File(workspace.toURI());
      FileSet fileSet = Util.createFileSet(workspaceRoot, path);
      FileScanner fs = fileSet.getDirectoryScanner();
      fs.setIncludes(new String[] {path});
      fs.scan();

      String[] includedFiles = fs.getIncludedFiles();
      if (includedFiles.length > 0) {
        File includedFile = new File(workspaceRoot, includedFiles[0]);
        if (includedFile.exists() && includedFile.isFile() && includedFile.canExecute()) {
          return includedFile;
        }
      }
    }
    return null;
  }
}
