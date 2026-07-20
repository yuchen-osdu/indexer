# Copyright © Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

CUR_DIR=$(pwd)
SCRIPT_SOURCE_DIR=$(dirname "$0")
echo "Script source location"
echo "$SCRIPT_SOURCE_DIR"
cd $SCRIPT_SOURCE_DIR/indexer-acceptance-test

# Required variables
export HOST=$SCHEMA_URL
export INDEXER_HOST=$INDEXER_HOST
export SEARCH_HOST=$SEARCH_URL
export STORAGE_HOST=$STORAGE_URL
export SECURITY_HTTPS_CERTIFICATE_TRUST='true'
export DEFAULT_DATA_PARTITION_ID_TENANT1=opendes
export DEFAULT_DATA_PARTITION_ID_TENANT2=common
export ENTITLEMENTS_DOMAIN=example.com
export LEGAL_TAG=osdu
export OTHER_RELEVANT_DATA_COUNTRIES=US
export ELASTIC_HOST=localhost
export ELASTIC_USER_NAME=$ELASTIC_USERNAME
export ELASTIC_PASSWORD=$ELASTIC_PASSWORD

export AWS_COGNITO_AUTH_FLOW="USER_PASSWORD_AUTH"
export PRIVILEGED_USER_TOKEN=$(aws cognito-idp initiate-auth --region ${AWS_REGION} --auth-flow ${AWS_COGNITO_AUTH_FLOW} --client-id ${AWS_COGNITO_CLIENT_ID} --auth-parameters "{\"USERNAME\":\"${ADMIN_USER}\",\"PASSWORD\":\"${ADMIN_PASSWORD}\"}" --query AuthenticationResult.AccessToken --output text)
export ROOT_USER_TOKEN=$PRIVILEGED_USER_TOKEN

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

mvn clean verify
TEST_EXIT_CODE=$?

cd $CUR_DIR

if [ -n "$1" ]
  then
    mkdir -p "$1"
    mkdir -p $1/os-indexer
    cp -R $SCRIPT_SOURCE_DIR/indexer-acceptance-test/target/surefire-reports/* $1/os-indexer
fi

exit $TEST_EXIT_CODE
