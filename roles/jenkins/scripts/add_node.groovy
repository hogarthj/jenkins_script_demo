import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.common.*
import com.cloudbees.plugins.credentials.domains.*
import com.cloudbees.jenkins.plugins.sshcredentials.impl.*
import hudson.plugins.sshslaves.SSHLauncher
import hudson.plugins.sshslaves.verifiers.ManuallyTrustedKeyVerificationStrategy
import hudson.slaves.*

def instance = Jenkins.getInstance()
def launcher
def node_type = "${node_type}"
def node_name = "${node_name}"
def node_address = "${node_addr}"
def node_command = "${node_command}"
def node_workdir = "${node_workdir}"
def enableWorkDir = true

if (node_type == "ssh") {
  credentials = SystemCredentialsProvider.getInstance().getCredentials(Domain.global())
  node_cred = credentials.find {
    if (it instanceof com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey) {
      it.getPrivateKeySource().privateKeyFile.contains(".ssh/slave_key")
    }
  }

  def host = node_address
  def port = 22
  // the current constructor uses the id and not the credential object itself
  // since this is dynamically generated when jenkins is installed we need to look it up and then refer to it
  launcher = new SSHLauncher(host, port, node_cred.id, "", "", "", "", 60, 10, 5, new ManuallyTrustedKeyVerificationStrategy(false))
}
if (node_type == 'command') {
  launcher = new CommandLauncher(node_command + ' ' + node_address)
}
if (node_type == 'jnlp') {
  launcher = new JNLPLauncher(enableWorkDir)
}
def node = new DumbSlave(node_name, node_workdir, launcher)
instance.addNode(node)
instance.save()
