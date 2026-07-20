# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This script executes the test and copies reports to the provided output directory
# To call this script from the service working directory
# ./dist/testing/integration/build-aws/run-tests.sh "./reports/"


SCRIPT_SOURCE_DIR=$(dirname "$0")
echo "Script source location"
echo "$SCRIPT_SOURCE_DIR"
(cd "$SCRIPT_SOURCE_DIR"/../bin && ./install-deps.sh)

#### ADD REQUIRED ENVIRONMENT VARIABLES HERE ###############################################
# The following variables are automatically populated from the environment during integration testing
# see os-deploy-aws/build-aws/integration-test-env-variables.py for an updated list

# AWS_COGNITO_CLIENT_ID
# ELASTIC_HOST
# ELASTIC_PORT
# FILE_URL
# LEGAL_URL
# SEARCH_URL
# STORAGE_URL
export AWS_COGNITO_AUTH_FLOW=USER_PASSWORD_AUTH
export AWS_COGNITO_AUTH_PARAMS_PASSWORD=$ADMIN_PASSWORD
export AWS_COGNITO_AUTH_PARAMS_USER=$ADMIN_USER
export DEFAULT_DATA_PARTITION_ID_TENANT1=opendes
export DEFAULT_DATA_PARTITION_ID_TENANT2=common
export ENTITLEMENTS_DOMAIN=example.com
export OTHER_RELEVANT_DATA_COUNTRIES=US
export STORAGE_HOST=$STORAGE_URL
export HOST=$SCHEMA_URL
export ELASTIC_HOST=localhost
export ELASTIC_PASSWORD=$ELASTIC_PASSWORD
export ELASTIC_USER_NAME=$ELASTIC_USERNAME

################ Elastic search port forwarding ########
##Check if port is available
localPort=$ELASTIC_PORT
while netstat -an | grep $localPort | grep -i listen ; do
    echo "$localPort Port in use"
    ((localPort++))
done
echo "Using local port: "$localPort

export KUBECONFIG=/tmp/kubeconfig-int-test$(date +%s).yaml
aws eks update-kubeconfig --name $EKS_CLUSTER_NAME --region $AWS_REGION --role-arn $CLUSTER_MANAGEMENT_ROLE_ARN
kubectl port-forward -n $TENANT_GROUP_NAME-tenant-$EKS_TENANT_NAME-elasticsearch svc/elasticsearch-es-http $localPort:$ELASTIC_PORT > /dev/null 2>&1 &

export ELASTIC_PORT=$localPort
pid=$!

trap '{
    echo killing "Port forward process: "$pid
    kill $pid
    rm $KUBECONFIG
}' EXIT

#### RUN INTEGRATION TEST #########################################################################

CUCUMBER_PROPERTY="$SCRIPT_SOURCE_DIR/../src/test/resources/cucumber.properties"

while IFS='=' read -r key value
do
    key=$(echo $key | tr '.' '_')
    eval ${key}=\${value}
done < "$CUCUMBER_PROPERTY"

echo "Cucumber option cucumber.options =         " ${cucumber_options}
JAVA_HOME=$JAVA17_HOME
mvn -ntp test -f "$SCRIPT_SOURCE_DIR"/../pom.xml -Dcucumber.options="--plugin junit:target/junit-report.xml $cucumber_options"

# mvn -Dmaven.surefire.debug test -f "$SCRIPT_SOURCE_DIR"/../pom.xml -Dcucumber.options="--plugin junit:target/junit-report.xml"
TEST_EXIT_CODE=$?

#### COPY TEST REPORTS #########################################################################

if [ -n "$1" ]
  then
    mkdir -p "$1"
    cp "$SCRIPT_SOURCE_DIR"/../target/junit-report.xml "$1"/os-indexer-junit-report.xml
    cp  -R "$SCRIPT_SOURCE_DIR"/../target/surefire-reports "$1"
fi

exit $TEST_EXIT_CODE
