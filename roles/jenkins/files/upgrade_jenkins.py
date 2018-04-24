#!/usr/bin/env python
'''
This script updates a local jenkins instance. It assumes a repository is already configured
for both the rpm and for the plugins.

It updates all of these to the most recent versions.
'''

from __future__ import unicode_literals, print_function

from argparse import ArgumentParser

import os
import sys
import subprocess

from time import sleep
from datetime import datetime

import requests

class UpgradeJenkins(object): #pylint:disable=missing-docstring

    @staticmethod
    def log(message):
        print('[' + str(datetime.utcnow()) + '] ' + message)

    def config(self):
        '''
        Handle the self variables that are required
        '''
        parser = ArgumentParser()
        parser.add_argument('--username', '-u', dest='user',
                            help='User to talk to jenkins with')
        parser.add_argument('--password', '-p', dest='password',
                            help='Password to talk to jenkins with')

        self.args = parser.parse_args()

        if self.args.user is None:
            if 'JENKINS_USERNAME' in os.environ:
                self.args.user = os.environ['JENKINS_USERNAME']
            else:
                print("Needs either -u <user> or JENKINS_USERNAME environment variable")
                sys.exit(1)
        if self.args.password is None:
            if 'JENKINS_PASSWORD' in os.environ:
                self.args.password = os.environ['JENKINS_PASSWORD']
            else:
                print("Needs either -p <password> or JENKINS_PASSWORD environment variable")
                sys.exit(1)

        self.jenkins_auth = (self.args.user, self.args.password)

        self.jenkins_url = "http://127.0.0.1:8080/"

    def run_jenkins_script(self, script):
        '''
        Runs the groovy script provided and returns the results
        '''

        payload = {'script': script}

        resp = requests.post(self.jenkins_url + 'scriptText', data=payload, auth=self.jenkins_auth)

        result = resp.text.lstrip('Result:').strip()

        return result

    def maintenance_on_start(self, enabled):
        '''
        Enable or disable starting in maintenance mode
        '''

        init_file_path = '/var/lib/jenkins/init.groovy'

        if enabled is True:
            self.log("Enabling maintenance on start")
            with open(init_file_path, 'w') as init_file:
                init_file.write('import jenkins.model.*; Jenkins.instance.doQuietDown();')
            init_file.close()
            return True
        else:
            if os.path.isfile(init_file_path):
                self.log("Disabling maintenance on start")
                os.remove(init_file_path)
                return False


    def is_jenkins_ready(self):
        '''
        Loop until it is possible to log in to jenkins
        '''
        while True:
            try:
                resp = requests.get(self.jenkins_url + 'cli', auth=self.jenkins_auth)
                if resp.status_code == 200:
                    self.log("Jenkins ready")
                    break
            except requests.exceptions.ConnectionError:
                pass

            self.log("Waiting for jenkins to be ready")
            sleep(1)
        return True


    def upgrade_plugins(self):
        '''
        Handle the updates of any plugins detected
        '''
        # Ensure metadata is fresh
        self.log("Updating metadata")
        resp = requests.post(self.jenkins_url + '/pluginManager/checkUpdatesServer',
                             auth=self.jenkins_auth)
        if resp.status_code not in [200, 302]:
            return False

        self.log("Getting current plugin details")
        resp = requests.get(self.jenkins_url + '/pluginManager/api/json?depth=1',
                            auth=self.jenkins_auth)
        plugin_details = resp.json()

        for plugin in plugin_details['plugins']:
            if plugin['hasUpdate'] is True:
                self.log("Updating plugin " + plugin['shortName'])
                requests.post(self.jenkins_url + '/pluginManager/install?plugin.' +
                              plugin['shortName'] + '.default=on', auth=self.jenkins_auth)

        while True:
            install_pending = False
            resp = requests.get(self.jenkins_url + '/updateCenter/installStatus',
                                auth=self.jenkins_auth)
            install_status = resp.json()
            # If anything is pending, loop again
            for install_job in install_status['data']['jobs']:
                if install_job['installStatus'] == 'Pending':
                    self.log("Waiting for plugins to update")
                    install_pending = True
                    break
            # Wait 5 seconds between each check otherwise the log looks pretty crazy
            if install_pending is True:
                sleep(5)
            else:
                break

        # Only carry out a restart if the plugins indicate one is required
        resp = requests.get(self.jenkins_url +
                            '/updateCenter/api/json?tree=restartRequiredForCompletion',
                            auth=self.jenkins_auth)
        if resp.status_code in [200, 302]:
            restart_status = resp.json()
            if bool(restart_status['restartRequiredForCompletion']):
                resp = requests.post(self.jenkins_url + '/safeRestart', auth=self.jenkins_auth)
                if resp.status_code not in [200, 302]:
                    return False
        else:
            return False

        return True

    def __init__(self):
        self.config()

        self.log("Preparing for updates")
        self.run_jenkins_script('Jenkins.instance.doQuietDown();')

        self.maintenance_on_start(True)

        while True:
            if bool(self.run_jenkins_script('RestartListener.isAllReady();')):
                break
            sleep(1)

        self.log("Ready to update")
        # The yum update will restart the jenkins service
        subprocess.call(['yum', 'clean', 'metadata'])
        subprocess.check_call(['yum', '-y', 'update', 'jenkins'])

        self.is_jenkins_ready()

        update_plugins = self.upgrade_plugins()

        if update_plugins is False:
            self.log("Failed to update plugins")
            sys.exit(1)

        self.is_jenkins_ready()

        self.maintenance_on_start(False)

        self.run_jenkins_script('Jenkins.instance.doCancelQuietDown();')

if __name__ == "__main__":
    UpgradeJenkins()
