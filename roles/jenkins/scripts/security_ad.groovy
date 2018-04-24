import hudson.security.*
import hudson.util.Secret
import hudson.plugins.active_directory.*
import groovy.json.JsonSlurper

changes = ""

def domain_json_as_text = '''
${domains}
'''

def domain_configs = new JsonSlurper().parseText(domain_json_as_text)

def instance = Jenkins.getInstance()
def realm = instance.getSecurityRealm()

if (!(realm instanceof ActiveDirectorySecurityRealm)) {
  changes = "changing security"
} else {
  def domains = realm.getDomains()
  if (domains.size() != domain_configs.size()) {
    changes = "changing security"
  } else {
      domain_configs.each { domain_req ->
        // internally the server is stored with the port
        def domain_check = domains.find { it.name.equals(domain_req.name) && it.servers.equals(domain_req.host + ":3268") }
        if (!domain_check) {
          // don't care which is missing as we'll reset them all
          changes = "changing_security"
        }
      }
  }
}

if (changes == "") {
  // no changes so just return
  return changes
}

def domain = null
def domains = []
def site = null
def bindName = null
def bindPassword = null
def server = null
def groupLookupStrategy = GroupLookupStrategy.AUTO
def removeIrrelevantGroups = false
def customDomain = true
def cache = new CacheConfiguration(100, 86400)
def starttls = false

domain_configs.each {
  domains.push(new ActiveDirectoryDomain(it.name, it.host))
}

def hudsonRealm = new ActiveDirectorySecurityRealm(
  domain,
  domains,
  site,
  bindName,
  bindPassword,
  server,
  groupLookupStrategy,
  removeIrrelevantGroups,
  customDomain,
  cache,
  starttls
)
instance.setSecurityRealm(hudsonRealm)
instance.save()

return changes
