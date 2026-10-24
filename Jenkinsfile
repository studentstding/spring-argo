@Library('company-library@main') _

pipeline {

    agent {
        kubernetes {
            yaml '''
apiVersion: v1
kind: Pod
spec:
  containers:

    - name: maven
      image: maven:3.9.9-eclipse-temurin-21
      command:
        - cat
      tty: true

    - name: kaniko
      image: gcr.io/kaniko-project/executor:v1.23.2-debug
      command:
        - /busybox/cat
      tty: true

    - name: yq
      image: mikefarah/yq:4.44.3
      command:
        - /bin/sh
        - -c
        - cat
      tty: true

    - name: git
      image: alpine:3.20
      command:
        - /bin/sh
        - -c
        - apk add --no-cache git openssh-client && cat
      tty: true
'''
        }
    }

    parameters {
        text(
            name: 'Message',
            defaultValue: 'message default',
            description: 'Wiadomość przekazywana do pipeline'
        )

        choice(
            name: 'ENVIRONMENT',
            choices: ['dev', 'prod'],
            description: 'Gdzie wdrożyć aplikację'
        )

        string(
            name: 'PARAM1',
            defaultValue: 'default val',
            description: 'Przykładowy parametr'
        )
    }

    environment {
        IMAGE_NAME = 'europe-central2-docker.pkg.dev/mr-dev12/docker-repo/spring-demo'

        MANIFEST_PATH = 'k8s/app-deploy.yaml'
        CONTAINER_NAME = 'app-legacy'

        TARGET_BRANCH = 'main'

        GIT_SSH_URL = 'git@github.com:studentstding/spring-argo.git'
        GIT_SSH_CREDENTIALS_ID = 'github-ssh'
    }

    stages {

        stage('Print Parameters') {
            steps {
                echo "Message: ${params.Message}"
                echo "Environment: ${params.ENVIRONMENT}"
                echo "PARAM1: ${params.PARAM1}"
            }
        }

        stage('Build') {
            steps {
                container('maven') {
                    sh '''
                        set -eu

                        echo "Katalog roboczy:"
                        pwd

                        echo "Budowanie aplikacji:"
                        mvn clean package
                    '''
                }
