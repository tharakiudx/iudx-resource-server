properties([pipelineTriggers([githubPush()])])
pipeline {
  environment {
    devRegistry = 'dockerhub.iudx.io/jenkins/resource-dev'
    deplRegistry = 'dockerhub.iudx.io/jenkins/resource-depl'
    testRegistry = 'dockerhub.iudx.io/jenkins/resource-test'
    registryUri = 'https://dockerhub.iudx.io'
    registryCredential = 'docker-jenkins'
  }
  agent any
  stages {
    stage('Building images') {
      steps{
        node('master || slave1') {
        script {
          devImage = docker.build( devRegistry, "-f ./docker/dev.dockerfile .")
          deplImage = docker.build( deplRegistry, "-f ./docker/depl.dockerfile .")
          testImage = docker.build( testRegistry, "-f ./docker/test.dockerfile .")
        }
        }
      }
    }
    stage('Run Tests'){
      steps{
        script{
          sh '''
          set +x
          docker-compose up test
          '''
        }
      }
    }
    stage('Capture Test results'){
      steps{
        xunit (
                thresholds: [ skipped(failureThreshold: '3'), failed(failureThreshold: '4') ],
                tools: [ JUnit(pattern: 'target/surefire-reports/*Test.xml') ]
        )
      }
      post{
        failure{
          error "Test failure. Stopping pipeline execution!"
        }
      }
    }
    //stage ('test token generation'){
      //steps {
        //sh 'authtoken=$(. /var/lib/jenkins/iudx/rs/generateToken.sh); echo "$authtoken"'  
      //}
    //}
    stage('Run Jmeter Performance Tests'){
      steps{
        script{
          sh 'rm -rf Jmeter/report ; mkdir -p Jmeter/report'
          sh 'docker-compose -f docker-compose-production.yml up -d rs'
          sh 'sleep 45'
          sh '''
          set +x
          authtoken=$(curl --silent --location --request POST \'https://authdev.iudx.io/auth/v1/token\' --cert /var/lib/jenkins/iudx/rs/cert.pem --key /var/lib/jenkins/iudx/rs/privkey.pem --header \'Content-Type: application/json\' --data-raw \'{
    "request": [
       "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/surat-itms-realtime-information"
    ]
}\' | grep -o \'authdev[^"]*\' | awk \'{print $1}\')
/var/lib/jenkins/apache-jmeter-5.4.1/bin/jmeter.sh -n -t Jmeter/ResourceServer.jmx -l Jmeter/report/JmeterTest.jtl -e -o Jmeter/report/ -Jtoken=$authtoken
set -x
'''
          sh 'docker-compose down'
        }
      }
    }
    stage('Capture Jmeter report'){
      steps{
        perfReport filterRegex: '', sourceDataFiles: 'Jmeter/report/*.jtl'
      }
    }
    stage('Push Image') {
      steps{
        script {
          docker.withRegistry( registryUri, registryCredential ) {
            devImage.push()
            deplImage.push()
          }
        }
      }
    }
  }
}
