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
        stage('Static Test'){
            steps{
                sh 'flake8 --version'

                sh 'flake8 --exit-zero --format=pylint src > flake8-report.out'
                sh 'bandit --exit-zero -r src -f custom -o bandit-report.out --msg-template "{abspath}:{line}: [{test_id}] {msg}"'
                archiveArtifacts artifacts: '*.out', allowEmptyArchive: true
            }
            
        }
            
            
        stage('Deploy'){
            steps{
                sh '''
                sam --version
                sam build --config-env staging
                sam validate --region us-east-1
                '''
                //La siguiente linea se usa pra no tener que eliminar el stack cada vez que se va a ejecutar el pipeline
                catchError(buildResult: 'UNSTABLE', stageResult: 'SUCCESS'){
                    sh 'sam deploy --config-env staging --force-upload'
                }
                //sh 'sam deploy --config-env staging --force-upload'
            }
        }
        stage('Rest Test'){
            steps{
                //Configurar la variable de entorno para que pueda se utilizada por las pruebas y ejecutar pruebas con pytest
                sh '''
                    pytest --version
                    SERVICE_BASE_URL=$(aws cloudformation describe-stacks --stack-name todo-list-aws-staging \
                    --query "Stacks[0].Outputs[?OutputKey==\'BaseUrlApi\'].OutputValue" --output text)

 
                    if [ -z "$SERVICE_BASE_URL" ]; then
                    echo "Error: No se pudo obtener la URL de la API."
                    exit 1
                    fi
                    
                    # Ejecutar pytest especificando la variable de entorno directamente
                    SERVICE_BASE_URL=$SERVICE_BASE_URL pytest test/integration/todoApiTest.py --junitxml=pytest-report.xml
                '''
            }
                
        }
        stage('Promote'){
            steps{
                //Validación de que el branch donde se opera es el correcto
                sh 'git branch'
                    
                // Proceso de merge
                withCredentials([usernamePassword(credentialsId: 'PAT-GitHub-jm', usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')]) {
                    sh 'git config user.name "$GIT_USERNAME"'
                    sh 'git config user.email "jmpp2k8@gmail.com"'
                    // Cambio a rama master
                    sh 'git checkout master'
                    // Merge de la rama develop en master
                    sh 'git merge develop'
                    // Actualizar el repositorio mediante un push
                    sh 'git push https://$GIT_USERNAME:$GIT_PASSWORD@github.com/$GIT_USERNAME/cp1-4-todo-list-aws.git HEAD:master'
                }
            }
        }
        
    }
}