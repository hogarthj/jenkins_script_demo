import com.cloudbees.hudson.plugins.folder.*;
import com.cloudbees.hudson.plugins.folder.properties.*;
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider.FolderCredentialsProperty;
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.common.*
import com.cloudbees.plugins.credentials.domains.*
import com.cloudbees.plugins.credentials.domains.*;
import com.cloudbees.plugins.credentials.impl.*
import com.cloudbees.plugins.credentials.impl.*;
import hudson.util.Secret
import java.nio.file.Files
import jenkins.branch.OrganizationFolder;
import org.jenkinsci.plugins.plaincredentials.impl.*

def project = "${cred_project}"
def cred_desc = "${cred_desc}"
def cred_type = "${cred_type}"
def cred_user = "${cred_user}"
def cred_pass = "${cred_pass}"
def cred_id   = "${cred_id}"
def cred_text = "${cred_text}"
def cred_filepath = "${cred_filepath}"
def cred_filename = "${cred_filename}"
def cred_state = "${cred_state}"

def changes = ""

def credentials
def credential_to_store
def stored_cred

def instance = Jenkins.getInstance()

// Handle empty string to null conversion
if (project == "" ) { project = null }
if (cred_id == "" ) { cred_id = null }

def folder_cred_property
def folder
def abstract_folder
def cred_store
def domain
def domain_creds

def credential_scope

// This is designed to find the right cred_store, wether it is global or in a project
if (project) {
  folder = instance.getAllItems(OrganizationFolder.class).find { it.name.toUpperCase() == project.toUpperCase() }

  if (!folder) {
    // Only a folder can have creds added, duck out if there has been a problem
    return null
  }

  abstract_folder = AbstractFolder.class.cast(folder)
  folder_cred_property = abstract_folder.getProperties().get(FolderCredentialsProperty.class)

  if (folder_cred_property) {
    cred_store = folder_cred_property.getStore()
    domain_creds = folder_cred_property.getDomainCredentials()
    // We use a global domain within the project, rather than limiting further
    proj_domain_creds = domain_creds.find { it.getDomain().isGlobal() }

    if (proj_domain_creds) {
      domain = proj_domain_creds.getDomain()
    }
    credential_scope = cred_store.getScopes()[0]
  }

} else {
  // not in a project ... these are global creds
  domain = Domain.global()
  cred_store = SystemCredentialsProvider.getInstance()
  credential_scope = CredentialsScope.GLOBAL
}

if (cred_store) {
  credentials = cred_store.getCredentials(domain)
}

// for projects we always specify the cred_id which internally in jenkins has to be unique
// if this is provided then use that to lookup the credential instead to avoid "fake" uniqueness
// being required for the description.
if (credentials) {
  if (cred_id) {
    stored_cred = credentials.find { it.getId() == cred_id }
  } else {
    stored_cred = credentials.find { it.getDescription() == cred_desc }
  }
}

if ((stored_cred) && (cred_state == "present")) {
  // credential exists so no need to add it, just return the empty string
  return changes
}

// if type is file but the filename is not set it's just a check so report back if it's missing
if (!(stored_cred) && (cred_type == "file") && (cred_filename == "") && (cred_state == "present")) {
  changes = "file cred missing"
  return changes
}

if (!(stored_cred) && (cred_state == "absent")) {
  // credential does not exists, but that's what we want so just return
  return changes
}


// By this point either the credential exists, but it shouldn't or it doesn't and it should
if (cred_state == "absent") {

  def cred_removed = cred_store.removeCredentials(domain, stored_cred)

  if (cred_removed) {
    changes = "removed credential"
    instance.save()
  }

  return changes
}

// if state is not present at this point then it's neither absent nor present so break
if (cred_state != "present") {
  throw new Exception("cred_state must be present or absent")
} else {

  // need to add the folder object if it doesn't exist already
  if ((project) && (!folder_cred_property)) {

    // a null domain will result in a global one
    domain = new Domain(null,null,null)
    domain_creds = new DomainCredentials(domain,null)
    folder_cred_property = new FolderCredentialsProperty(domain_creds)
    abstract_folder.addProperty(folder_cred_property)
    cred_store = folder_cred_property.getStore()
    credential_scope = cred_store.getScopes()[0]

  }

  // finally! It's missing and we need to add it
  switch(cred_type){
    case "userpass":
    credential_to_store = new UsernamePasswordCredentialsImpl(
      credential_scope,
      cred_id,
      cred_desc,
      cred_user,
      cred_pass,
    )
    break
    case "secret":
    credential_to_store = new StringCredentialsImpl(
      credential_scope,
      cred_id,
      cred_desc,
      Secret.fromString(cred_text)
    )
    break
    case "file":
    credential_to_store = new FileCredentialsImpl(
      credential_scope,
      cred_id,
      cred_desc,
      cred_filename,
      new SecretBytes(false, Files.readAllBytes(new File(cred_filepath + "/" + cred_filename).toPath()))
    )
    break
    default:
    break
  }

  if(credential_to_store) {

    cred_store.addCredentials(domain, credential_to_store)

    instance.save()

    changes = "added credential"
  }

  return changes
}
