import jenkins.branch.OrganizationFolder
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMNavigator
import org.jenkinsci.plugins.workflow.libs.FolderLibraries
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever
import jenkins.plugins.git.GitSCMSource


def project_name = "${project_name}"
def folder_library_name = "${library_name}"
def giturl = "${giturl}"
def gitcredsid = "${git_cred_id}"
def default_version = "${default_version}"
def load_implicit = "${load_implicit}".toBoolean()
def allow_version_override = "${allow_version_override}".toBoolean()
def state = "${state}"


instance = Jenkins.getInstance()
def ofs = instance.getAllItems(OrganizationFolder)

def projects = ofs.findAll { it.getNavigators().find { it instanceof BitbucketSCMNavigator  }  }

def project =  projects.find { it.name == project_name  }

FolderLibraries folderLibraries

def projectProperties = project.getProperties()

folderLibraries = projectProperties.find {
  it instanceof FolderLibraries
}

List<LibraryConfiguration> libraries = []

if (folderLibraries) {
  libraries = folderLibraries.getLibraries()

  def library_check = libraries.find { it.name == folder_library_name }

  if ((library_check) && (state == "present")) {
    // library exists so just return if state is present
    return ""
  }

  if (!(library_check) && (state == "absent")) {
    // library doesn't exist and we don't want it so just return
    return ""
  }

  if (state == "absent") {
      // there is no remove library function so the simplest way is to build a new array with it missing
      List<LibraryConfiguration> newFolderLibraries = []
      folderLibraries.getLibraries().each {
        if (it.name != folder_library_name) {
          newFolderLibraries.add(it)
        }
      }

      projectProperties.replace(newFolderLibraries)
      project.save()
      return "removed folder library"
  }


  if (state != "present") {
    // saftey check
    throw new Exception("state must be present or absent")
  }

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

def library = new LibraryConfiguration(folder_library_name, new SCMSourceRetriever(gitscm))

library.setImplicit(load_implicit)
library.setDefaultVersion(default_version)
library.setAllowVersionOverride(allow_version_override)

List<LibraryConfiguration> newLibraries = libraries + library

FolderLibraries newFolderLibraries = new FolderLibraries(newLibraries)

if (folderLibraries) {
  projectProperties.replace(newFolderLibraries)
} else {
  project.addProperty(newFolderLibraries)
}

project.save()

return "added project library"

