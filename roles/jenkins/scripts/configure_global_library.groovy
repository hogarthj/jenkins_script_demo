import org.jenkinsci.plugins.workflow.libs.GlobalLibraries
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever
import jenkins.plugins.git.GitSCMSource

def instance = Jenkins.getInstance()

GlobalLibraries globalLibraries = GlobalConfiguration.all().get(GlobalLibraries.class);

def libraries = globalLibraries.getLibraries()

def global_library_name = "${library_name}"

def giturl = "${giturl}"
def gitcredsid = "${git_cred_id}"
def default_version = "${default_version}"
def load_implicit = "${load_implicit}".toBoolean()
def allow_version_override = "${allow_version_override}".toBoolean()
def state = "${state}"

def library_check = libraries.find {

  it.name == global_library_name
}

if ((library_check) && (state == "present")) {
  // library exists so just return if state is present
  return ""
}

if (!(library_check) && (state == "absent")) {
  // library doesn't exist and we don't want it so just return if state is present
  return ""
}

if (state == "absent") {
  // there is no remove library function so the simplest way is to build a new array with it missing
  List<LibraryConfiguration> newGlobalLibraries = []
  globalLibraries.getLibraries().each {
    if (it.name != global_library_name) {
      newGlobalLibraries.add(it)
    }
  }

  globalLibraries.setLibraries(newGlobalLibraries)
  instance.save()
  return "removed global library"
}

if (state != "present") {
  // saftey check
  throw new Exception("state must be present or absent")
}

def gitscmprops = [
        id: null,
        remote: giturl,
        credentialsId: gitcredsid,
        remoteName: "origin",
        rawRefSpecs: "+refs/heads/*:refs/remotes/origin/*",
        includes: "*",
        excludes: "",
        ignoreOnPushNotifications: false
]

def gitscm = new GitSCMSource(
  gitscmprops.id,
  gitscmprops.remote,
  gitscmprops.credentialsId,
  gitscmprops.remoteName,
  gitscmprops.rawRefSpecs,
  gitscmprops.includes,
  gitscmprops.excludes,
  gitscmprops.ignoreOnPushNotifications
)

def library = new LibraryConfiguration(global_library_name, new SCMSourceRetriever(gitscm))

library.setImplicit(load_implicit)
library.setDefaultVersion(default_version)
library.setAllowVersionOverride(allow_version_override)

List<LibraryConfiguration> newGlobalLibraries = libraries + library

globalLibraries.setLibraries(newGlobalLibraries)
instance.save()

return "added global library"
