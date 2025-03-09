pipeline {
    agent any

    
    stages {
        
        stage('GetCode') {
            steps {
                git branch: 'develop', url: 'https://github.com/jmpanper/cp1-4-todo-list-aws.git'
                sh 'ls -la'
                echo "WORKSPACE: ${WORKSPACE}"
            }
        }
        

        stage('Static Test') {
            agent { label 'static' }
            steps {
                sh 'flake8 --version'
                sh 'flake8 --exit-zero --format=pylint src > flake8-report.out'
                sh 'bandit --exit-zero -r src -f custom -o bandit-report.out --msg-template "{abspath}:{line}: [{test_id}] {msg}"'
                archiveArtifacts artifacts: '*.out', allowEmptyArchive: true
            }
        }
        

        stage('Deploy') {
            steps {
                sh '''
                    sam --version
                    sam build --config-env staging
                    sam validate --region us-east-1
                '''
                // Manejo de errores en el despliegue
                catchError(buildResult: 'UNSTABLE', stageResult: 'SUCCESS') {
                    sh 'sam deploy --config-env staging --force-upload'
                }

                sh '''
                    SERVICE_BASE_URL=$(aws cloudformation describe-stacks --stack-name todo-list-aws-staging \
                    --query "Stacks[0].Outputs[?OutputKey==\'BaseUrlApi\'].OutputValue" --output text)

                    if [ -z "$SERVICE_BASE_URL" ]; then
                        echo "Error: No se pudo obtener la URL de la API."
                        exit 1
                    fi
                    
                    touch /tmp/service_base_url_ci.txt
                    echo "${SERVICE_BASE_URL}" > /tmp/service_base_url_ci.txt

                '''

            }
        }
        

        stage('Rest Test') {
            agent { label 'integration' }
            steps {
                sh '''
                pytest --version
                export SERVICE_BASE_URL=$(cat /tmp/service_base_url_ci.txt | tr -d '[:space:]')
                echo "DEBUG: URL en ENV -> $SERVICE_BASE_URL"
                env | grep SERVICE_BASE_URL || echo "SERVICE_BASE_URL no está en env"
                pytest /home/ubuntu/cp1-4-todo-list-aws/test/integration/todoApiTest.py --junitxml=pytest-report.xml
            '''
            }
        }

        stage('Promote') {
            steps {
                sh 'git branch'
                
                withCredentials([usernamePassword(credentialsId: 'PAT-GitHub-jm', usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')]) {
                    sh 'git config user.name "$GIT_USERNAME"'
                    sh 'git config user.email "jmpp2k8@gmail.com"'
                    
                    sh 'git checkout master'
                    sh 'git merge develop'
                    
                    sh 'git push https://$GIT_USERNAME:$GIT_PASSWORD@github.com/$GIT_USERNAME/cp1-4-todo-list-aws.git HEAD:master'
                }
            }
        }
    }
}