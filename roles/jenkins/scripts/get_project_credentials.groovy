import jenkins.branch.OrganizationFolder
import com.cloudbees.hudson.plugins.folder.*;
import com.cloudbees.hudson.plugins.folder.properties.*;
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider.FolderCredentialsProperty;
import groovy.json.JsonOutput


def instance = Jenkins.getInstance()

def ofs = instance.getAllItems(OrganizationFolder)

def projects = ofs.findAll { it.getNavigators() }

def cred_map = [:]

projects.each { project ->
  def cred_collection = [:]



  abstract_folder = AbstractFolder.class.cast(project)
  folder_cred_property = abstract_folder.getProperties().get(FolderCredentialsProperty.class)

   if (folder_cred_property) {
    def dom_credentials = [:]
    cred_store = folder_cred_property.getStore()
    domain_creds = folder_cred_property.getDomainCredentials()

    domain_creds.each { domain ->
      dom_name = domain.getDomain().name
      if (!dom_name) {
        dom_name = 'GLOBAL'
      }
      dom_credentials[dom_name] = cred_store.getCredentials(domain.getDomain())
      cred_collection[dom_name] = [:]
    }

    dom_credentials.each { dom_name, credentials ->

      cred_collection[dom_name]['userpass'] = []
      cred_collection[dom_name]['secrettext'] = []
      cred_collection[dom_name]['secretfile'] = []

      credentials.each { credential ->

        def cred = [:]
        if (credential instanceof com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl) {
          cred['cred_user'] = credential.username
          cred['cred_desc'] =  credential.description
          cred['cred_pass'] = credential.password.plainText
          cred['cred_id'] = credential.id
          cred_collection[dom_name]['userpass'].add(cred)
          return
        }

        if (credential instanceof org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl) {
           cred['cred_desc'] = credential.description
           cred['cred_id'] = credential.id
           cred['cred_text'] = credential.secret.plainText
           cred_collection[dom_name]['secrettext'].add(cred)
          return
        }

        if (credential instanceof org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl) {
         	cred['cred_desc'] = credential.description
          	cred['cred_id'] = credential.id
          	cred['cred_filename'] = credential.fileName
          	cred_collection[dom_name]['secretfile'].add(cred)
          	return
        }

      }

  }

}

     cred_map[project.name] = cred_collection

}

return JsonOutput.toJson(cred_map)
