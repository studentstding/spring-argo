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

    # Budowanie i wysyłanie obrazu do Artifact Registry
    - name: kaniko
      image: gcr.io/kaniko-project/executor:v1.23.2-debug
      command:
        - /busybox/cat
      tty: true

    # Edycja manifestu Kubernetes w repozytorium
    - name: yq
      image: mikefarah/yq:4.44.3
      command:
        - /bin/sh
        - -c
        - cat
      tty: true

    # Commit oraz push zmienionego manifestu
    - name: git
      image: alpine/git:2.47.2
      command:
        - /bin/sh
        - -c
        - apk add --no-cache openssh-client && cat
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

        /*
         * Adres SSH repozytorium.
         *
         * Checkout był wykonany przez HTTPS, ale push wykonujemy przez SSH,
         * dlatego jawnie podajemy adres SSH.
         */
        GIT_SSH_URL = 'git@github.com:studentstding/spring-argo.git'

        /*
         * Jenkins credential:
         *
         * Kind: SSH Username with private key
         * ID: github-ssh
         * Username: git
         */
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

                        echo "Katalog budowania:"
                        pwd

                        echo "Uruchamiam Maven"
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

                        echo "Pliki JAR utworzone przez Maven:"
                        find . -type f -path "*/target/*.jar" -print

                        JAR_COUNT="$(
                            find . -type f \
                                -path "*/target/*.jar" \
                                ! -name "original-*" \
                                | wc -l
                        )"

                        if [ "${JAR_COUNT}" -eq 0 ]; then
                            echo "BŁĄD: Maven nie utworzył pliku JAR"
                            exit 1
                        fi
                    '''
                }
            }
        }

        stage('Build Image') {
            steps {
                /*
                 * buildImage pochodzi z:
                 *
                 * @Library('company-library@main')
                 *
                 * Funkcja korzysta z credentiala gcp-sa-key,
                 * ustawia GOOGLE_APPLICATION_CREDENTIALS
                 * i uruchamia Kaniko.
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

                        export NEW_IMAGE="${IMAGE_NAME}:${BUILD_NUMBER}"
                        export CONTAINER_NAME

                        echo "Plik manifestu: ${MANIFEST_PATH}"
                        echo "Nazwa kontenera: ${CONTAINER_NAME}"
                        echo "Nowy obraz: ${NEW_IMAGE}"

                        if [ ! -f "${MANIFEST_PATH}" ]; then
                            echo "BŁĄD: Nie znaleziono pliku ${MANIFEST_PATH}"
                            exit 1
                        fi

                        /*
                         * Manifest zawiera kilka dokumentów YAML.
                         *
                         * yq zwraca osobny wynik dla każdego dokumentu,
                         * np. 0, 0, 1. awk sumuje te wyniki do jednej liczby.
                         *
                         * Operator []? pomija dokumenty, które nie mają
                         * ścieżki spec.template.spec.containers.
                         */
                        MATCHING_CONTAINERS="$(
                            yq '
                                [
                                    .spec.template.spec.containers[]?
                                    | select(
                                        .name == strenv(CONTAINER_NAME)
                                    )
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

                        /*
                         * Zmiana jest wykonywana tylko w lokalnym pliku.
                         * Nie ma tutaj żadnego połączenia z klastrem.
                         */
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
                    withCredentials([
                        sshUserPrivateKey(
                            credentialsId: env.GIT_SSH_CREDENTIALS_ID,
                            keyFileVariable: 'GIT_SSH_KEY',
                            usernameVariable: 'GIT_SSH_USERNAME'
                        )
                    ]) {
                        sh '''
                            set -eu

                            mkdir -p "${HOME}/.ssh"
                            chmod 700 "${HOME}/.ssh"

                            /*
                             * Dodajemy klucz hosta GitHub, żeby SSH nie czekało
                             * na interaktywne potwierdzenie.
                             */
                            ssh-keyscan -H github.com \
                                > "${HOME}/.ssh/known_hosts"

                            chmod 600 "${HOME}/.ssh/known_hosts"
                            chmod 600 "${GIT_SSH_KEY}"

                            /*
                             * Git użyje klucza przekazanego przez Jenkins.
                             */
                            export GIT_SSH_COMMAND="ssh \
                                -i ${GIT_SSH_KEY} \
                                -o IdentitiesOnly=yes \
                                -o UserKnownHostsFile=${HOME}/.ssh/known_hosts"

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

                            /*
                             * Nie zmieniamy origin, ponieważ checkout został
                             * wykonany przez HTTPS.
                             *
                             * Push wykonujemy bezpośrednio na adres SSH.
                             */
                            git push \
                                "${GIT_SSH_URL}" \
                                "HEAD:${TARGET_BRANCH}"

                            echo "Manifest został wysłany do repozytorium"
                            echo "Dalszą synchronizację wykonuje Argo CD"
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
