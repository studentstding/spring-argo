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
            }
        }

        stage('Verify JAR') {
            steps {
                container('maven') {
                    sh '''
                        set -eu

                        echo "Pliki w katalogu target:"
                        find target \
                            -maxdepth 1 \
                            -type f \
                            -name "*.jar" \
                            -print

                        JAR_COUNT="$(
                            find target \
                                -maxdepth 1 \
                                -type f \
                                -name "*.jar" \
                                ! -name "original-*" \
                                | wc -l
                        )"

                        echo "Liczba właściwych plików JAR: ${JAR_COUNT}"

                        if [ "${JAR_COUNT}" -eq 0 ]; then
                            echo "BŁĄD: Maven nie utworzył pliku JAR w target/"
                            exit 1
                        fi
                    '''
                }
            }
        }

        stage('Build Image') {
            steps {
                buildImage(
                    env.IMAGE_NAME,
                    env.BUILD_NUMBER
                )
            }
        }

        stage('Update Image in Manifest') {
            steps {
                container('yq') {
                    sh '''
                        set -eu

                        export NEW_IMAGE="${IMAGE_NAME}:${BUILD_NUMBER}"
                        export CONTAINER_NAME

                        echo "Plik manifestu: ${MANIFEST_PATH}"
                        echo "Nazwa kontenera: ${CONTAINER_NAME}"
                        echo "Nowy obraz: ${NEW_IMAGE}"

                        if [ ! -f "${MANIFEST_PATH}" ]; then
                            echo "BŁĄD: Nie znaleziono pliku ${MANIFEST_PATH}"
                            exit 1
                        fi

                        MATCHING_CONTAINERS="$(
                            yq '
                                [
                                    .spec.template.spec.containers[]?
                                    | select(
                                        .name == strenv(CONTAINER_NAME)
                                    )
                                ]
                                | length
                            ' "${MANIFEST_PATH}" |
                            awk '
                                {
                                    total += $1
                                }
                                END {
                                    print total + 0
                                }
                            '
                        )"

                        echo "Liczba znalezionych kontenerów: ${MATCHING_CONTAINERS}"

                        if [ "${MATCHING_CONTAINERS}" -eq 0 ]; then
                            echo "BŁĄD: Nie znaleziono kontenera ${CONTAINER_NAME}"
                            exit 1
                        fi

                        if [ "${MATCHING_CONTAINERS}" -gt 1 ]; then
                            echo "BŁĄD: Znaleziono więcej niż jeden kontener ${CONTAINER_NAME}"
                            exit 1
                        fi

                        yq -i '
                            (
                                .spec.template.spec.containers[]?
                                | select(
                                    .name == strenv(CONTAINER_NAME)
                                )
                                | .image
                            ) = strenv(NEW_IMAGE)
                        ' "${MANIFEST_PATH}"

                        UPDATED_IMAGE="$(
                            yq -r '
                                .spec.template.spec.containers[]?
                                | select(
                                    .name == strenv(CONTAINER_NAME)
                                )
                                | .image
                            ' "${MANIFEST_PATH}"
                        )"

                        echo "Obraz odczytany po zmianie: ${UPDATED_IMAGE}"

                        if [ "${UPDATED_IMAGE}" != "${NEW_IMAGE}" ]; then
                            echo "BŁĄD: Weryfikacja zmiany obrazu nie powiodła się"
                            echo "Oczekiwano: ${NEW_IMAGE}"
                            echo "Odczytano: ${UPDATED_IMAGE}"
                            exit 1
                        fi

                        echo "Manifest został poprawnie zmieniony"
                    '''
                }
            }
        }

        stage('Commit and Push Manifest') {
            steps {
                container('git') {
                    withCredentials([
                        sshUserPrivateKey(
                            credentialsId: env.GIT_SSH_CREDENTIALS_ID,
                            keyFileVariable: 'GIT_SSH_KEY',
                            usernameVariable: 'GIT_SSH_USERNAME'
                        )
                    ]) {
                        sh '''
                            set -eu
                            set +x

                            mkdir -p "${HOME}/.ssh"
                            chmod 700 "${HOME}/.ssh"

                            ssh-keyscan -H github.com \
                                > "${HOME}/.ssh/known_hosts"

                            chmod 600 "${HOME}/.ssh/known_hosts"
                            chmod 600 "${GIT_SSH_KEY}"

                            export GIT_SSH_COMMAND="ssh \
                                -i ${GIT_SSH_KEY} \
                                -o IdentitiesOnly=yes \
                                -o StrictHostKeyChecking=yes \
                                -o UserKnownHostsFile=${HOME}/.ssh/known_hosts"

                            git config user.name "Jenkins"
                            git config user.email "jenkins@localhost"

                            echo "Zmiany w manifeście:"
                            git diff -- "${MANIFEST_PATH}"

                            if git diff --quiet -- "${MANIFEST_PATH}"; then
                                echo "Brak zmian w manifeście"
                                echo "Commit i push zostają pominięte"
                                exit 0
                            fi

                            git add "${MANIFEST_PATH}"

                            git commit \
                                -m "Update ${CONTAINER_NAME} image to ${IMAGE_NAME}:${BUILD_NUMBER} [skip ci]"

                            git push \
                                "${GIT_SSH_URL}" \
                                "HEAD:${TARGET_BRANCH}"

                            echo "Manifest został wysłany do repozytorium"
                            echo "Synchronizację z klastrem wykona Argo CD"
                        '''
                    }
                }
            }
        }
    }

    post {
        success {
            echo 'Pipeline zakończył się poprawnie.'
            echo "Zbudowany obraz: ${env.IMAGE_NAME}:${env.BUILD_NUMBER}"
            echo "Zmieniony manifest: ${env.MANIFEST_PATH}"
        }

        failure {
            echo 'Pipeline zakończył się błędem.'
        }

        always {
            echo "Wybrane środowisko: ${params.ENVIRONMENT}"
        }
    }
}
