import hudson.security.*
import jenkins.security.*
import hudson.util.Secret
import hudson.model.*
import jenkins.model.Jenkins
import jenkins.branch.OrganizationFolder
import com.cloudbees.plugins.credentials.*
import groovy.json.JsonSlurper
import hudson.security.Permission

def changes = ""

def instance = Jenkins.getInstance()

def ofs = instance.getAllItems(OrganizationFolder)

def project_name = "${project_name}"

def projects = ofs.findAll { it.getNavigators() }

def project = projects.find { it.name == project_name }


def permission_tiers = [
  "admins": [
    hudson.model.Item.CONFIGURE,
    hudson.model.Computer.BUILD,
    hudson.model.Hudson.READ,
    CredentialsProvider.VIEW,
    hudson.model.Item.BUILD,
    hudson.model.Item.CANCEL,
    hudson.model.Item.DISCOVER,
    hudson.model.Item.CONFIGURE,
    hudson.model.Item.READ,
    hudson.model.Item.WORKSPACE,
    hudson.model.View.READ,
    org.jenkinsci.plugins.workflow.cps.replay.ReplayAction.REPLAY
  ],
  "users" : [
    hudson.model.Computer.BUILD,
    hudson.model.Hudson.READ,
    hudson.model.Item.BUILD,
    hudson.model.Item.CANCEL,
    hudson.model.Item.DISCOVER,
    hudson.model.Item.READ,
    hudson.model.View.READ,
    org.jenkinsci.plugins.workflow.cps.replay.ReplayAction.REPLAY
  ],
  "restricteds": [
    hudson.model.Hudson.READ,
    hudson.model.Item.DISCOVER,
    hudson.model.Item.READ,
    hudson.model.View.READ,
    hudson.model.Item.BUILD,
  ]
]

def group_json = '''
${permissions}
'''

def project_auth_matrix  = project.getProperties().get(com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty)

def perm_groups = new JsonSlurper().parseText(group_json)

if (!perm_groups) {

  // no permissions defined so remove any folder level permissions

  if (project_auth_matrix) {

    project.getProperties().remove(com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty.class)
    project.save()

    changes = "changing project permissions"

  }

  return changes

}



if (project_auth_matrix) {

  def permissions = Permission.getAll()

  def sids = project_auth_matrix.getAllSIDs()

  def sids_checked = []

  def sids_required = []

  def sid_perms = [:]

  sids.each { sid ->

    def perm_list = []
    permissions.each { perm ->
      if (project_auth_matrix.hasExplicitPermission(sid, perm)) {
        perm_list.push(perm)
      }
    }

    sid_perms.putAt(sid, perm_list)
  }

  sid_perms.each { sid, perm_list ->

    def perm_okay = []

    perm_groups.each { type, user_list ->

      user_list.each { u ->
        sids_required.push(u)
      }

      if (sid in user_list) {

        permission_tiers[type].each { perm ->

          if (project_auth_matrix.hasExplicitPermission(sid, perm)) {
             perm_okay.push(perm)
          } else {
            changes = "changing project permissions"
          }
        }

      }

    }

    // need the inverse intersection of the lists to see if either has an additional permission
    if ((perm_okay + perm_list - perm_okay.intersect(perm_list)) != []) {
     changes = "changing project permissions"

    }

    sids_checked.push(sid)
  }

  // ensure that the list of sids in jenkins match the list in configuration
  if ((sids_checked + sids_required - sids_checked.intersect(sids_required)) != []) {
     changes = "changing project permissions"

    }
} else {
  // if there's no folder permissions yet then no need to compare them all!
  changes = "changing project permissions"
}

if (changes != "") {
  def auth = new com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty()

  permission_tiers.each { tier, permissions ->
    if (perm_groups.containsKey(tier)) {
      for (sid in perm_groups[tier]) {
        if (sid != "") {
            permissions.each { permission -> auth.add(permission, sid) }
          }
        }
      }
    }

  project.getProperties().remove(com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty.class)
  project.addProperty(auth)
  project.save()
}

return changes
