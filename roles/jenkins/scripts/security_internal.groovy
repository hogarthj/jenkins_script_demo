import hudson.security.*


def changes = ""

def instance = Jenkins.getInstance()
def strategy = instance.getAuthorizationStrategy()

def realm = instance.getSecurityRealm()

if (!(realm instanceof HudsonPrivateSecurityRealm)) {
  changes = "changing security"
}

if (!(strategy instanceof ProjectMatrixAuthorizationStrategy)) {
  changes = "changing security"
}

if (changes != "" ) {
  def hudsonRealm = new HudsonPrivateSecurityRealm(false)
  hudsonRealm.createAccount('${admin_user}','${admin_pass}')
  instance.setSecurityRealm(hudsonRealm)
  strategy = new hudson.security.ProjectMatrixAuthorizationStrategy()
  strategy.add(Jenkins.ADMINISTER, '${admin_user}')
  instance.setAuthorizationStrategy(strategy)
  instance.save()
}

return changes
