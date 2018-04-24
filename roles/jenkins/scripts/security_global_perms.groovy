import hudson.security.Permission
import com.cloudbees.plugins.credentials.*
import groovy.json.JsonSlurper


def instance = Jenkins.getInstance()
def strategy = instance.getAuthorizationStrategy()

def changes = ""

def sid_perms = [:]

def permission_tiers = [
  "admins": [
    Jenkins.ADMINISTER
  ],
  "users" : [
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
  "authenticated" : [
    hudson.model.Hudson.READ,
    hudson.model.Item.DISCOVER,
    hudson.model.Item.READ,
    hudson.model.View.READ
  ],
  "anonymous" : [
    org.jenkinsci.plugins.badge.PublicBadgeAction.VIEW_STATUS
  ]
]

def permissions_json = '''
${permissions}
'''

def perm_groups = new JsonSlurper().parseText(permissions_json)

perm_groups.putAt("authenticated", ["authenticated"])
perm_groups.putAt("anonymous", ["anonymous"])

// only do the comparisons if the existing strategy type is matrix
if(strategy instanceof hudson.security.ProjectMatrixAuthorizationStrategy) {

  def permissions = Permission.getAll()

  // anonymous is skipped in getAllSIDs so add it to the list
  def sids = strategy.getAllSIDs() + "anonymous"

  def sids_checked = []

  def sids_required = []

  sids.each { sid ->

    def perm_list = []
    permissions.each { perm ->
      if (strategy.hasExplicitPermission(sid, perm)) {
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

          if (strategy.hasExplicitPermission(sid, perm)) {
             perm_okay.push(perm)
          } else {
            changes = "changing perms"
          }
        }

      }

    }

    // need the inverse intersection of the lists to see if either has an additional permission
    if ((perm_okay + perm_list - perm_okay.intersect(perm_list)) != []) {
     changes = "changing perms"

    }

    sids_checked.push(sid)
  }

  // ensure that the list of sids in jenkins match the list in configuration
  if ((sids_checked + sids_required - sids_checked.intersect(sids_required)) != []) {
     changes = "changing perms"

    }


} else {

  // if it's not matrix then we need to set everything
  changes = "changing perms"
}

if (changes != "") {

  strategy = new hudson.security.ProjectMatrixAuthorizationStrategy()

  permission_tiers.each { tier, permissions ->
    if (perm_groups.containsKey(tier)) {
      for (sid in perm_groups[tier]) {
        if (sid != "") {
            permissions.each { permission -> strategy.add(permission, sid) }
          }
        }
      }
    }

  instance.setAuthorizationStrategy(strategy)
  instance.save()
}

return changes

