@Library('company-library@main') _

pipeline {

    agent {
        kubernetes {
            yaml '''
apiVersion: v1
kind: Pod
spec:
  containers:

    # Kompilowanie aplikacji
    - name: maven
      image: maven:3.9.9-eclipse-temurin-21
      command:
        - cat
      tty: true

    # Budowanie i wysyłanie obrazu
    - name: kaniko
      image: gcr.io/kaniko-project/executor:v1.23.2-debug
      command:
        - /busybox/cat
      tty: true

    # Edycja manifestu YAML w repozytorium
    - name: yq
      image: mikefarah/yq:4.44.3
      command:
        - /bin/sh
        - -c
        - cat
      tty: true

    # Commitowanie i wysyłanie zmian do repozytorium
    - name: git
      image: alpine:3.20
      command:
        - cat
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

        /*
         * Jeśli app-deploy jest katalogiem, wpisz tutaj konkretny plik,
         * na przykład:
         *
         * k8s/app-deploy/deployment.yaml
         */
        MANIFEST_PATH = 'k8s/app-deploy.yaml'

        CONTAINER_NAME = 'app-legacy'

        /*
         * Gałąź, na którą Jenkins wypchnie zmianę manifestu.
         */
        TARGET_BRANCH = 'main'

        /*
         * Credential typu "SSH Username with private key".
         */
        GIT_SSH_CREDENTIALS_ID = 'github-ssh'
    }

    stages {

        stage('Print Parameters') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'gcp-sa',
                        usernameVariable: 'GCP_USER',
                        passwordVariable: 'GCP_PASS'
                    )
                ]) {
                    sh '''
                        set +x

                        echo "GCP user: ${GCP_USER}"

                        if [ -n "${GCP_PASS}" ]; then
                            echo "GCP password został poprawnie wczytany"
                        else
                            echo "Nie udało się wczytać GCP password"
                            exit 1
                        fi
                    '''
                }

                echo "Message: ${params.Message}"
                echo "Environment: ${params.ENVIRONMENT}"
                echo "PARAM1: ${params.PARAM1}"
            }
        }

        stage('Build') {
            steps {
                container('maven') {
                    dir('spring-mvc-demo') {
                        sh '''
                            set -eu
                            mvn clean package
                        '''
                    }
                }
            }
        }

        stage('Build Image') {
            steps {
                /*
                 * Funkcja pochodzi z:
                 *
                 * @Library('company-library@main')
                 *
                 * Zakładamy, że funkcja buduje obraz przez Kaniko
                 * i wysyła go do Artifact Registry.
                 */
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

                        NEW_IMAGE="${IMAGE_NAME}:${BUILD_NUMBER}"
                        export NEW_IMAGE
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
                                    .spec.template.spec.containers[]
                                    | select(.name == strenv(CONTAINER_NAME))
                                ]
                                | length
                            ' "${MANIFEST_PATH}"
                        )"

                        if [ "${MATCHING_CONTAINERS}" -eq 0 ]; then
                            echo "BŁĄD: W manifeście nie znaleziono kontenera ${CONTAINER_NAME}"
                            exit 1
                        fi

                        if [ "${MATCHING_CONTAINERS}" -gt 1 ]; then
                            echo "BŁĄD: Znaleziono więcej niż jeden kontener ${CONTAINER_NAME}"
                            exit 1
                        fi

                        yq -i '
                            (
                                .spec.template.spec.containers[]
                                | select(.name == strenv(CONTAINER_NAME))
                                | .image
                            ) = strenv(NEW_IMAGE)
                        ' "${MANIFEST_PATH}"

                        UPDATED_IMAGE="$(
                            yq -r '
                                .spec.template.spec.containers[]
                                | select(.name == strenv(CONTAINER_NAME))
                                | .image
                            ' "${MANIFEST_PATH}"
                        )"

                        if [ "${UPDATED_IMAGE}" != "${NEW_IMAGE}" ]; then
                            echo "BŁĄD: Weryfikacja zmiany obrazu nie powiodła się"
                            echo "Oczekiwano: ${NEW_IMAGE}"
                            echo "Odczytano: ${UPDATED_IMAGE}"
                            exit 1
                        fi

                        echo "Manifest został poprawnie zmieniony"
                        echo "Aktualny obraz: ${UPDATED_IMAGE}"
                    '''
                }
            }
        }

        stage('Commit and Push Manifest') {
            steps {
                container('git') {
                    sshagent(credentials: [env.GIT_SSH_CREDENTIALS_ID]) {
                        sh '''
                            set -eu

                            apk add --no-cache git openssh-client

                            git config user.name "Jenkins"
                            git config user.email "jenkins@localhost"

                            echo "Zmiany w manifeście:"
                            git diff -- "${MANIFEST_PATH}"

                            if git diff --quiet -- "${MANIFEST_PATH}"; then
                                echo "Manifest nie zawiera zmian"
                                echo "Commit i push zostają pominięte"
                                exit 0
                            fi

                            git add "${MANIFEST_PATH}"

                            git commit \
                                -m "Update ${CONTAINER_NAME} image to ${IMAGE_NAME}:${BUILD_NUMBER} [skip ci]"

                            git push origin "HEAD:${TARGET_BRANCH}"

                            echo "Zmiana została wypchnięta do repozytorium"
                            echo "Argo CD może teraz zsynchronizować manifest z klastrem"
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
            echo 'Manifest został zaktualizowany w repozytorium.'
        }

        failure {
            echo 'Pipeline zakończył się błędem.'
        }

        always {
            echo "Wybrane środowisko: ${params.ENVIRONMENT}"
        }
    }
}
