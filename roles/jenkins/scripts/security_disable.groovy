import hudson.security.*
def instance = Jenkins.getInstance()
instance.setAuthorizationStrategy()
instance.setSecurityRealm()
instance.save()
