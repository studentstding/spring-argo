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
        IMAGE_TAG  = "${BUILD_NUMBER}"

        // Katalog z pom.xml, src/, lombok.config i Dockerfile (kontekst budowania obrazu)
        APP_DIR = 'spring-mvc-demo'

        // Credential typu "Secret file" z kluczem JSON konta serwisowego GCP
        // (rola: Artifact Registry Writer)
        GCP_SA_KEY_CREDENTIALS_ID = 'gcp-sa-key'

        MANIFEST_PATH  = 'k8s/app-deploy.yaml'
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

        stage('Test') {
            // Dockerfile buduje WAR z -DskipTests, więc testy uruchamiamy tutaj
            steps {
                container('maven') {
                    dir("${env.APP_DIR}") {
                        sh '''
                            set -eu
                            mvn -B test
                        '''
                    }
                }
            }
        }

        stage('Build & Push Image') {
            steps {
                container('kaniko') {
                    withCredentials([
                        file(
                            credentialsId: env.GCP_SA_KEY_CREDENTIALS_ID,
                            variable: 'GCP_SA_KEY'
                        )
                    ]) {
                        sh '''
                            set +x
                            set -eu

                            # Host rejestru, np. europe-central2-docker.pkg.dev
                            REGISTRY="${IMAGE_NAME%%/*}"

                            # Logowanie do Artifact Registry kluczem JSON (_json_key)
                            mkdir -p /kaniko/.docker
                            AUTH="$(printf '%s' "_json_key:$(cat "${GCP_SA_KEY}")" | base64 | tr -d '\\n')"
                            printf '{"auths":{"%s":{"auth":"%s"}}}' "${REGISTRY}" "${AUTH}" \\
                                > /kaniko/.docker/config.json

                            echo "Budowanie i push obrazu: ${IMAGE_NAME}:${IMAGE_TAG}"

                            /kaniko/executor \\
                                --context="dir://${WORKSPACE}/${APP_DIR}" \\
                                --dockerfile="${WORKSPACE}/${APP_DIR}/Dockerfile" \\
                                --destination="${IMAGE_NAME}:${IMAGE_TAG}" \\
                                --cache=true \\
                                --cache-repo="${IMAGE_NAME}/cache"

                            rm -f /kaniko/.docker/config.json
                            echo "Obraz wypchnięty: ${IMAGE_NAME}:${IMAGE_TAG}"
                        '''
                    }
                }
            }
        }

        stage('Update Image in Manifest') {
            steps {
                container('yq') {
                    sh '''
                        set -eu

                        export NEW_IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"
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
                                    | select(.name == strenv(CONTAINER_NAME))
                                ]
                                | length
                            ' "${MANIFEST_PATH}" \
                            | awk '{ total += $1 } END { print total + 0 }'
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
                                | select(.name == strenv(CONTAINER_NAME))
                                | .image
                            ) = strenv(NEW_IMAGE)
                        ' "${MANIFEST_PATH}"

                        UPDATED_IMAGE="$(
                            yq -r '
                                .spec.template.spec.containers[]?
                                | select(.name == strenv(CONTAINER_NAME))
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
                            set +x
                            set -eu

                            # Kontener git działa jako root, a workspace należy do użytkownika jenkins
                            git config --global --add safe.directory "${WORKSPACE}"

                            mkdir -p "${HOME}/.ssh"
                            chmod 700 "${HOME}/.ssh"

                            ssh-keyscan -H github.com > "${HOME}/.ssh/known_hosts"
                            chmod 600 "${HOME}/.ssh/known_hosts"
                            chmod 600 "${GIT_SSH_KEY}"

                            export GIT_SSH_COMMAND="ssh -i ${GIT_SSH_KEY} -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile=${HOME}/.ssh/known_hosts"

                            git config user.name "Jenkins"
                            git config user.email "jenkins@localhost"

                            echo "Zmiany w manifeście:"
                            git --no-pager diff -- "${MANIFEST_PATH}"

                            if git diff --quiet -- "${MANIFEST_PATH}"; then
                                echo "Brak zmian w manifeście"
                                echo "Commit i push zostają pominięte"
                                exit 0
                            fi

                            git add "${MANIFEST_PATH}"

                            git commit -m "Update ${CONTAINER_NAME} image to ${IMAGE_NAME}:${IMAGE_TAG} [skip ci]"

                            # Na wypadek, gdyby ktoś w międzyczasie wypchnął coś na gałąź docelową
                            git pull --rebase "${GIT_SSH_URL}" "${TARGET_BRANCH}"

                            git push "${GIT_SSH_URL}" "HEAD:${TARGET_BRANCH}"

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
            echo "Zbudowany obraz: ${env.IMAGE_NAME}:${env.IMAGE_TAG}"
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
