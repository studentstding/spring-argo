@Library('company-library@main') _

/*
 * Pipeline do joba typu Multibranch Pipeline (GitHub Branch Source):
 *   - Pull Request -> testy + próbny build obrazu (bez pushu)
 *   - main         -> testy + build i push obrazu + zmiana tagu w manifeście + commit (Argo CD wdraża)
 *   - inne gałęzie -> tylko testy
 * Działa też w zwykłym jobie Pipeline (wtedy gałąź jest brana z GIT_BRANCH).
 */

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

    options {
        // Dwa buildy tej samej gałęzi naraz kłóciłyby się przy pushu manifestu (kolejne czekają w kolejce)
        disableConcurrentBuilds()
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
        // IMAGE_TAG jest ustawiany w stage'u 'Prepare' jako <BUILD_NUMBER>-<krótki SHA>

        // Katalog z pom.xml, src/, lombok.config i Dockerfile (kontekst budowania obrazu)
        APP_DIR = 'spring-mvc-demo'

        // Credential typu "Secret file" z kluczem JSON konta serwisowego GCP
        // (rola: Artifact Registry Writer)
        GCP_SA_KEY_CREDENTIALS_ID = 'gcp-sa-key'

        MANIFEST_PATH  = 'k8s/app-deploy.yaml'
        CONTAINER_NAME = 'app-legacy'

        // Tylko ta gałąź buduje i wdraża obraz
        TARGET_BRANCH = 'main'

        GIT_SSH_URL = 'git@github.com:studentstding/spring-argo.git'
        GIT_SSH_CREDENTIALS_ID = 'github-ssh'

        // Tożsamość, z jaką Jenkins robi commity - po niej rozpoznajemy własne commity
        GIT_BOT_NAME  = 'Jenkins'
        GIT_BOT_EMAIL = 'jenkins@localhost'
    }

    stages {

        stage('Prepare') {
            steps {
                script {
                    def author   = sh(script: 'git log -1 --pretty=format:%ae', returnStdout: true).trim()
                    def message  = sh(script: 'git log -1 --pretty=format:%B', returnStdout: true).trim()
                    def shortSha = sh(script: 'git rev-parse --short=7 HEAD', returnStdout: true).trim()

                    // CHANGE_ID ustawia Multibranch tylko dla Pull Requestów
                    env.IS_PR = env.CHANGE_ID ? 'true' : 'false'

                    // Multibranch ustawia BRANCH_NAME; zwykły job Pipeline tylko GIT_BRANCH (np. origin/main)
                    def branch = env.BRANCH_NAME ?: (env.GIT_BRANCH ?: '').replaceFirst(/^origin\//, '')
                    env.IS_MAIN = (env.IS_PR == 'false' && branch == env.TARGET_BRANCH) ? 'true' : 'false'

                    // Unikalny i czytelny tag: numer builda + commit, z którego powstał obraz
                    env.IMAGE_TAG = "${env.BUILD_NUMBER}-${shortSha}"

                    // Bezpiecznik przed pętlą: commit Jenkinsa na main nie uruchamia ponownie CI/CD
                    if (env.IS_PR == 'false' && (author == env.GIT_BOT_EMAIL || message.contains('[skip ci]'))) {
                        env.SKIP_CI = 'true'
                        currentBuild.result = 'NOT_BUILT'
                        currentBuild.description = 'Pominięto: commit od Jenkinsa'
                        echo "Ostatni commit (${author}) pochodzi od Jenkinsa - pomijam build"
                    } else {
                        env.SKIP_CI = 'false'
                    }

                    if (env.IS_PR == 'true') {
                        currentBuild.description = "PR #${env.CHANGE_ID} -> ${env.CHANGE_TARGET}"
                    } else if (env.IS_MAIN == 'true' && env.SKIP_CI != 'true') {
                        currentBuild.description = "Obraz: ${env.IMAGE_TAG}"
                    }

                    echo "Gałąź: ${branch ?: '-'} | PR: ${env.IS_PR} | main: ${env.IS_MAIN} | tag: ${env.IMAGE_TAG}"
                }
            }
        }

        stage('CI/CD') {
            when {
                expression { env.SKIP_CI != 'true' }
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
                    // Zawsze: PR-y, gałęzie i main. Dockerfile buduje WAR z -DskipTests, więc testy są tutaj.
                    steps {
                        container('maven') {
                            dir("${env.APP_DIR}") {
                                sh '''
                                    set -eu
                                    mvn -B -ntp test
                                '''
                            }
                        }
                    }
                    post {
                        always {
                            // Wyniki testów widoczne w Jenkinsie i w statusie PR-a
                            junit(
                                testResults: "${env.APP_DIR}/target/surefire-reports/*.xml",
                                allowEmptyResults: true
                            )
                        }
                    }
                }

                stage('Verify Docker Build') {
                    // PR: sprawdzamy, czy obraz się buduje, ale nic nie wypychamy
                    when {
                        expression { env.IS_PR == 'true' }
                    }
                    steps {
                        container('kaniko') {
                            sh '''
                                set -eu

                                echo "Próbny build obrazu dla PR (bez pushu)"

                                /kaniko/executor \\
                                    --context="dir://${WORKSPACE}/${APP_DIR}" \\
                                    --dockerfile="${WORKSPACE}/${APP_DIR}/Dockerfile" \\
                                    --no-push
                            '''
                        }
                    }
                }

                stage('Build & Push Image') {
                    when {
                        expression { env.IS_MAIN == 'true' }
                    }
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
                    when {
                        expression { env.IS_MAIN == 'true' }
                    }
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

                                # Tylko dokumenty kind: Deployment - plik zawiera też Namespace, ServiceAccount itd.
                                MATCHING_CONTAINERS="$(
                                    yq '
                                        select(.kind == "Deployment")
                                        | [
                                            .spec.template.spec.containers[]
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

                                # "with" zmienia tylko istniejące węzły - nie tworzy ścieżek w innych dokumentach
                                yq -i '
                                    with(
                                        select(.kind == "Deployment")
                                        | .spec.template.spec.containers[]
                                        | select(.name == strenv(CONTAINER_NAME));
                                        .image = strenv(NEW_IMAGE)
                                    )
                                ' "${MANIFEST_PATH}"

                                UPDATED_IMAGE="$(
                                    yq -r '
                                        select(.kind == "Deployment")
                                        | .spec.template.spec.containers[]
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
                    when {
                        expression { env.IS_MAIN == 'true' }
                    }
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

                                    git config user.name  "${GIT_BOT_NAME}"
                                    git config user.email "${GIT_BOT_EMAIL}"

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
        }
    }

    post {
        success {
            script {
                if (env.IS_MAIN == 'true') {
                    echo "Wdrożono obraz: ${env.IMAGE_NAME}:${env.IMAGE_TAG} (manifest: ${env.MANIFEST_PATH})"
                } else if (env.IS_PR == 'true') {
                    echo "PR #${env.CHANGE_ID}: testy i próbny build obrazu przeszły."
                } else {
                    echo 'Testy przeszły.'
                }
            }
        }

        notBuilt {
            echo 'Build pominięty - commit pochodził od Jenkinsa.'
        }

        failure {
            echo 'Pipeline zakończył się błędem.'
        }
    }
}
