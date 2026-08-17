pipeline {
    agent any
    options {
        timestamps()
        disableConcurrentBuilds()
    }
    stages {
        stage('Backend unit tests') {
            steps {
                dir('backend') {
                    sh './mvnw -B test'
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'backend/target/surefire-reports/*.xml'
                    archiveArtifacts artifacts: 'backend/target/site/jacoco/**', allowEmptyArchive: true
                }
            }
        }
        stage('Frontend unit tests') {
            steps {
                dir('frontend') {
                    sh 'npm ci'
                    sh 'npm test -- --coverage'
                    sh 'npm run lint'
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'frontend/coverage/**', allowEmptyArchive: true
                }
            }
        }
        stage('Docker images') {
            steps {
                sh 'docker build -t fair-ticketing-backend:${BUILD_NUMBER:-local} backend'
                sh 'docker build -t fair-ticketing-frontend:${BUILD_NUMBER:-local} frontend'
            }
        }
    }
}
