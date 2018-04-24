import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.common.*
import com.cloudbees.plugins.credentials.domains.*
import com.cloudbees.jenkins.plugins.sshcredentials.impl.*

def instance = Jenkins.getInstance()
def global_domain = Domain.global()

def credentials_store = SystemCredentialsProvider.getInstance()

def node_cred = credentials_store.getCredentials(global_domain).find {
    if (it instanceof com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey) {
      it.getPrivateKeySource().privateKeyFile.contains(".ssh/slave_key")
    }
}

if (!node_cred) {

  credentials = new BasicSSHUserPrivateKey(
      CredentialsScope.SYSTEM,
      null,
      "jenkins",
      new BasicSSHUserPrivateKey.FileOnMasterPrivateKeySource(System.getProperty("user.home") + '/.ssh/slave_key'),
      "",
      "key for nodes"
      )
  credentials_store.addCredentials(global_domain, credentials)
  credentials_store.save()
  instance.save()

  return "changed node sshkey"
}
